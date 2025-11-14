package com.example.coursework1.dto;

import java.util.List;

public class CalcDeliveryResult {

    private double totalCost;
    private int totalMoves;
    private List<DronePathResult> dronePaths;

    public CalcDeliveryResult() {}

    public CalcDeliveryResult(double totalCost, int totalMoves, List<DronePathResult> dronePaths) {
        this.totalCost = totalCost;
        this.totalMoves = totalMoves;
        this.dronePaths = dronePaths;
    }

    public double getTotalCost() { return totalCost; }
    public int getTotalMoves() { return totalMoves; }
    public List<DronePathResult> getDronePaths() { return dronePaths; }

    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
    public void setTotalMoves(int totalMoves) { this.totalMoves = totalMoves; }
    public void setDronePaths(List<DronePathResult> dronePaths) { this.dronePaths = dronePaths; }
}
