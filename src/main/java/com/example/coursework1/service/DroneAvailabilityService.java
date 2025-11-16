package com.example.coursework1.service;

import com.example.coursework1.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(DroneAvailabilityService.class);
    private final DroneService droneService;
    private static final double EPS = 1e-12;

    public DroneAvailabilityService(DroneService droneService) {
        this.droneService = droneService;
    }

    public List<String> queryAvailableDrones(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            logger.debug("No dispatches provided for availability query");
            return List.of();
        }

        List<MedDispatchRec> validDispatches = dispatches.stream()
                .filter(d -> d != null && d.getRequirements() != null)
                .collect(Collectors.toList());

        if (validDispatches.isEmpty()) {
            logger.warn("No valid dispatches found (all have null requirements)");
            return List.of();
        }

        logger.info("Querying available drones for {} valid dispatches (AND logic - must match ALL)",
                validDispatches.size());

        List<Drone> allDrones = droneService.fetchAllDrones();

        List<ServicePointDrones> servicePointData = droneService.fetchDronesForServicePoints();

        Map<String, List<TimeWindow>> availabilityMap = buildAvailabilityMap(servicePointData);

        logger.debug("Built availability map for {} drones", availabilityMap.size());

        List<String> availableDroneIds = new ArrayList<>();

        for (Drone drone : allDrones) {
            if (canHandleAllDispatches(drone, validDispatches, availabilityMap)) {
                availableDroneIds.add(drone.getId());
                logger.debug("Drone {} CAN handle all {} dispatches",
                        drone.getId(), validDispatches.size());
            } else {
                logger.debug("Drone {} CANNOT handle all dispatches", drone.getId());
            }
        }

        logger.info("Found {} available drones (out of {}) that can handle ALL {} dispatches",
                availableDroneIds.size(), allDrones.size(), validDispatches.size());

        return availableDroneIds;
    }

    private Map<String, List<TimeWindow>> buildAvailabilityMap(
            List<ServicePointDrones> servicePointData) {

        Map<String, List<TimeWindow>> map = new HashMap<>();

        for (ServicePointDrones spData : servicePointData) {
            if (spData.getDrones() == null) continue;

            for (DroneWithAvailability droneWithAvail : spData.getDrones()) {
                String droneId = droneWithAvail.getId();
                List<TimeWindow> windows = droneWithAvail.getAvailability();

                if (windows != null && !windows.isEmpty()) {
                    map.put(droneId, windows);
                }
            }
        }

        return map;
    }

    private boolean canHandleAllDispatches(Drone drone, List<MedDispatchRec> dispatches,
                                           Map<String, List<TimeWindow>> availabilityMap) {
        if (drone == null || drone.getCapability() == null) {
            return false;
        }

        Capability capability = drone.getCapability();

        for (MedDispatchRec dispatch : dispatches) {
            Requirements req = dispatch.getRequirements();

            if (capability.getCapacity() + EPS < req.getCapacity()) {
                logger.trace("Drone {} failed capacity check for dispatch {} ({} < {})",
                        drone.getId(), dispatch.getId(),
                        capability.getCapacity(), req.getCapacity());
                return false;
            }

            // Check 2: Cooling
            if (req.isCooling() && !capability.isCooling()) {
                logger.trace("Drone {} failed cooling check for dispatch {}",
                        drone.getId(), dispatch.getId());
                return false;
            }

            // Check 3: Heating
            if (req.isHeating() && !capability.isHeating()) {
                logger.trace("Drone {} failed heating check for dispatch {}",
                        drone.getId(), dispatch.getId());
                return false;
            }

            // Check 4: Date/Time Availability
            if (!isAvailableForDispatch(drone.getId(), dispatch, availabilityMap)) {
                logger.trace("Drone {} failed availability check for dispatch {} ({} at {})",
                        drone.getId(), dispatch.getId(),
                        dispatch.getDate(), dispatch.getTime());
                return false;
            }

            // Check 5: Cost (if specified)
            if (req.getMaxCost() != null) {
                double minCost = capability.getCostInitial() + capability.getCostFinal();
                if (minCost > req.getMaxCost()) {
                    logger.trace("Drone {} failed cost check for dispatch {} ({} > {})",
                            drone.getId(), dispatch.getId(), minCost, req.getMaxCost());
                    return false;
                }
            }
        }

        // All dispatches passed all checks
        return true;
    }

    private boolean isAvailableForDispatch(String droneId, MedDispatchRec dispatch,
                                           Map<String, List<TimeWindow>> availabilityMap) {
        // If no date/time specified, assume available (per spec)
        if (dispatch.getDate() == null || dispatch.getTime() == null) {
            return true;
        }

        // Get availability windows for this drone
        List<TimeWindow> windows = availabilityMap.get(droneId);
        if (windows == null || windows.isEmpty()) {
            // No availability data - assume available
            return true;
        }

        try {
            // Parse dispatch date and time
            LocalDate date = LocalDate.parse(dispatch.getDate());
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            String dayName = dayOfWeek.toString(); // "MONDAY", "TUESDAY", etc.

            LocalTime dispatchTime = parseTime(dispatch.getTime());
            if (dispatchTime == null) {
                logger.warn("Could not parse dispatch time '{}', assuming available",
                        dispatch.getTime());
                return true;
            }

            // Check if dispatch matches ANY time window
            for (TimeWindow window : windows) {
                if (isInTimeWindow(dayName, dispatchTime, window)) {
                    return true;
                }
            }

            // No matching window found
            return false;

        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse date '{}' for dispatch {}, assuming available",
                    dispatch.getDate(), dispatch.getId());
            return true;
        }
    }

    private boolean isInTimeWindow(String dayName, LocalTime dispatchTime, TimeWindow window) {
        if (!window.getDayOfWeek().equalsIgnoreCase(dayName)) {
            return false;
        }

        LocalTime fromTime = parseTime(window.getFrom());
        LocalTime untilTime = parseTime(window.getUntil());

        if (fromTime == null || untilTime == null) {
            logger.warn("Could not parse window times: from='{}', until='{}'",
                    window.getFrom(), window.getUntil());
            return false;
        }

        return !dispatchTime.isBefore(fromTime) && !dispatchTime.isAfter(untilTime);
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("HH:mm:ss"),   // "14:30:00" (API format)
                DateTimeFormatter.ofPattern("H:mm:ss"),    // "9:30:00"
                DateTimeFormatter.ofPattern("HH:mm"),      // "14:30"
                DateTimeFormatter.ofPattern("H:mm")        // "9:30"
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(timeStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        return null;
    }
}