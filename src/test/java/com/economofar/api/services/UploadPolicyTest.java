package com.economofar.api.services;

import com.economofar.api.services.UploadPolicy.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UploadPolicyTest {

    private static byte[] head(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1};
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    }

    private static byte[] riff(String format) {
        return head("RIFF\u0000\u0010\u0000\u0000" + format + "VP8 ");
    }

    private static byte[] ftyp(String brand) {
        return head("\u0000\u0000\u0000\u0018ftyp" + brand + "\u0000\u0000\u0000\u0000");
    }

    @Test
    @DisplayName("JPEG, PNG, WebP and HEIC images are recognised by their first bytes")
    void imagesAreRecognised() {
        assertEquals("jpg", UploadPolicy.detect(jpeg()));
        assertEquals("png", UploadPolicy.detect(png()));
        assertEquals("webp", UploadPolicy.detect(riff("WEBP")));
        assertEquals("gif", UploadPolicy.detect(head("GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000\u0000")));
        assertEquals("gif", UploadPolicy.detect(head("GIF87a\u0001\u0000\u0001\u0000\u0000\u0000\u0000\u0000")));
        assertEquals("heic", UploadPolicy.detect(ftyp("heic")));
        assertEquals("heic", UploadPolicy.detect(ftyp("heix")));
        assertEquals("heic", UploadPolicy.detect(ftyp("mif1")));
        assertEquals("heic", UploadPolicy.detect(ftyp("msf1")));
    }

    @Test
    @DisplayName("the shortest valid headers are recognised and one wrong byte is enough to refuse")
    void exactLengths() {
        assertEquals("jpg", UploadPolicy.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        assertEquals("png", UploadPolicy.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("webp", UploadPolicy.detect(head("RIFF\u0000\u0000\u0000\u0000WEBP")));
        assertEquals("gif", UploadPolicy.detect(head("GIF87a")));
        assertEquals("gif", UploadPolicy.detect(head("GIF89a")));
        assertNull(UploadPolicy.detect(head("GIF86a")));
        assertNull(UploadPolicy.detect(head("GIF88a")));
        assertNull(UploadPolicy.detect(head("GIF87b")));
        assertNull(UploadPolicy.detect(head("GIF89b")));
        assertNull(UploadPolicy.detect(head("GIG89a")));
        assertNull(UploadPolicy.detect(head("GIF89")));
        assertEquals("heic", UploadPolicy.detect(head("\u0000\u0000\u0000\u0018ftypheic")));

        assertNull(UploadPolicy.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFE}));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0xFF, (byte) 0xD9, (byte) 0xFF}));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0xFE, (byte) 0xD8, (byte) 0xFF}));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0x89, 'X', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0B}));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0x88, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertNull(UploadPolicy.detect(head("RIFX\u0000\u0000\u0000\u0000WEBP")));
        assertNull(UploadPolicy.detect(head("RIFF\u0000\u0000\u0000\u0000WEBQ")));
        assertNull(UploadPolicy.detect(head("\u0000\u0000\u0000\u0018ftypheid")));
        assertNull(UploadPolicy.detect(head("\u0000\u0000\u0000\u0018ftxpheic")));
    }

    @Test
    @DisplayName("bytes above 127 are never read as letters of a signature")
    void highBytesAreNotLetters() {
        assertNull(UploadPolicy.detect(head("\u0000\u0000\u0000\u0018\u00e6\u00f4\u00f9\u00f0\u00e8\u00e5\u00e9\u00e3")));
        assertNull(UploadPolicy.detect(head("\u00d2\u00c9\u00c6\u00c6\u0000\u0010\u0000\u0000WEBPVP8 ")));
        assertNull(UploadPolicy.detect(head("RIFF\u0000\u0010\u0000\u0000\u00d7\u00c5\u00c2\u00d0VP8 ")));
        assertNull(UploadPolicy.detect(head("\u0000\u0000\u0000\u0018ftyp\u00e8\u00e5\u00e9\u00e3")));
    }

    @Test
    @DisplayName("other files are not images, whatever their name or type says")
    void otherFilesAreRefused() {
        assertNull(UploadPolicy.detect(head("<html><script>alert(1)</script></html>")));
        assertNull(UploadPolicy.detect(head("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>")));
        assertNull(UploadPolicy.detect(head("GIF90a\u0001\u0000\u0001\u0000\u0000\u0000\u0000\u0000")));
        assertNull(UploadPolicy.detect(head("GIF8")));
        assertNull(UploadPolicy.detect(head("%PDF-1.7\n%\u00e2\u00e3\u00cf\u00d3")));
        assertNull(UploadPolicy.detect(head("PK\u0003\u0004\u0014\u0000\u0000\u0000\u0008\u0000\u0000\u0000")));
        assertNull(UploadPolicy.detect(riff("WAVE")));
        assertNull(UploadPolicy.detect(ftyp("mp42")));
        assertNull(UploadPolicy.detect(ftyp("M4A ")));
        assertNull(UploadPolicy.detect(head("#!/bin/sh\nrm -rf /\n")));
    }

    @Test
    @DisplayName("a header that is too short or missing is not an image")
    void shortHeaders() {
        assertNull(UploadPolicy.detect(null));
        assertNull(UploadPolicy.detect(new byte[0]));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0xFF}));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0xFF, (byte) 0xD8}));
        assertNull(UploadPolicy.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertNull(UploadPolicy.detect(head("RIFF\u0000\u0010\u0000\u0000WEB")));
        assertNull(UploadPolicy.detect(head("\u0000\u0000\u0000\u0018ftyphei")));
    }

    @Test
    @DisplayName("a good image is accepted with its extension")
    void goodImage() {
        Verdict verdict = UploadPolicy.inspect(250_000, jpeg());

        assertTrue(verdict.accepted());
        assertEquals("jpg", verdict.extension());
        assertEquals(200, verdict.status());
    }

    @Test
    @DisplayName("a file of one byte is not empty")
    void oneByte() {
        assertTrue(UploadPolicy.inspect(1, jpeg()).accepted());
    }

    @Test
    @DisplayName("an empty file answers 400")
    void emptyFile() {
        Verdict zero = UploadPolicy.inspect(0, jpeg());
        Verdict negative = UploadPolicy.inspect(-1, jpeg());

        assertFalse(zero.accepted());
        assertEquals(400, zero.status());
        assertEquals(400, negative.status());
        assertEquals(400, UploadPolicy.inspect(0, new byte[0]).status());
    }

    @Test
    @DisplayName("a file of 15 MB is accepted and a byte more answers 413")
    void sizeLimit() {
        assertTrue(UploadPolicy.inspect(UploadPolicy.MAX_BYTES, png()).accepted());

        Verdict tooLarge = UploadPolicy.inspect(UploadPolicy.MAX_BYTES + 1, png());

        assertFalse(tooLarge.accepted());
        assertEquals(413, tooLarge.status());
        assertEquals(15L * 1024L * 1024L, UploadPolicy.MAX_BYTES);
    }

    @Test
    @DisplayName("something that is not an image answers 415")
    void notAnImage() {
        Verdict verdict = UploadPolicy.inspect(1_000, head("<html></html>"));

        assertFalse(verdict.accepted());
        assertEquals(415, verdict.status());
        assertNull(verdict.extension());
        assertTrue(verdict.reason().contains("JPEG"));
    }
}
