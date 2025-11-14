package com.example.coursework1.service;

import com.example.coursework1.dto.Region;
import com.example.coursework1.dto.RegionRequest;
import com.example.coursework1.model.Position;
import com.example.coursework1.model.RestrictedArea;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class RestrictedAreaService {

    private final String ilpEndpoint;
    private final RegionService regionService;
    private final RestTemplate restTemplate;

    // Cache restricted areas to avoid repeated API calls
    private List<RestrictedArea> cachedRestrictedAreas = null;

    @Autowired
    public RestrictedAreaService(String ilpEndpoint,
                                 RegionService regionService,
                                 RestTemplate restTemplate) {
        this.ilpEndpoint = ilpEndpoint;
        this.regionService = regionService;
        this.restTemplate = restTemplate;
    }

    public List<RestrictedArea> fetchRestrictedAreas() {
        if (cachedRestrictedAreas != null) {
            return cachedRestrictedAreas;
        }

        try {
            String url = ilpEndpoint + "/restricted-areas";
            ResponseEntity<List<RestrictedArea>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<RestrictedArea>>() {}
            );

            cachedRestrictedAreas = response.getBody();
            return cachedRestrictedAreas != null ? cachedRestrictedAreas : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Failed to fetch restricted areas: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean isInRestrictedArea(Position position) {
        List<RestrictedArea> areas = fetchRestrictedAreas();

        for (RestrictedArea area : areas) {
            if (area.getVertices() == null || area.getVertices().isEmpty()) {
                continue;
            }

            Region region = new Region(area.getName(), area.getVertices());
            RegionRequest request = new RegionRequest(position, region);

            if (regionService.isInRegion(request)) {
                return true;
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
        List<RestrictedArea> areas = fetchRestrictedAreas();

        // Sample points along the path
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

                if (regionService.isInRegion(request)) {
                    return area.getName();
                }
            }
        }

        return null;
    }

    public void clearCache() {
        cachedRestrictedAreas = null;
    }

    public List<String> getRestrictedAreaNames() {
        return fetchRestrictedAreas().stream()
                .map(RestrictedArea::getName)
                .toList();
    }
}