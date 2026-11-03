package com.economofar.api.services;

import com.economofar.api.dtos.OllamaRequest;
import com.economofar.api.dtos.OllamaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Receipt analysis with a local Ollama vision model.
 *
 * Optional settings (application.properties or .env):
 *   OLLAMA_URL              default http://localhost:11434/api/generate
 *   OLLAMA_MODEL            default minicpm-v
 *   OLLAMA_TIMEOUT_SECONDS  default 180
 */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final String ollamaApiUrl;
    private final String modelName;
    private final RestTemplate restTemplate;

    public OllamaService(
            @Value("${OLLAMA_URL:http://localhost:11434/api/generate}") String ollamaApiUrl,
            @Value("${OLLAMA_MODEL:minicpm-v}") String modelName,
            @Value("${OLLAMA_TIMEOUT_SECONDS:180}") long timeoutSeconds
    ) {
        this.ollamaApiUrl = ollamaApiUrl;
        this.modelName = modelName;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(10L, timeoutSeconds)));

        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * Analyses the receipt image and returns the JSON block found in the model answer.
     *
     * @throws OcrException if Ollama is unreachable or does not return usable data
     */
    public String analyzeReceipt(MultipartFile file) {
        log.info("Receipt analysis started: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        String base64Image;

        try {
            base64Image = compressAndEncodeImage(file);
        } catch (IOException exception) {
            throw new OcrException("Impossible de lire l'image envoyée.", exception);
        }

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

        OllamaRequest request = OllamaRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .stream(false)
                .images(List.of(base64Image))
                .options(Map.of(
                        "temperature", 0.0,
                        "num_predict", 2000
                ))
                .build();

        long startTime = System.currentTimeMillis();
        OllamaResponse response;

        try {
            response = restTemplate.postForObject(ollamaApiUrl, request, OllamaResponse.class);
        } catch (Exception exception) {
            log.error("Ollama call failed: {}", exception.getMessage());
            throw new OcrException(
                    "Le service d'analyse (Ollama) est indisponible. Vérifiez qu'il est lancé puis réessayez.",
                    exception
            );
        }

        log.info("Ollama answered in {} s", (System.currentTimeMillis() - startTime) / 1000);

        if (response == null || response.getResponse() == null || response.getResponse().isBlank()) {
            throw new OcrException("L'analyse n'a renvoyé aucune donnée. Réessayez avec une photo plus nette.");
        }

        String rawResponse = response.getResponse();

        int firstBrace = rawResponse.indexOf("{");
        int lastBrace = rawResponse.lastIndexOf("}");

        if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
            log.warn("No JSON block found in the model answer");
            throw new OcrException("Le ticket n'a pas pu être lu. Réessayez avec une photo plus nette et bien éclairée.");
        }

        return rawResponse.substring(firstBrace, lastBrace + 1);
    }

    private String compressAndEncodeImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());

        if (originalImage == null) {
            log.warn("Image could not be decoded, sending the raw file");
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
        Graphics2D graphics = resizedImage.createGraphics();

        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", outputStream);

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
}
