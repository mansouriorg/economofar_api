package com.economofar.api.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    // Le dossier où seront stockées les images (à la racine du projet)
    private final Path fileStorageLocation;

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        
        try {
            // Créer le dossier s'il n'existe pas
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Impossible de créer le dossier de stockage uploads.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        try {
            // Générer un nom de fichier unique pour éviter les conflits (ex: un-uuid-aleatoire.jpg)
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            
            // Chemin complet de destination
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            
            // Copier le fichier
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            // Retourner le nom du fichier pour l'URL
            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Impossible de stocker le fichier " + file.getOriginalFilename(), ex);
        }
    }
}