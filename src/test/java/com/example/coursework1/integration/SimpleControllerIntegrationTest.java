package com.example.coursework1.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SimpleControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void testHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"status\":\"UP\"}"));
    }

    @Test
    void testUID() throws Exception {
        mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andExpect(content().string("s2488749"));
    }

    // distanceTo tests
    @Test
    void testDistanceTo() throws Exception {
        Map<String, Object> body = Map.of(
                "position1", Map.of("lng", 0.0, "lat", 0.0),
                "position2", Map.of("lng", 3.0, "lat", 4.0)
        );

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().string("5.0"));
    }

    @Test
    void testDistanceToInvalidBody() throws Exception {
        Map<String, Object> badBody = Map.of(
                "position1", Map.of("lng", 0.0)
        );

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badBody)))
                .andExpect(status().isBadRequest());
    }

    // isCloseTo tests
    @Test
    void testIsCloseTo() throws Exception {
        Map<String, Object> body = Map.of(
                "position1", Map.of("lng", 0.0, "lat", 0.0),
                "position2", Map.of("lng", 0.0001, "lat", 0.0001)
        );

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void testIsCloseToMissingData() throws Exception {
        Map<String, Object> body = Map.of(
                "position1", Map.of("lng", 0.0),
                "position2", Map.of("lng", 0.0, "lat", 0.0)
        );

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // nextPosition tests
    @Test
    void testNextPositionValidAngle() throws Exception {
        Map<String, Object> body = Map.of(
                "start", Map.of("lng", 0.0, "lat", 0.0),
                "angle", 45.0
        );

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lng").isNumber())
                .andExpect(jsonPath("$.lat").isNumber());
    }

    @Test
    void testNextPositionInvalidAngle() throws Exception {
        Map<String, Object> body = Map.of(
                "start", Map.of("lng", 0.0, "lat", 0.0),
                "angle", 23.0
        );

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testNextPositionMissingStart() throws Exception {
        Map<String, Object> body = Map.of("angle", 90.0);

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testNextPositionMissingAngle() throws Exception {
        Map<String, Object> body = Map.of("start", Map.of("lng", 0.0, "lat", 0.0));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // isInRegion tests
    @Test
    void testIsInRegion() throws Exception {
        Map<String, Object> region = Map.of(
                "name", "Square",
                "vertices", List.of(
                        Map.of("lng", 0.0, "lat", 0.0),
                        Map.of("lng", 0.0, "lat", 1.0),
                        Map.of("lng", 1.0, "lat", 1.0),
                        Map.of("lng", 1.0, "lat", 0.0),
                        Map.of("lng", 0.0, "lat", 0.0)
                )
        );

        Map<String, Object> body = Map.of(
                "position", Map.of("lng", 0.5, "lat", 0.5),
                "region", region
        );

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void testIsInRegionMissingPosition() throws Exception {
        Map<String, Object> region = Map.of(
                "name", "Square",
                "vertices", List.of(
                        Map.of("lng", 0.0, "lat", 0.0),
                        Map.of("lng", 0.0, "lat", 1.0),
                        Map.of("lng", 1.0, "lat", 1.0),
                        Map.of("lng", 1.0, "lat", 0.0),
                        Map.of("lng", 0.0, "lat", 0.0)
                )
        );

        Map<String, Object> body = Map.of("region", region);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testIsInRegionMissingVertices() throws Exception {
        Map<String, Object> region = Map.of("name", "Square");

        Map<String, Object> body = Map.of(
                "position", Map.of("lng", 0.5, "lat", 0.5),
                "region", region
        );

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
