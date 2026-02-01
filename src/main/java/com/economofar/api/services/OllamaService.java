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
        try {
            // 1. OPTIMISATION IMAGE : Compression et Redimensionnement
            // MiniCPM-V est puissant, mais on optimise l'image pour la vitesse.
            String base64Image = compressAndEncodeImage(file);

            // 2. PROMPT "CHAIN OF THOUGHT" (Chaîne de Pensée) 🧠
            // On demande à l'IA de décomposer le problème en étapes pour éviter les erreurs.
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

            // 3. CONSTRUCTION DE LA REQUÊTE AVEC OPTIONS
            OllamaRequest request = OllamaRequest.builder()
                    .model("minicpm-v") // ✅ Modèle spécialisé OCR
                    .prompt(prompt)
                    .stream(false)
                    .images(List.of(base64Image))
                    // 🔥 OPTIONS CRITIQUES :
                    .options(Map.of(
                            "temperature", 0.0,  // 0 = Strictement factuel
                            "num_predict", 2000  // Autorise une réponse longue (car elle inclut l'étape 1)
                    ))
                    .build();

            // 4. ENVOI
            RestTemplate restTemplate = new RestTemplate();
            OllamaResponse response = restTemplate.postForObject(OLLAMA_API_URL, request, OllamaResponse.class);

            // 5. NETTOYAGE CHIRURGICAL DU JSON
            // L'IA va répondre "Step 1: I see bread... Step 2: Here is the JSON...".
            // Ce code ignore tout le blabla et ne garde que ce qui est entre { et }.
            if (response != null && response.getResponse() != null) {
                String rawResponse = response.getResponse();
                
                int firstBrace = rawResponse.indexOf("{");
                int lastBrace = rawResponse.lastIndexOf("}");

                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    return rawResponse.substring(firstBrace, lastBrace + 1);
                } else {
                    System.err.println("ERREUR IA (Pas de JSON trouvé) : " + rawResponse);
                    return "{}"; 
                }
            }
            
            return "{}";

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors du traitement de l'image", e);
        }
    }

    /**
     * Méthode utilitaire pour redimensionner l'image avant l'envoi.
     * Convertit en JPG et réduit la largeur à 1024px max.
     */
    private String compressAndEncodeImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            return Base64.getEncoder().encodeToString(file.getBytes());
        }

        int targetWidth = 1024;
        int originalWidth = originalImage.getWidth();
        
        if (originalWidth <= targetWidth) {
            return Base64.getEncoder().encodeToString(file.getBytes());
        }

        int newWidth = targetWidth;
        int newHeight = (int) (originalImage.getHeight() * ((double) newWidth / originalWidth));

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