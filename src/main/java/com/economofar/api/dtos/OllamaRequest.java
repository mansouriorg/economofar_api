package com.economofar.api.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map; // ✅ Import Maps

@Data
@Builder
public class OllamaRequest {
    private String model;   // "llava"
    private String prompt;  // "Analyse ce ticket..."
    private boolean stream; // false (pour avoir la réponse d'un coup)
    private List<String> images; // L'image convertie en texte (Base64)
    private Map<String, Object> options; // ✅ Ajoute ce champ
}