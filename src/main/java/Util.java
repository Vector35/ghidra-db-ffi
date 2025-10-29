import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import db.DBBuffer;

public class Util {
    public static class AllocatedString {
        CCharPointer buf;
        long bufLen;
        AllocatedString(CCharPointer buf, long bufLen) {
            this.buf = buf;
            this.bufLen = bufLen;
        }
    }

    public static AllocatedString allocString(String s) {
        UnsignedWord byteLen = CTypeConversion.toCString(s, StandardCharsets.UTF_8, WordFactory.nullPointer(), WordFactory.zero());
        UnsignedWord bufLen = byteLen.add(1);
        CCharPointer buf = UnmanagedMemory.calloc(bufLen);
        CTypeConversion.toCString(s, StandardCharsets.UTF_8, buf, bufLen);
        return new AllocatedString(buf, bufLen.rawValue());
    }

    // TODO: this is a lot of copying
    public static AllocatedString allocBuffer(DBBuffer dbBuf) throws IOException {
        int byteLen = dbBuf.length();
        CCharPointer buf = UnmanagedMemory.malloc(byteLen);

        ByteBuffer holder = CTypeConversion.asByteBuffer(buf, byteLen);

        // Store data in temporary buffer
        byte[] data = new byte[byteLen];
        dbBuf.get(0, data);

        // Copy data from temporary buffer to allocated memory
        holder.put(data);

        return new AllocatedString(buf, byteLen);
    }
}
