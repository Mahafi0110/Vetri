package com.example.demo.exception;

import com.example.demo.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation errors (@Valid) ─────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
            .getAllErrors()
            .forEach(error -> {
                String field   = ((FieldError) error).getField();
                String message = error.getDefaultMessage();
                errors.put(field, message);
            });

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse(
                false,
                "Validation failed",
                errors
            ));
    }

    // ── File too large ────────────────────────
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse> handleMaxSizeException(
            MaxUploadSizeExceededException ex) {

        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiResponse.error(
                "File too large. Maximum allowed size is 50MB."
            ));
    }

    // ── File processing error ─────────────────
    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ApiResponse> handleFileProcessingException(
            FileProcessingException ex) {

        // Print real error to console
        System.err.println(
            "FileProcessingException: " + ex.getMessage()
        );
        ex.printStackTrace();

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ex.getMessage()));
    }

    // ── Runtime errors ────────────────────────
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse> handleRuntimeException(
            RuntimeException ex) {

        // Print real error to console
        System.err.println(
            "RuntimeException: " + ex.getMessage()
        );
        ex.printStackTrace();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage()));
    }

    // ── All other errors ──────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGeneralException(
            Exception ex) {

        // Print real error to console
        System.err.println(
            "Exception: " + ex.getMessage()
        );
        ex.printStackTrace();

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                "Something went wrong: " + ex.getMessage()
            ));
    }
}