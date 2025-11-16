package com.example.coursework1.service;

import com.example.coursework1.dto.Drone;
import com.example.coursework1.dto.QueryAttribute;
import com.example.coursework1.dto.ServicePointDrones;
import com.example.coursework1.repository.DroneRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DroneService {

    private final DroneRepository droneRepository;

    public DroneService(DroneRepository droneRepository) {
        this.droneRepository = droneRepository;
    }

    public List<Drone> fetchAllDrones() {
        return droneRepository.fetchAllDrones();
    }

    /**
     * Fetch drone availability organized by service point
     */
    public List<ServicePointDrones> fetchDronesForServicePoints() {
        return droneRepository.fetchDronesForServicePoints();
    }

    public Drone getDroneById(String id) {
        return fetchAllDrones()
                .stream()
                .filter(d -> d.getId() != null && d.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public List<String> dronesWithCooling(boolean state) {
        return fetchAllDrones()
                .stream()
                .filter(d -> d.isCooling() == state)
                .map(Drone::getId)
                .toList();
    }

    public List<String> queryAsPath(String attribute, String value) {
        return fetchAllDrones().stream()
                .filter(d -> matches(d, attribute, "=", value))
                .map(Drone::getId)
                .toList();
    }

    public List<String> query(List<QueryAttribute> filters) {
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

        // String comparison (equality only)
        if (!(value instanceof Number)) {
            return operator.equals("=") &&
                    value.toString().equalsIgnoreCase(rawValue);
        }

        // Numerical comparison (supports <, >, =, !=)
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
        if (d == null || d.getCapability() == null) {
            return null;
        }

        return switch (attribute.toLowerCase()) {
            case "id" -> d.getId();
            case "name" -> d.getName();
            case "capacity" -> d.getCapability().getCapacity();
            case "cooling" -> d.getCapability().isCooling();
            case "heating" -> d.getCapability().isHeating();
            case "maxmoves" -> d.getCapability().getMaxMoves();
            case "costpermove" -> d.getCapability().getCostPerMove();
            case "costinitial" -> d.getCapability().getCostInitial();
            case "costfinal" -> d.getCapability().getCostFinal();
            default -> null;
        };
    }
}