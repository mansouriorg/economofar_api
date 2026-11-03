package com.economofar.api.services;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

public final class UploadGuard {

    private UploadGuard() {
    }

    public static String requireImage(MultipartFile file) {
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        byte[] head;

        try (InputStream input = file.getInputStream()) {
            head = input.readNBytes(UploadPolicy.HEAD_BYTES);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file cannot be read");
        }

        UploadPolicy.Verdict verdict = UploadPolicy.inspect(file.getSize(), head);

        if (verdict.accepted()) {
            return verdict.extension();
        }

        throw new ResponseStatusException(HttpStatusCode.valueOf(verdict.status()), verdict.reason());
    }
}
