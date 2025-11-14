package com.example.coursework1.service;

import com.example.coursework1.dto.DistanceRequest;
import org.springframework.stereotype.Service;

@Service
public class DistanceService {

    private static final double THRESHOLD = 0.00015; // Threshold for isCloseTo to compare against
    private static final double TOLERANCE = 1e-12;

    public double calculateDistance(DistanceRequest request) {
        double dx = request.getPosition1().getLng() - request.getPosition2().getLng();
        double dy = request.getPosition1().getLat() - request.getPosition2().getLat();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean isClose(DistanceRequest request) {
        return calculateDistance(request) <= THRESHOLD - TOLERANCE;
    }
}
