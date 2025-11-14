package com.example.coursework1.dto;

import java.util.List;

public class DronePathResult {

    private String droneId;  // Changed to String
    private List<DeliveryResult> deliveries;

    public DronePathResult() {}

    public DronePathResult(String droneId, List<DeliveryResult> deliveries) {
        this.droneId = droneId;
        this.deliveries = deliveries;
    }

    public String getDroneId() { return droneId; }
    public List<DeliveryResult> getDeliveries() { return deliveries; }

    public void setDroneId(String droneId) { this.droneId = droneId; }
    public void setDeliveries(List<DeliveryResult> deliveries) { this.deliveries = deliveries; }
}