package com.economofar.api.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "heic", "heif", "webp", "gif"
    );

    // The folder where images are stored (at the root of the project)
    private final Path fileStorageLocation;

    public FileStorageService() {
        this(Paths.get("uploads"));
    }

    FileStorageService(Path location) {
        this.fileStorageLocation = location.toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Impossible de créer le dossier de stockage uploads.", ex);
        }
    }

    /**
     * Stores the file with a random name (the original name is never used on disk).
     *
     * @return the stored file name, for example "3f2a...c1.jpg"
     */
    public String storeFile(MultipartFile file) {
        String fileName = UUID.randomUUID() + "." + resolveExtension(file);

        Path targetLocation = this.fileStorageLocation.resolve(fileName).normalize();

        if (!targetLocation.startsWith(this.fileStorageLocation)) {
            throw new RuntimeException("Chemin de fichier invalide.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Impossible de stocker le fichier.", ex);
        }
    }

    /**
     * Deletes a stored image from its URL or file name
     * (for example "/uploads/abc.jpg" or "abc.jpg").
     * Never throws: a missing file is not an error.
     */
    public void deleteByUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.equals("placeholder.jpg")) {
            return;
        }

        try {
            String value = imageUrl.trim();
            int lastSlash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
            String fileName = lastSlash >= 0 ? value.substring(lastSlash + 1) : value;

            if (fileName.isBlank()) {
                return;
            }

            Path target = this.fileStorageLocation.resolve(fileName).normalize();

            if (!target.startsWith(this.fileStorageLocation)) {
                return;
            }

            Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("Could not delete image {}: {}", imageUrl, ex.getMessage());
        }
    }

    private String resolveExtension(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');

            if (dot >= 0 && dot < originalName.length() - 1) {
                String extension = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);

                if (ALLOWED_EXTENSIONS.contains(extension)) {
                    return extension;
                }
            }
        }

        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "";

        if (contentType.contains("png")) {
            return "png";
        }

        if (contentType.contains("heic")) {
            return "heic";
        }

        if (contentType.contains("heif")) {
            return "heif";
        }

        if (contentType.contains("webp")) {
            return "webp";
        }

        return "jpg";
    }
}
