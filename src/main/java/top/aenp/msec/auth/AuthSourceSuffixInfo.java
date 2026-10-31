package top.aenp.msec.auth;

import java.util.Locale;
import java.util.Set;

public record AuthSourceSuffixInfo(String primarySuffix, Set<String> allPossibleSuffixes) {
    public static final Set<Character> SUFFIX_SEPARATORS = Set.of('.', '-', '+', '_');

    public boolean isSuffixed(String name) {
        for (String suffix : allPossibleSuffixes) {
            if (name.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
