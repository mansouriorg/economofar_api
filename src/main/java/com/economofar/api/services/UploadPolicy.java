package com.economofar.api.services;

import java.util.Set;

public final class UploadPolicy {

    public static final long MAX_BYTES = 15L * 1024L * 1024L;
    public static final int HEAD_BYTES = 16;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF_87A = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF_89A = {'G', 'I', 'F', '8', '9', 'a'};
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private UploadPolicy() {
    }

    public record Verdict(boolean accepted, String extension, int status, String reason) {

        static Verdict accept(String extension) {
            return new Verdict(true, extension, 200, "");
        }

        static Verdict reject(int status, String reason) {
            return new Verdict(false, null, status, reason);
        }
    }

    public static Verdict inspect(long size, byte[] head) {
        if (size <= 0) {
            return Verdict.reject(400, "The file is empty.");
        }

        if (size > MAX_BYTES) {
            return Verdict.reject(413, "The file is too large: 15 MB at most.");
        }

        String extension = detect(head);

        if (extension == null) {
            return Verdict.reject(415, "Only JPEG, PNG, GIF, WebP and HEIC images are accepted.");
        }

        return Verdict.accept(extension);
    }

    public static String detect(byte[] head) {
        if (head == null) {
            return null;
        }

        if (head.length >= 3 && head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF) {
            return "jpg";
        }

        if (startsWith(head, PNG)) {
            return "png";
        }

        if (startsWith(head, GIF_87A) || startsWith(head, GIF_89A)) {
            return "gif";
        }

        if (head.length >= 12 && ascii(head, 0, 4).equals("RIFF") && ascii(head, 8, 12).equals("WEBP")) {
            return "webp";
        }

        if (head.length >= 12 && ascii(head, 4, 8).equals("ftyp") && HEIC_BRANDS.contains(ascii(head, 8, 12))) {
            return "heic";
        }

        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private static String ascii(byte[] data, int from, int to) {
        StringBuilder builder = new StringBuilder(to - from);

        for (int i = from; i < to; i++) {
            int value = data[i] & 0xFF;
            builder.append(value < 0x80 ? (char) value : '?');
        }

        return builder.toString();
    }
}
