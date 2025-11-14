package com.example.coursework1.service;

import com.example.coursework1.dto.Region;
import com.example.coursework1.dto.RegionRequest;
import com.example.coursework1.model.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class RegionServiceTest {

    private final RegionService service = new RegionService();

    private List<Position> square() {
        return List.of(
                new Position(0.0, 0.0),
                new Position(0.0, 1.0),
                new Position(1.0, 1.0),
                new Position(1.0, 0.0),
                new Position(0.0, 0.0)
        );
    }

    // Test point in region
    @Test
    void testPointInsideRegion() {
        Region region = new Region("Square", square());
        RegionRequest request = new RegionRequest(new Position(0.5, 0.5), region);
        assertTrue(service.isInRegion(request));
    }

    //Test point outside region
    @Test
    void testPointOutsideRegion() {
        Region region = new Region("Square", square());
        RegionRequest request = new RegionRequest(new Position(2.0, 2.0), region);
        assertFalse(service.isInRegion(request));
    }

    //Test point on border
    @Test
    void testPointOnBoundary() {
        Region region = new Region("Square", square());
        RegionRequest request = new RegionRequest(new Position(1.0, 0.5), region);
        assertTrue(service.isInRegion(request));
    }

    //Test too few vertices
    @Test
    void testTooFewVertices() {
        Region region = new Region("Triangle", List.of(
                new Position(0.0, 0.0),
                new Position(1.0, 1.0),
                new Position(0.0, 0.0)
        ));
        RegionRequest request = new RegionRequest(new Position(0.5, 0.5), region);

        assertThrows(IllegalArgumentException.class, () -> service.isInRegion(request));
    }

    //Test non-closed polygon
    @Test
    void testNonClosedPolygon() {
        Region region = new Region("nonClosedPolygon", List.of(
                new Position(0.0, 0.0),
                new Position(1.0, 1.0),
                new Position(1.0, 2.0),
                new Position(2.0, 4.0),
                new Position(4.0, 5.0)
        ));
        RegionRequest request = new RegionRequest(new Position(0.5, 0.5), region);

        assertThrows(IllegalArgumentException.class, () -> service.isInRegion(request));
    }
}
