package com.example.coursework1.dto;

public class Drone {

    private int id;
    private String name;
    private Capability capability;

    public Drone() {}

    public int getId() { return id; }
    public String getName() { return name; }
    public Capability getCapability() { return capability; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCapability(Capability capability) { this.capability = capability; }

    public boolean isCooling() { return capability != null && capability.isCooling(); }
    public boolean isHeating() { return capability != null && capability.isHeating(); }
    public double getCapacity() { return capability != null ? capability.getCapacity() : 0; }
}
