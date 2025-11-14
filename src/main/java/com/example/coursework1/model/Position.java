package com.example.coursework1.model;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class Position {

    @NotNull(message = "Longitude cannot be null")
    private Double lng;

    @NotNull(message = "Latitude cannot be null")
    private Double lat;

    public Position() {}

    public Position(Double lng, Double lat) {
        this.lng = lng;
        this.lat = lat;
    }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position)) return false;
        Position position = (Position) o;
        return Objects.equals(lng, position.lng) && Objects.equals(lat, position.lat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lng, lat);
    }

    @Override
    public String toString() {
        return String.format("Position(%.6f, %.6f)", lng, lat);
    }
}