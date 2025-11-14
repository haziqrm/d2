package com.example.coursework1.dto;

public class Drone {

    private String id;
    private String name;
    private Capability capability;

    public Drone() {}

    public String getId() { return id; }
    public String getName() { return name; }
    public Capability getCapability() { return capability; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCapability(Capability capability) { this.capability = capability; }

    public boolean isCooling() {
        return capability != null && capability.isCooling();
    }

    public boolean isHeating() {
        return capability != null && capability.isHeating();
    }

    public double getCapacity() {
        return capability != null ? capability.getCapacity() : 0;
    }

    public int getMaxMoves() {
        return capability != null ? capability.getMaxMoves() : 0;
    }

    public double getCostPerMove() {
        return capability != null ? capability.getCostPerMove() : 0.0;
    }

    public double getCostInitial() {
        return capability != null ? capability.getCostInitial() : 0.0;
    }

    public double getCostFinal() {
        return capability != null ? capability.getCostFinal() : 0.0;
    }
}