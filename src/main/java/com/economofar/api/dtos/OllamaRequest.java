package com.economofar.api.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class OllamaRequest {
    private String model;   // "llava"
    private String prompt;  // "Analyse ce ticket..."
    private boolean stream; // false (pour avoir la réponse d'un coup)
    private List<String> images; // L'image convertie en texte (Base64)
}