package com.example.coursework1.service;

import com.example.coursework1.dto.DistanceRequest;
import com.example.coursework1.model.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistanceServiceTest {

    private final DistanceService service = new DistanceService();

    @Test
    void testCalculateDistance() {
        Position p1 = new Position(0.0, 0.0);
        Position p2 = new Position(3.0, 4.0);
        DistanceRequest req = new DistanceRequest(p1, p2);

        double distance = service.calculateDistance(req);
        assertEquals(5.0, distance, 1e-9);
    }

    @Test
    void testIsCloseTrue() {
        Position p1 = new Position(0.0, 0.0);
        Position p2 = new Position(0.0001, 0.0001);
        DistanceRequest req = new DistanceRequest(p1, p2);

        assertTrue(service.isClose(req));
    }

    @Test
    void testIsCloseFalse() {
        Position p1 = new Position(0.0, 0.0);
        Position p2 = new Position(1.0, 1.0);
        DistanceRequest req = new DistanceRequest(p1, p2);

        assertFalse(service.isClose(req));
    }
}
