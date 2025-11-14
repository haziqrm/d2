package com.example.coursework1.dto;

import com.example.coursework1.model.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class DistanceRequest {

    @Valid
    @NotNull(message = "position1 must not be null")
    private Position position1;

    @Valid
    @NotNull(message = "position2 must not be null")
    private Position position2;

    public DistanceRequest() { }

    public DistanceRequest(Position position1, Position position2) {
        this.position1 = position1;
        this.position2 = position2;
    }

    public Position getPosition1() {
        return position1;
    }

    public void setPosition1(Position position1) {
        this.position1 = position1;
    }

    public Position getPosition2() {
        return position2;
    }

    public void setPosition2(Position position2) {
        this.position2 = position2;
    }
}
