package com.example.coursework1.repository;

import com.example.coursework1.dto.Drone;
import com.example.coursework1.dto.ServicePointDrones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Repository
public class DroneRepository {

    private static final Logger logger = LoggerFactory.getLogger(DroneRepository.class);
    private final RestTemplate restTemplate;
    private final String ilpEndpoint;

    public DroneRepository(RestTemplate restTemplate, String ilpEndpoint) {
        this.restTemplate = restTemplate;
        this.ilpEndpoint = ilpEndpoint.endsWith("/") ? ilpEndpoint : ilpEndpoint + "/";
    }

    public List<Drone> fetchAllDrones() {
        try {
            String url = ilpEndpoint + "drones";
            logger.debug("Fetching drones from: {}", url);

            Drone[] drones = restTemplate.getForObject(url, Drone[].class);

            if (drones == null) {
                logger.warn("Received null drones array from ILP service");
                return List.of();
            }

            logger.info("Successfully fetched {} drones", drones.length);
            return Arrays.asList(drones);
        } catch (Exception e) {
            logger.error("Failed to fetch drones from ILP service", e);
            return List.of();
        }
    }

    /**
     * Fetch drone availability organized by service point
     * Endpoint: /drones-for-service-points
     */
    public List<ServicePointDrones> fetchDronesForServicePoints() {
        try {
            String url = ilpEndpoint + "drones-for-service-points";
            logger.debug("Fetching drones-for-service-points from: {}", url);

            ResponseEntity<List<ServicePointDrones>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ServicePointDrones>>() {}
            );

            List<ServicePointDrones> data = response.getBody();

            if (data == null) {
                logger.warn("Received null drones-for-service-points from ILP service");
                return List.of();
            }

            int totalDrones = data.stream()
                    .mapToInt(sp -> sp.getDrones() != null ? sp.getDrones().size() : 0)
                    .sum();

            logger.info("Successfully fetched {} service points with {} total drones",
                    data.size(), totalDrones);
            return data;
        } catch (Exception e) {
            logger.error("Failed to fetch drones-for-service-points from ILP service", e);
            return List.of();
        }
    }
}