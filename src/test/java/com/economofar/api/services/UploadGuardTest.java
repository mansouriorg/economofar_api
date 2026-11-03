package com.economofar.api.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class UploadGuardTest {

    private static byte[] jpeg(int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        return data;
    }

    private static int statusOf(MultipartFile file) {
        ResponseStatusException refused = assertThrows(ResponseStatusException.class, () -> UploadGuard.requireImage(file));
        return refused.getStatusCode().value();
    }

    @Test
    @DisplayName("an image is accepted and its extension is returned")
    void acceptsAnImage() {
        assertEquals("jpg", UploadGuard.requireImage(StubUpload.of("ticket.jpg", "image/jpeg", jpeg(2_000))));
    }

    @Test
    @DisplayName("the content decides, not the name or the type that the client sends")
    void contentDecides() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

        assertEquals("png", UploadGuard.requireImage(StubUpload.of("ticket.txt", "text/plain", png)));
        assertEquals("jpg", UploadGuard.requireImage(StubUpload.of(null, null, jpeg(50))));
    }

    @Test
    @DisplayName("a page named like an image answers 415")
    void pageNamedLikeAnImage() {
        byte[] page = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertEquals(415, statusOf(StubUpload.of("ticket.jpg", "image/jpeg", page)));
    }

    @Test
    @DisplayName("an empty file answers 400")
    void emptyFile() {
        assertEquals(400, statusOf(StubUpload.of("ticket.jpg", "image/jpeg", new byte[0])));
    }

    @Test
    @DisplayName("a file of 15 MB passes and a byte more answers 413")
    void sizeLimit() {
        byte[] head = jpeg(16);

        assertEquals("jpg", UploadGuard.requireImage(StubUpload.claiming("a.jpg", "image/jpeg", head, UploadPolicy.MAX_BYTES)));
        assertEquals(413, statusOf(StubUpload.claiming("a.jpg", "image/jpeg", head, UploadPolicy.MAX_BYTES + 1)));
    }

    @Test
    @DisplayName("no file at all answers 400")
    void noFile() {
        assertEquals(400, statusOf(null));
    }

    @Test
    @DisplayName("a file that cannot be read answers 400")
    void unreadable() {
        assertEquals(400, statusOf(StubUpload.unreadable("ticket.jpg", "image/jpeg", 500)));
    }
}
