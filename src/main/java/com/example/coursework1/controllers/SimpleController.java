package com.example.coursework1.controllers;

import com.example.coursework1.dto.*;
import com.example.coursework1.model.Position;
import com.example.coursework1.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SimpleController {

    private final DistanceService distanceService;
    private final NavigationService navigationService;
    private final RegionService regionService;
    private final DroneService droneService;
    private final DeliveryPlannerService deliveryPlannerService;
    private final DroneAvailabilityService droneAvailabilityService;
    private final GeoJsonService geoJsonService;

    public SimpleController(DistanceService distanceService,
                            NavigationService navigationService,
                            RegionService regionService,
                            DroneService droneService,
                            DeliveryPlannerService deliveryPlannerService,
                            DroneAvailabilityService droneAvailabilityService,
                            GeoJsonService geoJsonService) {
        this.distanceService = distanceService;
        this.navigationService = navigationService;
        this.regionService = regionService;
        this.droneService = droneService;
        this.deliveryPlannerService = deliveryPlannerService;
        this.droneAvailabilityService = droneAvailabilityService;
        this.geoJsonService = geoJsonService;
    }

    @GetMapping("/uid")
    public ResponseEntity<String> uid() {
        return ResponseEntity.ok("s2488749");
    }

    @PostMapping("/distanceTo")
    public ResponseEntity<Double> distanceTo(@Valid @RequestBody DistanceRequest request) {
        double distance = distanceService.calculateDistance(request);
        return ResponseEntity.ok(distance);
    }

    @PostMapping("/isCloseTo")
    public ResponseEntity<Boolean> isCloseTo(@Valid @RequestBody DistanceRequest request) {
        boolean isClose = distanceService.isClose(request);
        return ResponseEntity.ok(isClose);
    }

    @PostMapping("/nextPosition")
    public ResponseEntity<Position> nextPosition(@Valid @RequestBody NextPositionRequest request) {
        Position next = navigationService.calculateNextPosition(request);
        return ResponseEntity.ok(next);
    }

    @PostMapping("/isInRegion")
    public ResponseEntity<Boolean> isInRegion(@Valid @RequestBody RegionRequest request) {
        boolean inside = regionService.isInRegion(request);
        return ResponseEntity.ok(inside);
    }

    @GetMapping("/dronesWithCooling/{state}")
    public ResponseEntity<List<String>> dronesWithCooling(@PathVariable boolean state) {
        List<String> ids = droneService.dronesWithCooling(state);
        return ResponseEntity.ok(ids);
    }

    @GetMapping("/droneDetails/{id}")
    public ResponseEntity<?> droneDetails(@PathVariable String id) {
        Drone drone = droneService.getDroneById(id);

        if (drone == null) {
            return ResponseEntity.status(404).build();
        }

        return ResponseEntity.ok(drone);
    }

    @GetMapping("/queryAsPath/{attribute}/{value}")
    public ResponseEntity<List<String>> queryAsPath(
            @PathVariable String attribute,
            @PathVariable String value
    ) {
        return ResponseEntity.ok(droneService.queryAsPath(attribute, value));
    }

    @PostMapping("/query")
    public ResponseEntity<List<String>> query(
            @RequestBody List<QueryAttribute> filters
    ) {
        return ResponseEntity.ok(droneService.query(filters));
    }

    @PostMapping("/queryAvailableDrones")
    public ResponseEntity<List<String>> queryAvailableDrones(
            @RequestBody List<MedDispatchRec> dispatches) {
        List<String> availableDrones = droneAvailabilityService.queryAvailableDrones(dispatches);
        return ResponseEntity.ok(availableDrones);
    }

    @PostMapping("/calcDeliveryPath")
    public ResponseEntity<CalcDeliveryResult> calcDeliveryPath(
            @RequestBody List<MedDispatchRec> recs) {

        CalcDeliveryResult result = deliveryPlannerService.calcDeliveryPath(recs);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/calcDeliveryPathAsGeoJson")
    public ResponseEntity<GeoJsonResponse> calcDeliveryPathAsGeoJson(
            @RequestBody List<MedDispatchRec> recs) {

        GeoJsonResponse geoJson = geoJsonService.calcDeliveryPathAsGeoJson(recs);
        return ResponseEntity.ok(geoJson);
    }
}