package com.example.coursework1.repository;

import com.example.coursework1.dto.ServicePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Repository
public class ServicePointRepository {

    private static final Logger logger = LoggerFactory.getLogger(ServicePointRepository.class);
    private final RestTemplate restTemplate;
    private final String ilpEndpoint;

    public ServicePointRepository(RestTemplate restTemplate, String ilpEndpoint) {
        this.restTemplate = restTemplate;
        this.ilpEndpoint = ilpEndpoint.endsWith("/") ? ilpEndpoint : ilpEndpoint + "/";
    }

    public List<ServicePoint> fetchAllServicePoints() {
        try {
            String url = ilpEndpoint + "service-points";
            logger.debug("Fetching service points from: {}", url);

            ServicePoint[] points = restTemplate.getForObject(url, ServicePoint[].class);

            if (points == null) {
                logger.warn("Received null service points array from ILP service");
                return List.of();
            }

            logger.info("Successfully fetched {} service points", points.length);
            return Arrays.asList(points);
        } catch (Exception e) {
            logger.error("Failed to fetch service points from ILP service", e);
            return List.of();
        }
    }
}