package com.example.coursework1.dto;

import com.example.coursework1.model.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class NextPositionRequest {

    @Valid
    @NotNull(message = "start must not be null")
    private Position start;

    @NotNull(message = "angle must not be null")
    @Min(value = -180, message = "Angle must be between -180 and 180")
    @Max(value = 180, message = "Angle must be between -180 and 180")
    private Double angle;

    public NextPositionRequest() { }

    public NextPositionRequest(Position start, Double angle) {
        this.start = start;
        this.angle = angle;
    }

    public Position getStart() {
        return start;
    }

    public void setStart(Position start) {
        this.start = start;
    }

    public Double getAngle() {
        return angle;
    }

    public void setAngle(Double angle) {
        this.angle = angle;
    }
}