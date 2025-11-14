package com.example.coursework1.controllers;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s with id '%s' not found", resourceType, resourceId));
    }
}