package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse {

    private boolean success;
    private String message;
    private Object data;

    // Quick success response
    public static ApiResponse success(String message, Object data) {
        return new ApiResponse(true, message, data);
    }

    // Quick error response
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null);
    }
}