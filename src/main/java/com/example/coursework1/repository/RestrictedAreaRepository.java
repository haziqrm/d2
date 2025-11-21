package com.example.coursework1.repository;

import com.example.coursework1.model.RestrictedArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Repository
public class RestrictedAreaRepository {

    private static final Logger logger = LoggerFactory.getLogger(RestrictedAreaRepository.class);
    private final RestTemplate restTemplate;
    private final String ilpEndpoint;

    private List<RestrictedArea> cachedRestrictedAreas = null;

    public RestrictedAreaRepository(RestTemplate restTemplate, String ilpEndpoint) {
        this.restTemplate = restTemplate;
        this.ilpEndpoint = ilpEndpoint.endsWith("/") ? ilpEndpoint : ilpEndpoint + "/";
    }

    public List<RestrictedArea> fetchRestrictedAreas() {
        if (cachedRestrictedAreas != null) {
            logger.debug("Returning cached restricted areas");
            return cachedRestrictedAreas;
        }

        try {
            String url = ilpEndpoint + "restricted-areas";
            logger.debug("Fetching restricted areas from: {}", url);

            ResponseEntity<List<RestrictedArea>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<RestrictedArea>>() {}
            );

            cachedRestrictedAreas = response.getBody();

            if (cachedRestrictedAreas == null) {
                logger.warn("Received null restricted areas from ILP service");
                cachedRestrictedAreas = new ArrayList<>();
            }

            logger.info("Successfully fetched {} restricted areas", cachedRestrictedAreas.size());
            return cachedRestrictedAreas;
        } catch (Exception e) {
            logger.error("Failed to fetch restricted areas from ILP service", e);
            return new ArrayList<>();
        }
    }

    public void clearCache() {
        logger.info("Clearing restricted areas cache");
        cachedRestrictedAreas = null;
    }
}