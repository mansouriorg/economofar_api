package com.economofar.api.config;

import com.economofar.api.services.OcrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One single JSON error format for the whole API:
 *
 * { "status": 400, "message": "Human readable message", "timestamp": "..." }
 *
 * The iPhone app reads the "message" field and shows it to the user.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() != null && !exception.getReason().isBlank()
                ? exception.getReason()
                : "La requête a échoué";

        return build(exception.getStatusCode().value(), message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException exception) {
        return build(HttpStatus.UNAUTHORIZED.value(), "Email ou mot de passe incorrect");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException exception) {
        return build(HttpStatus.UNAUTHORIZED.value(), "Authentification impossible. Veuillez vous reconnecter.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Le fichier est trop volumineux (15 Mo maximum)");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT.value(), "Cette opération est impossible : la donnée existe déjà ou est liée à d'autres données");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return build(HttpStatus.BAD_REQUEST.value(), "Corps de la requête invalide");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : "Paramètre invalide";
        return build(HttpStatus.BAD_REQUEST.value(), message);
    }

    @ExceptionHandler(OcrException.class)
    public ResponseEntity<Map<String, Object>> handleOcr(OcrException exception) {
        log.warn("OCR failure: {}", exception.getMessage());
        return build(HttpStatus.BAD_GATEWAY.value(), exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(status).body(body);
    }
}
