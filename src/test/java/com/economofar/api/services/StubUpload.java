package com.economofar.api.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;

final class StubUpload {

    private StubUpload() {
    }

    static MultipartFile of(String originalName, String contentType, byte[] data) {
        return create(originalName, contentType, data, data.length, false);
    }

    static MultipartFile claiming(String originalName, String contentType, byte[] head, long size) {
        return create(originalName, contentType, head, size, false);
    }

    static MultipartFile unreadable(String originalName, String contentType, long size) {
        return create(originalName, contentType, new byte[0], size, true);
    }

    private static MultipartFile create(String originalName, String contentType, byte[] data, long size, boolean broken) {
        return (MultipartFile) Proxy.newProxyInstance(
                MultipartFile.class.getClassLoader(),
                new Class<?>[]{MultipartFile.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return "file";
                        case "getOriginalFilename":
                            return originalName;
                        case "getContentType":
                            return contentType;
                        case "isEmpty":
                            return size == 0;
                        case "getSize":
                            return size;
                        case "getBytes":
                            return data;
                        case "getInputStream":
                            if (broken) {
                                throw new IOException("the file cannot be read");
                            }

                            return new ByteArrayInputStream(data);
                        case "toString":
                            return "StubUpload(" + originalName + ")";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                }
        );
    }
}
