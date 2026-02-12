package com.economofar.api.services;

import com.economofar.api.dtos.OllamaRequest;
import com.economofar.api.dtos.OllamaResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class OllamaService {

    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";

    public String analyzeReceipt(MultipartFile file) {
        System.out.println("🎬 [OllamaService] Début de l'analyse du ticket...");
        
        try {
            // 1. OPTIMISATION IMAGE
            System.out.println("📸 [OllamaService] Traitement de l'image: " + file.getOriginalFilename() + " (" + file.getSize() + " octets)");
            String base64Image = compressAndEncodeImage(file);
            System.out.println("✅ [OllamaService] Image optimisée et encodée en Base64.");

            // 2. PROMPT
            String prompt = """
                    You are an advanced OCR assistant using MiniCPM-V.
                    
                    TASK: Analyze this receipt image step-by-step.
                    
                    STEP 1: RAW READING
                    First, read and list ALL the text visible on the receipt, line by line. Be very precise with prices.
                    
                    STEP 2: EXTRACTION
                    Based ONLY on the text you read in Step 1, create a JSON object.
                    - Translate item names to English if needed (from French/Arabic).
                    - Do not invent items. Only list what is visible.
                    
                    OUTPUT FORMAT:
                    After your analysis, output the JSON block.
                    Structure:
                    {
                        "merchant": "Store Name",
                        "date": "YYYY-MM-DD",
                        "total": 0.00,
                        "items": [
                            { "name": "Item Name", "qty": 1, "price": 0.00 }
                        ]
                    }
                    """;

            // 3. CONSTRUCTION DE LA REQUÊTE
            OllamaRequest request = OllamaRequest.builder()
                    .model("minicpm-v")
                    .prompt(prompt)
                    .stream(false)
                    .images(List.of(base64Image))
                    .options(Map.of(
                            "temperature", 0.0,
                            "num_predict", 2000
                    ))
                    .build();

            // 4. ENVOI
            System.out.println("📡 [OllamaService] Envoi de la requête à Ollama (cela peut prendre du temps si le modèle doit charger)...");
            long startTime = System.currentTimeMillis();
            
            RestTemplate restTemplate = new RestTemplate();
            OllamaResponse response = restTemplate.postForObject(OLLAMA_API_URL, request, OllamaResponse.class);
            
            long endTime = System.currentTimeMillis();
            System.out.println("⏱️ [OllamaService] Réponse reçue d'Ollama en " + (endTime - startTime) / 1000 + " secondes.");

            // 5. NETTOYAGE CHIRURGICAL DU JSON
            if (response != null && response.getResponse() != null) {
                String rawResponse = response.getResponse();
                System.out.println("📝 [OllamaService] Réponse brute de l'IA: " + rawResponse);
                
                int firstBrace = rawResponse.indexOf("{");
                int lastBrace = rawResponse.lastIndexOf("}");

                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    String cleanJson = rawResponse.substring(firstBrace, lastBrace + 1);
                    System.out.println("💎 [OllamaService] JSON extrait avec succès: " + cleanJson);
                    return cleanJson;
                } else {
                    System.err.println("❌ [OllamaService] Erreur: Aucun bloc JSON { } trouvé dans la réponse de l'IA.");
                    return "{\"error\": \"No JSON found in AI response\"}"; 
                }
            }
            
            System.err.println("❌ [OllamaService] Erreur: La réponse d'Ollama est null.");
            return "{\"error\": \"Null response from Ollama\"}";

        } catch (Exception e) {
            System.err.println("💥 [OllamaService] Erreur critique lors de l'analyse: " + e.getMessage());
            e.printStackTrace();
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String compressAndEncodeImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            System.err.println("⚠️ [OllamaService] Impossible de lire le flux d'image, envoi du fichier brut.");
            return Base64.getEncoder().encodeToString(file.getBytes());
        }

        int targetWidth = 1024;
        int originalWidth = originalImage.getWidth();
        
        if (originalWidth <= targetWidth) {
            System.out.println("ℹ️ [OllamaService] Image déjà petite, encodage direct.");
            return Base64.getEncoder().encodeToString(file.getBytes());
        }

        int newWidth = targetWidth;
        int newHeight = (int) (originalImage.getHeight() * ((double) newWidth / originalWidth));
        System.out.println("📏 [OllamaService] Redimensionnement de " + originalWidth + "px à " + newWidth + "px.");

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", os);
        return Base64.getEncoder().encodeToString(os.toByteArray());
    }
}