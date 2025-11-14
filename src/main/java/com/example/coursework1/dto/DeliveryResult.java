package com.example.coursework1.dto;

import java.util.List;

public class DeliveryResult {

    private int deliveryId;
    private List<LngLat> flightPath;

    public DeliveryResult() {}

    public DeliveryResult(int deliveryId, List<LngLat> flightPath) {
        this.deliveryId = deliveryId;
        this.flightPath = flightPath;
    }

    public int getDeliveryId() { return deliveryId; }
    public List<LngLat> getFlightPath() { return flightPath; }

    public void setDeliveryId(int deliveryId) { this.deliveryId = deliveryId; }
    public void setFlightPath(List<LngLat> flightPath) { this.flightPath = flightPath; }
}