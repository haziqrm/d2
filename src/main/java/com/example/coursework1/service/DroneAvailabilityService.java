package com.example.coursework1.service;

import com.example.coursework1.dto.*;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DroneAvailabilityService {

    private final DroneService droneService;
    private static final double EPS = 1e-12;

    public DroneAvailabilityService(DroneService droneService) {
        this.droneService = droneService;
    }

    public List<Integer> queryAvailableDrones(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return List.of();
        }

        List<MedDispatchRec> validDispatches = dispatches.stream()
                .filter(d -> d != null && d.getRequirements() != null)
                .collect(Collectors.toList());

        if (validDispatches.isEmpty()) {
            return List.of();
        }

        List<Drone> allDrones = droneService.fetchAllDrones();
        List<DroneAvailability> availabilityList = droneService.fetchDroneAvailability();

        Map<Integer, DroneAvailability> availabilityMap = availabilityList.stream()
                .collect(Collectors.toMap(
                        DroneAvailability::getDroneId,
                        a -> a,
                        (a, b) -> a
                ));

        List<Integer> availableDroneIds = new ArrayList<>();

        for (Drone drone : allDrones) {
            if (canHandleAllDispatches(drone, validDispatches, availabilityMap)) {
                availableDroneIds.add(drone.getId());
            }
        }

        return availableDroneIds;
    }

    private boolean canHandleAllDispatches(Drone drone, List<MedDispatchRec> dispatches,
                                           Map<Integer, DroneAvailability> availabilityMap) {
        if (drone == null || drone.getCapability() == null) {
            return false;
        }

        Capability capability = drone.getCapability();

        for (MedDispatchRec dispatch : dispatches) {
            Requirements req = dispatch.getRequirements();

            if (capability.getCapacity() + EPS < req.getCapacity()) {
                return false;
            }

            if (req.isCooling() && !capability.isCooling()) {
                return false;
            }

            if (req.isHeating() && !capability.isHeating()) {
                return false;
            }

            if (!isAvailableForDispatch(drone.getId(), dispatch, availabilityMap)) {
                return false;
            }

            if (req.getMaxCost() != null) {
                double minCost = capability.getCostInitial() + capability.getCostFinal();
                if (minCost > req.getMaxCost()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isAvailableForDispatch(int droneId, MedDispatchRec dispatch,
                                           Map<Integer, DroneAvailability> availabilityMap) {
        if (dispatch.getDate() == null || dispatch.getTime() == null) {
            return true;
        }

        DroneAvailability availability = availabilityMap.get(droneId);
        if (availability == null) {
            return true;
        }

        try {
            LocalDate date = LocalDate.parse(dispatch.getDate());
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            String dayName = dayOfWeek.toString();

            List<String> availableDays = availability.getAvailableDays();
            if (availableDays == null || !availableDays.contains(dayName)) {
                return false;
            }

            if (availability.getStartTime() != null && availability.getEndTime() != null) {
                LocalTime dispatchTime = parseTime(dispatch.getTime());
                LocalTime startTime = parseTime(availability.getStartTime());
                LocalTime endTime = parseTime(availability.getEndTime());

                if (dispatchTime == null || startTime == null || endTime == null) {
                    return true;
                }

                if (dispatchTime.isBefore(startTime) || dispatchTime.isAfter(endTime)) {
                    return false;
                }
            }

            return true;

        } catch (DateTimeParseException e) {
            return true;
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e1) {
            try {
                return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm"));
            } catch (DateTimeParseException e2) {
                try {
                    return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
                } catch (DateTimeParseException e3) {
                    return null;
                }
            }
        }
    }
}