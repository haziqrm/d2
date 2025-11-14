package com.example.coursework1.dto;

import com.example.coursework1.model.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class MedDispatchRec {

    @NotNull
    private Integer id;  // Integer per spec (not String)

    private String date;  // Format: "2025-12-22"
    private String time;  // Format: "14:30"

    @Valid
    @NotNull
    private Requirements requirements;

    @Valid
    @NotNull
    private Position delivery;  // Delivery coordinates (lng/lat)

    public MedDispatchRec() {}

    public MedDispatchRec(Integer id, String date, String time,
                          Requirements requirements, Position delivery) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.requirements = requirements;
        this.delivery = delivery;
    }

    public Integer getId() { return id; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public Requirements getRequirements() { return requirements; }
    public Position getDelivery() { return delivery; }

    public void setId(Integer id) { this.id = id; }
    public void setDate(String date) { this.date = date; }
    public void setTime(String time) { this.time = time; }
    public void setRequirements(Requirements requirements) { this.requirements = requirements; }
    public void setDelivery(Position delivery) { this.delivery = delivery; }
}