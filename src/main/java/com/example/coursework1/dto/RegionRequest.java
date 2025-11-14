package com.example.coursework1.dto;

import com.example.coursework1.model.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class RegionRequest {

    @Valid
    @NotNull(message = "position must not be null")
    private Position position;

    @Valid
    @NotNull(message = "region must not be null")
    private Region region;

    public RegionRequest() { }

    public RegionRequest(Position position, Region region) {
        this.position = position;
        this.region = region;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }
}