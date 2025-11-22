package com.example.coursework1.service;

import com.example.coursework1.dto.Region;
import com.example.coursework1.dto.RegionRequest;
import com.example.coursework1.model.Position;
import com.example.coursework1.model.RestrictedArea;
import com.example.coursework1.repository.RestrictedAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RestrictedAreaService {

    private static final Logger logger = LoggerFactory.getLogger(RestrictedAreaService.class);
    private static final double TOLERANCE = 1e-10;

    private final RestrictedAreaRepository restrictedAreaRepository;
    private final RegionService regionService;

    public RestrictedAreaService(RestrictedAreaRepository restrictedAreaRepository,
                                 RegionService regionService) {
        this.restrictedAreaRepository = restrictedAreaRepository;
        this.regionService = regionService;
    }

    public boolean isInRestrictedArea(Position position) {
        if (position == null) {
            return false;
        }

        List<RestrictedArea> areas = restrictedAreaRepository.fetchRestrictedAreas();

        for (RestrictedArea area : areas) {
            if (area.getVertices() == null || area.getVertices().isEmpty()) {
                continue;
            }

            Region region = new Region(area.getName(), area.getVertices());
            RegionRequest request = new RegionRequest(position, region);

            try {
                if (regionService.isInRegion(request)) {
                    logger.debug("Position {} is in restricted area: {}", position, area.getName());
                    return true;
                }
            } catch (Exception e) {
                logger.warn("Error checking if position is in area {}: {}", area.getName(), e.getMessage());
            }
        }

        return false;
    }

    public boolean pathCrossesRestrictedArea(Position from, Position to) {
        if (from == null || to == null) {
            return false;
        }

        if (isInRestrictedArea(from) || isInRestrictedArea(to)) {
            logger.debug("Path endpoint in restricted area: from={}, to={}", from, to);
            return true;
        }

        List<RestrictedArea> areas = restrictedAreaRepository.fetchRestrictedAreas();

        for (RestrictedArea area : areas) {
            if (area.getVertices() == null || area.getVertices().isEmpty()) {
                continue;
            }

            if (lineSegmentIntersectsPolygon(from, to, area.getVertices())) {
                logger.debug("Path crosses restricted area {}: from {} to {}",
                        area.getName(), from, to);
                return true;
            }
        }

        return false;
    }

    private boolean lineSegmentIntersectsPolygon(Position p1, Position p2, List<Position> polygon) {
        if (polygon == null || polygon.size() < 2) {
            return false;
        }

        for (int i = 0; i < polygon.size() - 1; i++) {
            Position v1 = polygon.get(i);
            Position v2 = polygon.get(i + 1);

            if (lineSegmentsIntersect(p1, p2, v1, v2)) {
                return true;
            }
        }

        int samples = 10;
        for (int i = 1; i < samples; i++) {
            double t = (double) i / samples;
            double lng = p1.getLng() + t * (p2.getLng() - p1.getLng());
            double lat = p1.getLat() + t * (p2.getLat() - p1.getLat());

            Position testPoint = new Position(lng, lat);

            Region region = new Region("test", polygon);
            RegionRequest request = new RegionRequest(testPoint, region);

            try {
                if (regionService.isInRegion(request)) {
                    return true;
                }
            } catch (Exception e) {
            }
        }

        return false;
    }

    private boolean lineSegmentsIntersect(Position p1, Position p2, Position p3, Position p4) {
        double x1 = p1.getLng(), y1 = p1.getLat();
        double x2 = p2.getLng(), y2 = p2.getLat();
        double x3 = p3.getLng(), y3 = p3.getLat();
        double x4 = p4.getLng(), y4 = p4.getLat();

        double d1x = x2 - x1, d1y = y2 - y1;
        double d2x = x4 - x3, d2y = y4 - y3;

        double denominator = d1x * d2y - d1y * d2x;

        if (Math.abs(denominator) < TOLERANCE) {
            return false;
        }

        double t = ((x3 - x1) * d2y - (y3 - y1) * d2x) / denominator;
        double u = ((x3 - x1) * d1y - (y3 - y1) * d1x) / denominator;

        double eps = TOLERANCE;
        return (t >= -eps && t <= 1 + eps) && (u >= -eps && u <= 1 + eps);
    }

    public boolean flightPathCrossesRestrictedArea(List<Position> flightPath) {
        if (flightPath == null || flightPath.size() < 2) {
            return false;
        }

        for (int i = 0; i < flightPath.size() - 1; i++) {
            if (pathCrossesRestrictedArea(flightPath.get(i), flightPath.get(i + 1))) {
                return true;
            }
        }

        return false;
    }

    public String getRestrictedAreaNameForPath(Position from, Position to) {
        List<RestrictedArea> areas = restrictedAreaRepository.fetchRestrictedAreas();

        int samples = 20;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double lng = from.getLng() + t * (to.getLng() - from.getLng());
            double lat = from.getLat() + t * (to.getLat() - from.getLat());

            Position point = new Position(lng, lat);

            for (RestrictedArea area : areas) {
                if (area.getVertices() == null || area.getVertices().isEmpty()) {
                    continue;
                }

                Region region = new Region(area.getName(), area.getVertices());
                RegionRequest request = new RegionRequest(point, region);

                try {
                    if (regionService.isInRegion(request)) {
                        return area.getName();
                    }
                } catch (Exception e) {
                    logger.warn("Error checking area {}: {}", area.getName(), e.getMessage());
                }
            }
        }

        return null;
    }

    public List<String> getRestrictedAreaNames() {
        return restrictedAreaRepository.fetchRestrictedAreas().stream()
                .map(RestrictedArea::getName)
                .toList();
    }

    public void clearCache() {
        restrictedAreaRepository.clearCache();
    }
}