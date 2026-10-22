package top.aenp.msec.trust;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import top.aenp.msec.MythicWorldSecurity;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

public class TrustUtils {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss O");
    public static final Codec<ZonedDateTime> DATE_TIME_CODEC = Codec.STRING.comapFlatMap(
            string -> {
                try {
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(string, DATE_TIME_FORMATTER);
                    return DataResult.success(zonedDateTime);
                } catch (DateTimeParseException e) {
                    return DataResult.error(e::getMessage);
                }
            },
            zonedDateTime -> zonedDateTime.format(DATE_TIME_FORMATTER)
    );
    public static final Function<String, DataResult<String>> HOST_STRING_NORMALIZER;
    public static final Codec<String> VALIDATED_ADDR_STRING_CODEC;
    public static final String HTTPS_META_PATH = "/.well-known/msec/msec_server_metadata.json";
    public static final String DNS_HTTPS_DOMAIN_PREFIX = "_msec.";
    public static final HttpClient HTTP_CLIENT;
    public static final Codec<Set<String>> DNS_HTTPS_ADDRS_CODEC;
    public static final Codec<Set<String>> DIRECT_HTTPS_ADDRS_CODEC;

    static {
        HOST_STRING_NORMALIZER = string -> {
            String hostname;
            int port = 25565;
            String[] segments = string.split(":");
            switch (segments.length) {
                case 1:
                    hostname = segments[0].toLowerCase(Locale.ROOT);
                    break;
                case 2:
                    hostname = segments[0].toLowerCase(Locale.ROOT);
                    try {
                        port = Integer.parseInt(segments[1]);
                    } catch (NumberFormatException e) {
                        return DataResult.error(() -> String.format("Port number cannot be parsed: %s", segments[1]));
                    }
                    if (port < 0 || port > 65535) {
                        int illegalPort = port;
                        return DataResult.error(() -> String.format("Port number is out of range: %d", illegalPort));
                    }
                    break;
                default:
                    return DataResult.error(() -> String.format("Illegal host string: %s", string));
            }
            return DataResult.success(hostname + ":" + port);
        };
        VALIDATED_ADDR_STRING_CODEC = Codec.STRING.flatXmap(TrustUtils.HOST_STRING_NORMALIZER, TrustUtils.HOST_STRING_NORMALIZER);
        DNS_HTTPS_ADDRS_CODEC = Codec.STRING.listOf().comapFlatMap(
                list -> decodeAddressStringAsSet(list, true),
                set -> set.stream().toList()
        );
        DIRECT_HTTPS_ADDRS_CODEC = Codec.STRING.listOf().comapFlatMap(
                list -> decodeAddressStringAsSet(list, false),
                set -> set.stream().toList()
        );
        try {
            HTTP_CLIENT = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20L))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .sslContext(SSLContext.getDefault())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isDnsHttpsApplicable(String hostname) {
        try {
            InetAddress inetAddress = InetAddress.getByName(hostname);
            boolean isDomain = !hostname.equals(inetAddress.getHostAddress());
            boolean isLocal = inetAddress.isSiteLocalAddress();
            boolean isLoopback = inetAddress.isLoopbackAddress();
            return isDomain && !isLocal && !isLoopback;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isDirectHttpsApplicable(String hostname) {
        try {
            InetAddress inetAddress = InetAddress.getByName(hostname);
            boolean isLocal = inetAddress.isSiteLocalAddress();
            boolean isLoopback = inetAddress.isLoopbackAddress();
            return !isLocal && !isLoopback;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static Set<URI> fetchDnsHttpsUrisFromDomain(String domainToQuery) throws IOException {
        try {
            DirContext context = new InitialDirContext();
            Attributes attributes = context.getAttributes("dns:///" + domainToQuery, new String[]{"TXT"});
            Attribute attribute = attributes.get("txt");
            HashSet<URI> uriSet = new HashSet<>();
            if (attribute != null) {
                NamingEnumeration<?> namingEnumeration = attribute.getAll();
                while (namingEnumeration.hasMore()) {
                    String dnsRecordString = namingEnumeration.next().toString().trim().toLowerCase(Locale.ROOT);
                    if (dnsRecordString.startsWith("\"") && dnsRecordString.endsWith("\"")) {
                        dnsRecordString = dnsRecordString.substring(1, dnsRecordString.length() - 1);
                    }
                    for (String metadataUriString : dnsRecordString.split(" ")) {
                        if (metadataUriString.startsWith("http://")) {
                            throw new IOException(String.format("Insecure metadata URI: %s", metadataUriString));
                        } else if (!metadataUriString.startsWith("https://")) {
                            metadataUriString = "https://" + metadataUriString;
                        }
                        try {
                            uriSet.add(URI.create(metadataUriString));
                        } catch (Exception e) {
                            throw new IOException("Failed to parse URI.", e);
                        }
                    }
                }
            }
            return uriSet;
        } catch (NamingException e) {
            throw new IOException(String.format("Failed to fetch URIs from %s", domainToQuery), e);
        }
    }

    public static Map<String, HttpsMetadataEntry> fetchMetadataFromUri(URI uri) throws IOException {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();
            HttpResponse<String> httpResponse = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int responseCode = httpResponse.statusCode();
            if (!(responseCode >= 200 && responseCode < 400)) {
                throw new IOException(String.format("Failed to request metadata from %s : %d", uri, responseCode));
            } else {
                try {
                    JsonObject json = JsonParser.parseString(httpResponse.body()).getAsJsonObject();
                    return HttpsMetadataEntry.METADATA_MAP_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                } catch (Exception e) {
                    throw new IOException("Failed to parse metadata from the server.", e);
                }
            }
        } catch (InterruptedException e) {
            throw new IOException(String.format("Failed to request metadata from %s.", uri), e);
        }
    }

    public static DataResult<Set<String>> decodeAddressStringAsSet(List<String> list, boolean dns) {
        HashSet<String> set = new HashSet<>();
        for (String rawAddress : list) {
            DataResult<String> stringResult = HOST_STRING_NORMALIZER.apply(rawAddress);
            if (stringResult.isSuccess()) {
                String normalizedAddress = stringResult.getOrThrow();
                if (dns) {
                    if (isDnsHttpsApplicable(normalizedAddress.split(":")[0])) {
                        set.add(normalizedAddress);
                    } else {
                        MythicWorldSecurity.LOGGER.warn("Address {} is not applicable for DNS HTTPS. Reason: not a domain/local address/loopback address.", normalizedAddress);
                    }
                } else {
                    if (isDirectHttpsApplicable(normalizedAddress.split(":")[0])) {
                        set.add(normalizedAddress);
                    } else {
                        MythicWorldSecurity.LOGGER.warn("Address {} is not applicable for Direct HTTPS. Reason: local address/loopback address.", normalizedAddress);
                    }
                }
            } else {
                return DataResult.error(() -> stringResult.error().orElseThrow().message());
            }
        }
        return DataResult.success(set);
    }
}
