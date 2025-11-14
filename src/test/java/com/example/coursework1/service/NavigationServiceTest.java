package com.example.coursework1.service;

import com.example.coursework1.dto.NextPositionRequest;
import com.example.coursework1.model.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationServiceTest {

    private final NavigationService service = new NavigationService();

    @Test
    void testNextPositionAtZeroDegrees() {
        Position start = new Position(0.0, 0.0);
        NextPositionRequest req = new NextPositionRequest(start, 0.0);

        Position next = service.calculateNextPosition(req);

        assertTrue(next.getLng() > 0);
        assertEquals(0.0, next.getLat(), 1e-9);
    }

    @Test
    void testNextPositionAt90Degrees() {
        Position start = new Position(0.0, 0.0);
        NextPositionRequest req = new NextPositionRequest(start, 90.0);

        Position next = service.calculateNextPosition(req);

        assertEquals(0.0, next.getLng(), 1e-9);
        assertTrue(next.getLat() > 0);
    }
}
