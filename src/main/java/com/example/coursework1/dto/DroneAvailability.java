package com.example.coursework1.dto;

import java.util.List;

public class DroneAvailability {

    private String droneId;
    private List<String> availableDays;
    private String startTime;
    private String endTime;

    public DroneAvailability() {}

    public DroneAvailability(String droneId, List<String> availableDays, String startTime, String endTime) {
        this.droneId = droneId;
        this.availableDays = availableDays;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getDroneId() { return droneId; }
    public void setDroneId(String droneId) { this.droneId = droneId; }

    public List<String> getAvailableDays() { return availableDays; }
    public void setAvailableDays(List<String> availableDays) { this.availableDays = availableDays; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}