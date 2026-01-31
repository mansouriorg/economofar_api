package com.economofar.api.controllers;

import com.economofar.api.services.FileStorageService;
import com.economofar.api.services.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final FileStorageService fileStorageService;
    private final OllamaService ollamaService; // ✅ On ajoute le service IA

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        // 1. Sauvegarder le fichier sur le disque (pour avoir une URL accessible)
        String fileName = fileStorageService.storeFile(file);

        // 2. Créer l'URL publique (ex: http://localhost:8080/uploads/ticket.jpg)
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(fileName)
                .toUriString();

        // 3. 🧠 APPEL IA : Analyser l'image avec Ollama
        // Cela va envoyer l'image à ton modèle LLaVA local
        String aiAnalysis = ollamaService.analyzeReceipt(file);

        // 4. Construire la réponse JSON
        Map<String, Object> response = new HashMap<>();
        response.put("url", fileDownloadUri);      // L'URL de l'image
        response.put("analysis", aiAnalysis);      // Le JSON extrait par l'IA (Commerçant + Prix)

        return ResponseEntity.ok(response);
    }
}