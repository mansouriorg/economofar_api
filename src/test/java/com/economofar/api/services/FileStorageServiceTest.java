package com.economofar.api.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class FileStorageServiceTest {

    private static final String NAME = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static void withFolder(Consumer<Path> body) {
        Path folder = null;

        try {
            folder = Files.createTempDirectory("economofar-storage-test");
            body.accept(folder);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            if (folder != null) {
                delete(folder);
            }
        }
    }

    private static void delete(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long count(Path folder) {
        try (Stream<Path> list = Files.list(folder)) {
            return list.count();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] data(int size) {
        byte[] data = new byte[size];

        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 251);
        }

        return data;
    }

    @Test
    @DisplayName("a file is stored under a random name with the same content")
    void storesUnderRandomName() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);
            byte[] content = data(2_000);

            String stored = service.storeFile(StubUpload.of("ticket.jpg", "image/jpeg", content));

            assertTrue(stored.matches(NAME + "\\.jpg"));
            assertEquals(2_000L, folder.resolve(stored).toFile().length());
            assertEquals(1L, count(folder));

            try {
                assertTrue(java.util.Arrays.equals(content, Files.readAllBytes(folder.resolve(stored))));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    @DisplayName("the name sent by the client is never used on disk")
    void clientNameIsNotUsed() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);

            String stored = service.storeFile(StubUpload.of("../../evil.html", "text/html", data(10)));

            assertTrue(stored.matches(NAME + "\\.jpg"));
            assertEquals(1L, count(folder));
            assertFalse(Files.exists(folder.resolve("evil.html")));
        });
    }

    @Test
    @DisplayName("two uploads of the same file get two names")
    void twoNames() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);

            assertFalse(service.storeFile(StubUpload.of("a.jpg", "image/jpeg", data(5)))
                    .equals(service.storeFile(StubUpload.of("a.jpg", "image/jpeg", data(5)))));
            assertEquals(2L, count(folder));
        });
    }

    @Test
    @DisplayName("an allowed extension of the client is kept, in lower case")
    void allowedExtensionIsKept() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);

            assertTrue(service.storeFile(StubUpload.of("a.PNG", null, data(5))).endsWith(".png"));
            assertTrue(service.storeFile(StubUpload.of("a.b.webp", null, data(5))).endsWith(".webp"));
            assertTrue(service.storeFile(StubUpload.of("a.heic", null, data(5))).endsWith(".heic"));
            assertTrue(service.storeFile(StubUpload.of("a.heif", null, data(5))).endsWith(".heif"));
            assertTrue(service.storeFile(StubUpload.of("a.gif", null, data(5))).endsWith(".gif"));
            assertTrue(service.storeFile(StubUpload.of("a.jpeg", null, data(5))).endsWith(".jpeg"));
            assertTrue(service.storeFile(StubUpload.of(".png", "image/jpeg", data(5))).endsWith(".png"));
        });
    }

    @Test
    @DisplayName("without a usable name, the type sent by the client gives the extension")
    void contentTypeGivesTheExtension() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);

            assertTrue(service.storeFile(StubUpload.of("ticket", "image/png", data(5))).endsWith(".png"));
            assertTrue(service.storeFile(StubUpload.of("ticket.exe", "IMAGE/PNG", data(5))).endsWith(".png"));
            assertTrue(service.storeFile(StubUpload.of(null, "image/heic", data(5))).endsWith(".heic"));
            assertTrue(service.storeFile(StubUpload.of(null, "image/heif", data(5))).endsWith(".heif"));
            assertTrue(service.storeFile(StubUpload.of(null, "image/webp", data(5))).endsWith(".webp"));
            assertTrue(service.storeFile(StubUpload.of("ticket.", "image/jpeg", data(5))).endsWith(".jpg"));
            assertTrue(service.storeFile(StubUpload.of("png", "image/webp", data(5))).endsWith(".webp"));
            assertTrue(service.storeFile(StubUpload.of("a.p", "image/webp", data(5))).endsWith(".webp"));
        });
    }

    @Test
    @DisplayName("anything else is stored as a jpg, never with an extension that a browser would run")
    void unknownTypeIsAJpg() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);

            assertTrue(service.storeFile(StubUpload.of("page.html", "text/html", data(5))).endsWith(".jpg"));
            assertTrue(service.storeFile(StubUpload.of("image.svg", "image/svg+xml", data(5))).endsWith(".jpg"));
            assertTrue(service.storeFile(StubUpload.of(null, null, data(5))).endsWith(".jpg"));
        });
    }

    @Test
    @DisplayName("the folder is created when it does not exist")
    void folderIsCreated() {
        withFolder(folder -> {
            Path inner = folder.resolve("a").resolve("uploads");
            FileStorageService service = new FileStorageService(inner);

            assertTrue(Files.isDirectory(inner));
            assertTrue(service.storeFile(StubUpload.of("x.jpg", "image/jpeg", data(10))).endsWith(".jpg"));
        });
    }

    @Test
    @DisplayName("an image is deleted from its address, from its path or from its name")
    void deletesByUrl() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);

            for (String address : new String[]{"/uploads/%s", "/%s", "%s", "http://192.168.1.20:8080/uploads/%s", "  /uploads/%s  ", "C:\\uploads\\%s"}) {
                Path file = folder.resolve("one.jpg");
                write(file, "image");

                service.deleteByUrl(String.format(address, "one.jpg"));

                assertFalse(Files.exists(file), address);
            }
        });
    }

    @Test
    @DisplayName("deleting nothing, a blank address, the placeholder or a missing file is not an error")
    void deleteNothing() {
        withFolder(folder -> {
            FileStorageService service = new FileStorageService(folder);
            write(folder.resolve("keep.jpg"), "image");
            write(folder.resolve("placeholder.jpg"), "image");

            service.deleteByUrl(null);
            service.deleteByUrl("");
            service.deleteByUrl("   ");
            service.deleteByUrl("placeholder.jpg");
            service.deleteByUrl("/uploads/");
            service.deleteByUrl("/uploads/missing.jpg");

            assertTrue(Files.exists(folder.resolve("keep.jpg")));
            assertTrue(Files.exists(folder.resolve("placeholder.jpg")));
            assertEquals(2L, count(folder));
        });
    }

    @Test
    @DisplayName("an address with .. cannot delete anything outside the uploads folder")
    void deleteStaysInside() {
        withFolder(folder -> {
            Path uploads = folder.resolve("uploads");
            FileStorageService service = new FileStorageService(uploads);
            Path outside = folder.resolve("secret.txt");
            write(outside, "private");
            write(uploads.resolve("inside.jpg"), "image");

            service.deleteByUrl("../secret.txt");
            service.deleteByUrl("/uploads/../secret.txt");
            service.deleteByUrl("..");
            service.deleteByUrl("/..");
            service.deleteByUrl(outside.toString());

            assertTrue(Files.exists(outside));
            assertTrue(Files.isDirectory(uploads));
            assertTrue(Files.exists(uploads.resolve("inside.jpg")));
        });
    }
}
