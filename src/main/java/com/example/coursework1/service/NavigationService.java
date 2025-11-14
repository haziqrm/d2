package com.example.coursework1.service;

import com.example.coursework1.dto.NextPositionRequest;
import com.example.coursework1.model.Position;
import org.springframework.stereotype.Service;

@Service
public class NavigationService {

    private static final double STEP = 0.00015;
    private static final double ANGLE_INCREMENT = 22.5;

    private static final double TOLERANCE = 1e-9; // Tolerance for floating point numbers

    public Position calculateNextPosition(NextPositionRequest request) {
        double angleRad = Math.toRadians(request.getAngle());
        double deltaLng = STEP * Math.cos(angleRad);
        double deltaLat = STEP * Math.sin(angleRad);

        double angle = request.getAngle();

        // Check if angle is multiple of 22.5
        if (!isMultipleOf(angle, ANGLE_INCREMENT)) {
            throw new IllegalArgumentException("Angle must be a multiple of 22.5 degrees");
        }

        return new Position(
                request.getStart().getLng() + deltaLng,
                request.getStart().getLat() + deltaLat
        );
    }

    private boolean isMultipleOf(double value, double increment) {
        double ratio = value / increment;
        return Math.abs(ratio - Math.round(ratio)) < TOLERANCE;
    }
}
