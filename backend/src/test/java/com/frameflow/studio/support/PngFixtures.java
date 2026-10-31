package com.frameflow.studio.support;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Small compressed fixtures without allocating an image-sized raster in the test JVM. */
public final class PngFixtures {
    private PngFixtures() {}

    public static byte[] solid(int width, int height, int bitDepth, boolean alpha) throws IOException {
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(compressed)) {
            byte[] zeros = new byte[8192];
            long rowBytes = (long) width * (alpha ? 4 : 3) * (bitDepth / 8);
            for (int row = 0; row < height; row++) {
                deflater.write(0); // PNG filter: none.
                for (long remaining = rowBytes; remaining > 0;) {
                    int count = (int) Math.min(remaining, zeros.length);
                    deflater.write(zeros, 0, count);
                    remaining -= count;
                }
            }
        }
        return png(width, height, bitDepth, alpha, compressed.toByteArray());
    }

    public static byte[] header(int width, int height) throws IOException {
        // Intentionally has no raster: over-limit dimensions must be rejected before decoding it.
        return png(width, height, 8, false, new byte[0]);
    }

    private static byte[] png(int width, int height, int bitDepth, boolean alpha, byte[] data) throws IOException {
        var result = new ByteArrayOutputStream();
        var output = new DataOutputStream(result);
        output.writeLong(0x89504e470d0a1a0aL);
        var header = new ByteArrayOutputStream();
        var fields = new DataOutputStream(header);
        fields.writeInt(width);
        fields.writeInt(height);
        fields.writeByte(bitDepth);
        fields.writeByte(alpha ? 6 : 2);
        fields.write(new byte[3]);
        chunk(output, "IHDR", header.toByteArray());
        chunk(output, "IDAT", data);
        chunk(output, "IEND", new byte[0]);
        return result.toByteArray();
    }

    private static void chunk(DataOutputStream output, String type, byte[] bytes) throws IOException {
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        output.writeInt(bytes.length);
        output.write(name);
        output.write(bytes);
        var crc = new CRC32();
        crc.update(name);
        crc.update(bytes);
        output.writeInt((int) crc.getValue());
    }
}
