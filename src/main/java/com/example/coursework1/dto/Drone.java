package com.example.coursework1.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @JsonIgnore
    public boolean isCooling() {
        return capability != null && capability.isCooling();
    }

    @JsonIgnore
    public boolean isHeating() {
        return capability != null && capability.isHeating();
    }

    @JsonIgnore
    public double getCapacity() {
        return capability != null ? capability.getCapacity() : 0;
    }

    @JsonIgnore
    public int getMaxMoves() {
        return capability != null ? capability.getMaxMoves() : 0;
    }

    @JsonIgnore
    public double getCostPerMove() {
        return capability != null ? capability.getCostPerMove() : 0.0;
    }

    @JsonIgnore
    public double getCostInitial() {
        return capability != null ? capability.getCostInitial() : 0.0;
    }

    @JsonIgnore
    public double getCostFinal() {
        return capability != null ? capability.getCostFinal() : 0.0;
    }
}