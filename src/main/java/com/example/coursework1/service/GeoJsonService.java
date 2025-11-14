package com.example.coursework1.service;

import com.example.coursework1.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeoJsonService {

    private final DeliveryPlannerService deliveryPlannerService;

    public GeoJsonService(DeliveryPlannerService deliveryPlannerService) {
        this.deliveryPlannerService = deliveryPlannerService;
    }

    public GeoJsonResponse calcDeliveryPathAsGeoJson(List<MedDispatchRec> dispatches) {
        CalcDeliveryResult result = deliveryPlannerService.calcDeliveryPath(dispatches);
        List<double[]> coordinates = new ArrayList<>();

        if (result.getDronePaths() != null && !result.getDronePaths().isEmpty()) {
            DronePathResult dronePath = result.getDronePaths().get(0);

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

        if (coordinates.isEmpty()) {
            coordinates.add(new double[]{0.0, 0.0});
        }

        GeoJsonResponse geoJson = new GeoJsonResponse(coordinates);

        geoJson.getProperties().put("totalMoves", result.getTotalMoves());
        geoJson.getProperties().put("totalCost", result.getTotalCost());
        geoJson.getProperties().put("deliveryCount",
                result.getDronePaths().isEmpty() ? 0 :
                        result.getDronePaths().get(0).getDeliveries().size());

        return geoJson;
    }
}