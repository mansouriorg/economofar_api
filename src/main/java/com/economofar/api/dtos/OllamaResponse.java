package com.economofar.api.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Important pour ignorer les détails techniques
public class OllamaResponse {
    private String response; // La réponse de l'IA !
    private boolean done;
}