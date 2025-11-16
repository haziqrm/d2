package com.example.coursework1.dto;

import java.util.List;

public class DroneWithAvailability {

    private String id;
    private List<TimeWindow> availability;

    public DroneWithAvailability() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<TimeWindow> getAvailability() { return availability; }
    public void setAvailability(List<TimeWindow> availability) {
        this.availability = availability;
    }
}