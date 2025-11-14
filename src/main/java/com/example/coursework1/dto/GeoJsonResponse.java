package com.example.coursework1.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonPropertyOrder({"type", "geometry", "properties"})
public class GeoJsonResponse {

    private String type = "Feature";
    private Geometry geometry;
    private Map<String, Object> properties;

    public GeoJsonResponse() {
        this.properties = new HashMap<>();
    }

    public GeoJsonResponse(List<double[]> coordinates) {
        this.geometry = new Geometry(coordinates);
        this.properties = new HashMap<>();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @JsonPropertyOrder({"type", "coordinates"})
    public static class Geometry {
        private String type = "LineString";
        private List<double[]> coordinates;

        public Geometry() {}

        public Geometry(List<double[]> coordinates) {
            this.coordinates = coordinates;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<double[]> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<double[]> coordinates) {
            this.coordinates = coordinates;
        }
    }
}