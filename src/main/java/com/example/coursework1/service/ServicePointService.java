package com.example.coursework1.service;

import com.example.coursework1.dto.ServicePoint;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class ServicePointService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String ilpEndpoint;

    public ServicePointService(String ilpEndpoint) {
        if (!ilpEndpoint.endsWith("/")) {
            ilpEndpoint = ilpEndpoint + "/";
        }
        this.ilpEndpoint = ilpEndpoint;
    }

    public List<ServicePoint> fetchAllServicePoints() {
        String url = ilpEndpoint + "service-points";

        ServicePoint[] points = restTemplate.getForObject(url, ServicePoint[].class);

        if (points == null) {
            return List.of();
        }

        return Arrays.asList(points);
    }
}
