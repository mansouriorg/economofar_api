package com.economofar.api.services;

/**
 * Thrown when the receipt analysis (Ollama) is unavailable or returns nothing usable.
 * The message is written to be shown to the end user.
 */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
