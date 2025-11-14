package com.example.coursework1.service;

import com.example.coursework1.dto.RegionRequest;
import com.example.coursework1.model.Position;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegionService {

    private static final double TOLERANCE = 1e-12;

    public boolean isInRegion(RegionRequest request) {
        List<Position> vertices = request.getRegion().getVertices();
        Position point = request.getPosition();

        if (vertices == null || vertices.size() < 4) {
            throw new IllegalArgumentException("Region must have at least 4 vertices.");
        }

        Position first = vertices.get(0);
        Position last = vertices.get(vertices.size() - 1);

        if (Math.abs(first.getLng() - last.getLng()) > TOLERANCE ||
                Math.abs(first.getLat() - last.getLat()) > TOLERANCE) {
            throw new IllegalArgumentException("Polygon must be closed (first and last vertices must match).");
        }

        if (isOnBoundary(point, vertices)) {
            return true;
        }

        boolean inside = false;
        double px = point.getLng();
        double py = point.getLat();

        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            double xi = vertices.get(i).getLng(), yi = vertices.get(i).getLat();
            double xj = vertices.get(j).getLng(), yj = vertices.get(j).getLat();

            if (Math.abs(yj - yi) < TOLERANCE) continue;

            boolean intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi);

            if (intersect)
                inside = !inside;
        }

        return inside;
    }

    private boolean isOnBoundary(Position point, List<Position> vertices) {
        double px = point.getLng();
        double py = point.getLat();

        for (int i = 0; i < vertices.size() - 1; i++) {
            double x1 = vertices.get(i).getLng();
            double y1 = vertices.get(i).getLat();
            double x2 = vertices.get(i + 1).getLng();
            double y2 = vertices.get(i + 1).getLat();

            double dx = x2 - x1;
            double dy = y2 - y1;

            double cross = (px - x1) * dy - (py - y1) * dx;
            if (Math.abs(cross) > TOLERANCE) continue;

            double dot = (px - x1) * (px - x2) + (py - y1) * (py - y2);
            if (dot <= TOLERANCE)
                return true;
        }

        return false;
    }
}
