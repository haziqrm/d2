package com.example.coursework1.dto;

import java.util.List;

public class ServicePointDrones {

    private Integer servicePointId;
    private List<DroneWithAvailability> drones;

    public ServicePointDrones() {}

    public Integer getServicePointId() { return servicePointId; }
    public void setServicePointId(Integer servicePointId) {
        this.servicePointId = servicePointId;
    }

    public List<DroneWithAvailability> getDrones() { return drones; }
    public void setDrones(List<DroneWithAvailability> drones) {
        this.drones = drones;
    }
}