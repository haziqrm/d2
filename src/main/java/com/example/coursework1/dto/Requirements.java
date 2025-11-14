package com.example.coursework1.dto;

public class Requirements {

    private double capacity;
    private boolean cooling;
    private boolean heating;
    private Double maxCost;

    public Requirements() {}

    public Requirements(double capacity, boolean cooling, boolean heating, Double maxCost) {
        this.capacity = capacity;
        this.cooling = cooling;
        this.heating = heating;
        this.maxCost = maxCost;
    }

    public double getCapacity() { return capacity; }
    public boolean isCooling() { return cooling; }
    public boolean isHeating() { return heating; }
    public Double getMaxCost() { return maxCost; }

    public void setCapacity(double capacity) { this.capacity = capacity; }
    public void setCooling(boolean cooling) { this.cooling = cooling; }
    public void setHeating(boolean heating) { this.heating = heating; }
    public void setMaxCost(Double maxCost) { this.maxCost = maxCost; }
}
