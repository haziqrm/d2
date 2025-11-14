package com.example.coursework1.service;

import com.example.coursework1.dto.Drone;
import com.example.coursework1.dto.DroneAvailability;
import com.example.coursework1.dto.QueryAttribute;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class DroneService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String ilpEndpoint;

    public DroneService(String ilpEndpoint) {
        if (!ilpEndpoint.endsWith("/")) {
            ilpEndpoint = ilpEndpoint + "/";
        }
        this.ilpEndpoint = ilpEndpoint;
    }

    public List<Drone> fetchAllDrones() {
        String url = ilpEndpoint + "drones";

        Drone[] drones = restTemplate.getForObject(url, Drone[].class);

        if (drones == null) {
            return List.of();
        }

        return Arrays.asList(drones);
    }

    public List<DroneAvailability> fetchDroneAvailability() {
        String url = ilpEndpoint + "drone-availability";

        try {
            DroneAvailability[] availability = restTemplate.getForObject(url, DroneAvailability[].class);

            if (availability == null) {
                return List.of();
            }

            return Arrays.asList(availability);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Drone getDroneById(int id) {
        return fetchAllDrones()
                .stream()
                .filter(d -> d.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public List<Integer> dronesWithCooling(boolean state) {
        return fetchAllDrones()
                .stream()
                .filter(d -> d.isCooling() == state)
                .map(Drone::getId)
                .toList();
    }

    public List<Integer> queryAsPath(String attribute, String value) {
        return fetchAllDrones().stream()
                .filter(d -> matches(d, attribute, "=", value))
                .map(Drone::getId)
                .toList();
    }

    public List<Integer> query(List<QueryAttribute> filters) {
        return fetchAllDrones().stream()
                .filter(drone ->
                        filters.stream().allMatch(f ->
                                matches(drone, f.getAttribute(), f.getOperator(), f.getValue())
                        )
                )
                .map(Drone::getId)
                .toList();
    }

    private boolean matches(Drone d, String attribute, String operator, String rawValue) {
        Object value = extractAttributeValue(d, attribute);

        if (value == null) return false;

        if (!(value instanceof Number)) {
            return operator.equals("=") &&
                    value.toString().equalsIgnoreCase(rawValue);
        }

        double droneVal = ((Number) value).doubleValue();
        double queryVal;

        try {
            queryVal = Double.parseDouble(rawValue);
        } catch (Exception e) {
            return false;
        }

        return switch (operator) {
            case "="  -> droneVal == queryVal;
            case "!=" -> droneVal != queryVal;
            case "<"  -> droneVal < queryVal;
            case ">"  -> droneVal > queryVal;
            default -> false;
        };
    }

    private Object extractAttributeValue(Drone d, String attribute) {

        switch (attribute.toLowerCase()) {
            case "id": return d.getId();
            case "name": return d.getName();
            case "capacity": return d.getCapability().getCapacity();
            case "cooling": return d.getCapability().isCooling();
            case "heating": return d.getCapability().isHeating();
            case "maxmoves": return d.getCapability().getMaxMoves();
            case "costpermove": return d.getCapability().getCostPerMove();
            case "costinitial": return d.getCapability().getCostInitial();
            case "costfinal": return d.getCapability().getCostFinal();

            default: return null;
        }
    }
}