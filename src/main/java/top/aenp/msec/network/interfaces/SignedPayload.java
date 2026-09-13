package top.aenp.msec.network.interfaces;

import java.io.ByteArrayOutputStream;

public interface SignedPayload {
    void writeMessageAsBytes(ByteArrayOutputStream byteArrayOutputStream);
}
