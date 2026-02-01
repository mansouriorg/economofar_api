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

            // 2. Préparer la question pour l'IA (Prompt Amélioré)
            // On utilise un Text Block (""") pour écrire le JSON modèle plus clairement
            String prompt = """
                    You are a professional receipt scanner. Analyze this image.
                    Extract the following details:
                    1. Merchant Name
                    2. Total Amount
                    3. Date (YYYY-MM-DD format if possible)
                    4. The list of purchased items (Name, Quantity, Unit Price).
                    
                    If the quantity is not explicitly written, assume it is 1.
                    
                    Return ONLY a valid JSON object with this EXACT structure (no markdown, no comments):
                    {
                        "merchant": "Supermarket Name",
                        "total": 12.50,
                        "date": "2023-10-25",
                        "items": [
                            { "name": "Milk Bottle", "qty": 2, "price": 1.50 },
                            { "name": "Bread", "qty": 1, "price": 2.00 }
                        ]
                    }
                    """;

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

            // 5. Retourner le texte de l'IA (avec Nettoyage)
            if (response != null && response.getResponse() != null) {
                // LLaVA ajoute souvent des balises ```json ... ```, on les retire pour ne garder que le JSON pur
                return response.getResponse()
                        .replace("```json", "")
                        .replace("```", "")
                        .trim();
            }
            
            return "{}"; // Retourne un JSON vide en cas d'erreur

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la lecture de l'image", e);
        }
    }
}