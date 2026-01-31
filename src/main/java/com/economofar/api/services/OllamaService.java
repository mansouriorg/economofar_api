package com.economofar.api.services;

import com.economofar.api.dtos.OllamaRequest;
import com.economofar.api.dtos.OllamaResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
public class OllamaService {

    // L'adresse locale d'Ollama
    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";

    public String analyzeReceipt(MultipartFile file) {
        try {
            // 1. Convertir l'image en Base64 (Ollama ne peut pas lire un fichier direct)
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

            // 2. Préparer la question pour l'IA (Prompt)
            // On lui demande de répondre en JSON pour faciliter la lecture future
            String prompt = "You are a receipt scanner. Analyze this image. " +
                            "Identify the merchant name and the total amount. " +
                            "Reply ONLY with a JSON format like this: " +
                            "{ \"merchant\": \"Supermarket Name\", \"amount\": 12.50 }";

            // 3. Créer la requête
            OllamaRequest request = OllamaRequest.builder()
                    .model("llava")
                    .prompt(prompt)
                    .stream(false)
                    .images(List.of(base64Image))
                    .build();

            // 4. Envoyer la requête à Ollama via HTTP
            RestTemplate restTemplate = new RestTemplate();
            OllamaResponse response = restTemplate.postForObject(OLLAMA_API_URL, request, OllamaResponse.class);

            // 5. Retourner le texte de l'IA
            return response != null ? response.getResponse() : "Erreur : Pas de réponse de l'IA";

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la lecture de l'image", e);
        }
    }
}