package com.example.coursework1.model;

import java.util.List;

public class RestrictedArea {

    private String name;
    private Integer id;
    private Limits limits;
    private List<Position> vertices;

    public RestrictedArea() {}

    public RestrictedArea(String name, Integer id, Limits limits, List<Position> vertices) {
        this.name = name;
        this.id = id;
        this.limits = limits;
        this.vertices = vertices;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Limits getLimits() { return limits; }
    public void setLimits(Limits limits) { this.limits = limits; }

    public List<Position> getVertices() { return vertices; }
    public void setVertices(List<Position> vertices) { this.vertices = vertices; }

    public static class Limits {
        private Double lower;
        private Double upper;

        public Limits() {}

        public Limits(Double lower, Double upper) {
            this.lower = lower;
            this.upper = upper;
        }

        public Double getLower() { return lower; }
        public void setLower(Double lower) { this.lower = lower; }

        public Double getUpper() { return upper; }
        public void setUpper(Double upper) { this.upper = upper; }
    }
}