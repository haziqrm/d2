package com.example.coursework1.service;

import com.example.coursework1.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeoJsonService {

    private static final Logger logger = LoggerFactory.getLogger(GeoJsonService.class);
    private final DeliveryPlannerService deliveryPlannerService;

    public GeoJsonService(DeliveryPlannerService deliveryPlannerService) {
        this.deliveryPlannerService = deliveryPlannerService;
    }

    public Object calcDeliveryPathAsGeoJson(List<MedDispatchRec> dispatches) {
        CalcDeliveryResult result = deliveryPlannerService.calcDeliveryPath(dispatches);

        if (result.getDronePaths() != null && result.getDronePaths().size() > 1) {
            logger.info("Multiple drones ({}) used - generating FeatureCollection",
                    result.getDronePaths().size());
            return createFeatureCollection(result);
        }

        List<double[]> coordinates = new ArrayList<>();
        if (result.getDronePaths() == null || result.getDronePaths().isEmpty()) {
            logger.warn("No drone paths found for GeoJSON generation");
            coordinates.add(new double[]{0.0, 0.0});
        } else {
            DronePathResult dronePath = result.getDronePaths().get(0);
            extractPathFromDrone(dronePath, coordinates);
        }

        if (coordinates.isEmpty()) {
            coordinates.add(new double[]{0.0, 0.0});
        }

        GeoJsonResponse geoJson = new GeoJsonResponse(coordinates);
        geoJson.getProperties().put("totalMoves", result.getTotalMoves());
        geoJson.getProperties().put("totalCost", result.getTotalCost());
        geoJson.getProperties().put("deliveryCount",
                result.getDronePaths().isEmpty() ? 0 :
                        result.getDronePaths().get(0).getDeliveries().size());
        geoJson.getProperties().put("droneCount", result.getDronePaths().size());

        return geoJson;
    }

    private Map<String, Object> createFeatureCollection(CalcDeliveryResult result) {
        Map<String, Object> featureCollection = new HashMap<>();
        featureCollection.put("type", "FeatureCollection");

        List<Map<String, Object>> features = new ArrayList<>();

        for (DronePathResult dronePath : result.getDronePaths()) {
            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");

            List<double[]> coordinates = new ArrayList<>();
            extractPathFromDrone(dronePath, coordinates);

            if (!coordinates.isEmpty()) {
                Map<String, Object> geometry = new HashMap<>();
                geometry.put("type", "LineString");
                geometry.put("coordinates", coordinates);
                feature.put("geometry", geometry);

                Map<String, Object> properties = new HashMap<>();
                properties.put("droneId", dronePath.getDroneId());
                properties.put("deliveryCount", dronePath.getDeliveries().size());

                int droneMoves = 0;
                for (DeliveryResult delivery : dronePath.getDeliveries()) {
                    if (delivery.getFlightPath() != null) {
                        droneMoves += delivery.getFlightPath().size() - 1;
                    }
                }
                properties.put("moves", droneMoves);

                properties.put("totalCost", result.getTotalCost());
                properties.put("totalMoves", result.getTotalMoves());
                properties.put("droneCount", result.getDronePaths().size());

                feature.put("properties", properties);
                features.add(feature);
            }
        }

        featureCollection.put("features", features);

        return featureCollection;
    }

    private void extractPathFromDrone(DronePathResult dronePath, List<double[]> coordinates) {
        if (dronePath.getDeliveries() != null) {
            for (DeliveryResult delivery : dronePath.getDeliveries()) {
                if (delivery.getFlightPath() != null) {
                    for (LngLat point : delivery.getFlightPath()) {
                        coordinates.add(new double[]{point.getLng(), point.getLat()});
                    }
                }
            }
        }
    }
}