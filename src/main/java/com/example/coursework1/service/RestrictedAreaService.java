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
            return true;
        }

        int samples = 20;
        for (int i = 1; i < samples; i++) {
            double t = (double) i / samples;
            double lng = from.getLng() + t * (to.getLng() - from.getLng());
            double lat = from.getLat() + t * (to.getLat() - from.getLat());

            Position intermediatePoint = new Position(lng, lat);
            if (isInRestrictedArea(intermediatePoint)) {
                return true;
            }
        }

        return false;
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