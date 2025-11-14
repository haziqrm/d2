package com.example.coursework1.dto;

public class Capability {

    private boolean cooling;
    private boolean heating;
    private double capacity;
    private int maxMoves;
    private double costPerMove;
    private double costInitial;
    private double costFinal;

    public Capability() {}

    public boolean isCooling() { return cooling; }
    public boolean isHeating() { return heating; }
    public double getCapacity() { return capacity; }
    public int getMaxMoves() { return maxMoves; }
    public double getCostPerMove() { return costPerMove; }
    public double getCostInitial() { return costInitial; }
    public double getCostFinal() { return costFinal; }

    public void setCooling(boolean cooling) { this.cooling = cooling; }
    public void setHeating(boolean heating) { this.heating = heating; }
    public void setCapacity(double capacity) { this.capacity = capacity; }
    public void setMaxMoves(int maxMoves) { this.maxMoves = maxMoves; }
    public void setCostPerMove(double costPerMove) { this.costPerMove = costPerMove; }
    public void setCostInitial(double costInitial) { this.costInitial = costInitial; }
    public void setCostFinal(double costFinal) { this.costFinal = costFinal; }
}
