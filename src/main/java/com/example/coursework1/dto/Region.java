package com.example.coursework1.dto;

import com.example.coursework1.model.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class Region {

    @NotNull(message = "name must not be null")
    private String name;

    @Valid
    @NotNull(message = "vertices must not be null")
    private List<Position> vertices;

    public Region() { }

    public Region(String name, List<Position> vertices) {
        this.name = name;
        this.vertices = vertices;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Position> getVertices() {
        return vertices;
    }

    public void setVertices(List<Position> vertices) {
        this.vertices = vertices;
    }
}
