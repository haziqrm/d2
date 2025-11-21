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

        logger.info("Querying available drones for {} valid dispatches (single journey - must handle ALL in one trip)",
                validDispatches.size());

        List<Drone> allDrones = droneService.fetchAllDrones();

        List<ServicePointDrones> servicePointData = droneService.fetchDronesForServicePoints();

        Map<String, List<TimeWindow>> availabilityMap = buildAvailabilityMap(servicePointData);

        logger.debug("Built availability map for {} drones", availabilityMap.size());

        List<String> availableDroneIds = new ArrayList<>();

        for (Drone drone : allDrones) {
            if (canHandleAllDispatches(drone, validDispatches, availabilityMap)) {
                availableDroneIds.add(drone.getId());
                logger.debug("Drone {} CAN handle all {} dispatches in single journey",
                        drone.getId(), validDispatches.size());
            } else {
                logger.debug("Drone {} CANNOT handle all dispatches in single journey", drone.getId());
            }
        }

        logger.info("Found {} available drones (out of {}) that can handle ALL {} dispatches in single journey",
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

        double totalCapacityNeeded = dispatches.stream()
                .mapToDouble(d -> d.getRequirements().getCapacity())
                .sum();

        if (totalCapacityNeeded > capability.getCapacity() + EPS) {
            logger.trace("Drone {} failed total capacity check for single journey ({} > {})",
                    drone.getId(), totalCapacityNeeded, capability.getCapacity());
            return false;
        }

        for (MedDispatchRec dispatch : dispatches) {
            Requirements req = dispatch.getRequirements();

            if (capability.getCapacity() + EPS < req.getCapacity()) {
                logger.trace("Drone {} failed individual capacity check for dispatch {} ({} < {})",
                        drone.getId(), dispatch.getId(),
                        capability.getCapacity(), req.getCapacity());
                return false;
            }

            if (req.isCooling() && !capability.isCooling()) {
                logger.trace("Drone {} failed cooling check for dispatch {}",
                        drone.getId(), dispatch.getId());
                return false;
            }

            if (req.isHeating() && !capability.isHeating()) {
                logger.trace("Drone {} failed heating check for dispatch {}",
                        drone.getId(), dispatch.getId());
                return false;
            }

            if (!isAvailableForDispatch(drone.getId(), dispatch, availabilityMap)) {
                logger.trace("Drone {} failed availability check for dispatch {} ({} at {})",
                        drone.getId(), dispatch.getId(),
                        dispatch.getDate(), dispatch.getTime());
                return false;
            }

            if (req.getMaxCost() != null) {
                double minCost = capability.getCostInitial() + capability.getCostFinal();
                if (minCost > req.getMaxCost()) {
                    logger.trace("Drone {} failed cost check for dispatch {} ({} > {})",
                            drone.getId(), dispatch.getId(), minCost, req.getMaxCost());
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isAvailableForDispatch(String droneId, MedDispatchRec dispatch,
                                           Map<String, List<TimeWindow>> availabilityMap) {
        if (dispatch.getDate() == null || dispatch.getTime() == null) {
            return true;
        }

        List<TimeWindow> windows = availabilityMap.get(droneId);
        if (windows == null || windows.isEmpty()) {
            return true;
        }

        try {
            LocalDate date = LocalDate.parse(dispatch.getDate());
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            String dayName = dayOfWeek.toString();

            LocalTime dispatchTime = parseTime(dispatch.getTime());
            if (dispatchTime == null) {
                logger.warn("Could not parse dispatch time '{}', assuming available",
                        dispatch.getTime());
                return true;
            }

            for (TimeWindow window : windows) {
                if (isInTimeWindow(dayName, dispatchTime, window)) {
                    return true;
                }
            }

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
                DateTimeFormatter.ofPattern("HH:mm:ss"),
                DateTimeFormatter.ofPattern("H:mm:ss"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm")
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(timeStr, formatter);
            } catch (DateTimeParseException e) {
            }
        }

        return null;
    }
}