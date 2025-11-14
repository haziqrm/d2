package com.example.coursework1.dto;

import java.util.List;

public class DronePathResult {

    private int droneId;
    private List<DeliveryResult> deliveries;

    public DronePathResult() {}

    public DronePathResult(int droneId, List<DeliveryResult> deliveries) {
        this.droneId = droneId;
        this.deliveries = deliveries;
    }

    public int getDroneId() { return droneId; }
    public List<DeliveryResult> getDeliveries() { return deliveries; }

    public void setDroneId(int droneId) { this.droneId = droneId; }
    public void setDeliveries(List<DeliveryResult> deliveries) { this.deliveries = deliveries; }
}
