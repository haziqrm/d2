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

        public void setLng(double lng) { this.lng = lng; }
        public void setLat(double lat) { this.lat = lat; }
        public void setAlt(double alt) { this.alt = alt; }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Location getLocation() { return location; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setLocation(Location location) { this.location = location; }

    public Position getPosition() {
        return location != null ? new Position(location.lng, location.lat) : null;
    }
}