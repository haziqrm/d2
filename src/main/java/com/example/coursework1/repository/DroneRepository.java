package com.example.coursework1.repository;

import com.example.coursework1.dto.Drone;
import com.example.coursework1.dto.DroneAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public List<DroneAvailability> fetchDroneAvailability() {
        try {
            String url = ilpEndpoint + "drone-availability";
            logger.debug("Fetching drone availability from: {}", url);

            DroneAvailability[] availability = restTemplate.getForObject(url, DroneAvailability[].class);

            if (availability == null) {
                logger.warn("Received null availability array from ILP service");
                return List.of();
            }

            logger.info("Successfully fetched availability for {} drones", availability.length);
            return Arrays.asList(availability);
        } catch (Exception e) {
            logger.error("Failed to fetch drone availability from ILP service", e);
            return List.of();
        }
    }
}