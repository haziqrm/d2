package com.example.coursework1.dto;

import com.example.coursework1.model.Position;

public class ServicePoint {

    private int id;
    private String name;
    private Location location;

    public static class Location {
        private double lng;
        private double lat;
        private double alt;

        public double getLng() { return lng; }
        public double getLat() { return lat; }
        public double getAlt() { return alt; }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Location getLocation() { return location; }

    public Position getPosition() {
        return new Position(location.lng, location.lat);
    }
}
