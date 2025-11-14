package com.example.coursework1.dto;

import java.util.List;

public class DroneAvailability {

    private int droneId;
    private List<String> availableDays; // e.g., ["MONDAY", "TUESDAY", "WEDNESDAY"]
    private String startTime;  // e.g., "09:00"
    private String endTime;    // e.g., "17:00"

    public DroneAvailability() {}

    public DroneAvailability(int droneId, List<String> availableDays, String startTime, String endTime) {
        this.droneId = droneId;
        this.availableDays = availableDays;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getDroneId() { return droneId; }
    public void setDroneId(int droneId) { this.droneId = droneId; }

    public List<String> getAvailableDays() { return availableDays; }
    public void setAvailableDays(List<String> availableDays) { this.availableDays = availableDays; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}