package com.example.coursework1.service;

import com.example.coursework1.dto.*;
import com.example.coursework1.model.Position;
import com.example.coursework1.model.RestrictedArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeliveryPlannerService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPlannerService.class);

    private final DroneService droneService;
    private final ServicePointService servicePointService;
    private final RestrictedAreaService restrictedAreaService;
    private final DroneAvailabilityService droneAvailabilityService;

    private static final double STEP = 0.00015;
    private static final double ANGLE_INCREMENT = 22.5;
    private static final double CLOSE_THRESHOLD = 0.00015;
    private static final double EPS = 1e-12;
    private static final int MAX_PATH_ITERATIONS = 30000;

    public DeliveryPlannerService(DroneService droneService,
                                  ServicePointService servicePointService,
                                  RestrictedAreaService restrictedAreaService,
                                  DroneAvailabilityService droneAvailabilityService) {
        this.droneService = droneService;
        this.servicePointService = servicePointService;
        this.restrictedAreaService = restrictedAreaService;
        this.droneAvailabilityService = droneAvailabilityService;
    }

    public CalcDeliveryResult calcDeliveryPath(List<MedDispatchRec> dispatches) {
        logger.info("=== Starting calcDeliveryPath for {} dispatches ===",
                dispatches != null ? dispatches.size() : 0);

        if (dispatches == null || dispatches.isEmpty()) {
            return new CalcDeliveryResult(0.0, 0, List.of());
        }

        Set<String> uniqueDates = dispatches.stream()
                .map(MedDispatchRec::getDate)
                .filter(date -> date != null && !date.isEmpty())
                .collect(Collectors.toSet());

        if (uniqueDates.size() > 1) {
            logger.error("Cannot plan delivery path for dispatches on different dates: {}", uniqueDates);
            throw new IllegalArgumentException(
                    "All dispatches must be on the same date. Found dates: " + uniqueDates);
        }

        if (uniqueDates.isEmpty()) {
            logger.warn("No valid dates found in dispatches");
        } else {
            logger.info("Planning delivery path for date: {}", uniqueDates.iterator().next());
        }

        List<MedDispatchRec> pending = new ArrayList<>(dispatches.stream()
                .filter(d -> d != null && d.getId() != null &&
                        d.getRequirements() != null && d.getDelivery() != null)
                .toList());

        List<Drone> allDrones = droneService.fetchAllDrones();
        List<ServicePoint> servicePoints = servicePointService.fetchAllServicePoints();

        Position defaultBase = servicePoints.isEmpty() ?
                new Position(0.0, 0.0) : safeGetPosition(servicePoints.get(0));

        logger.info("PHASE 1: Checking if any single drone can handle all {} dispatches", pending.size());
        List<String> singleDroneCapable = droneAvailabilityService.queryAvailableDrones(pending);

        if (!singleDroneCapable.isEmpty()) {
            logger.info("Found {} drones capable of handling all dispatches in single journey: {}",
                    singleDroneCapable.size(), singleDroneCapable);

            List<Drone> capableDrones = allDrones.stream()
                    .filter(d -> singleDroneCapable.contains(d.getId()))
                    .sorted(Comparator.comparingDouble((Drone dr) -> -safeGetCapabilityCapacity(dr)))
                    .toList();

            for (Drone drone : capableDrones) {
                logger.info("Attempting single-drone delivery with drone {}", drone.getId());
                CalcDeliveryResult singleDroneResult = planSingleDroneDelivery(
                        drone, new ArrayList<>(pending), defaultBase);

                if (singleDroneResult != null && !singleDroneResult.getDronePaths().isEmpty()) {
                    logger.info("✓ Successfully planned all deliveries with single drone {}!", drone.getId());
                    logger.info("=== Completed: 1 drone, {} moves, ${} cost ===",
                            singleDroneResult.getTotalMoves(), singleDroneResult.getTotalCost());
                    return singleDroneResult;
                }
            }

            logger.warn("Single-drone capable drones found but pathfinding failed, falling back to multi-drone");
        } else {
            logger.info("No single drone can handle all dispatches, proceeding with multi-drone strategy");
        }

        logger.info("PHASE 2: Planning multi-drone delivery");
        return planMultiDroneDelivery(pending, dispatches, allDrones, defaultBase);
    }

    private CalcDeliveryResult planSingleDroneDelivery(Drone drone, List<MedDispatchRec> dispatches,
                                                       Position base) {
        Capability cap = drone.getCapability();
        if (cap == null) return null;

        List<DeliveryResult> allDeliveries = new ArrayList<>();
        Position current = base;
        int totalMoves = 0;

        for (MedDispatchRec dispatch : dispatches) {
            Position dest = dispatch.getDelivery();

            logger.debug("Planning path for delivery {} from {} to {}",
                    dispatch.getId(), current, dest);

            List<LngLat> pathToDest = buildPathAvoidingRestrictions(current, dest);

            if (pathToDest == null || pathToDest.isEmpty()) {
                logger.warn("Failed to find path for delivery {}, trying relaxed", dispatch.getId());
                diagnoseDeliveryFailure(dispatch, current);
                pathToDest = buildPathWithRelaxedConstraints(current, dest);
            }

            if (pathToDest == null || pathToDest.isEmpty()) {
                logger.error("All pathfinding failed for delivery {} - cannot complete single-drone delivery",
                        dispatch.getId());
                return null;
            }

            int closestIndex = 0;
            double closestDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < pathToDest.size(); i++) {
                LngLat point = pathToDest.get(i);
                Position pos = new Position(point.getLng(), point.getLat());
                double d = dist(pos, dest);
                if (d < closestDist) {
                    closestDist = d;
                    closestIndex = i;
                }
            }

            pathToDest = new ArrayList<>(pathToDest.subList(0, closestIndex + 1));

            if (!allDeliveries.isEmpty() && !pathToDest.isEmpty()) {
                pathToDest = new ArrayList<>(pathToDest.subList(1, pathToDest.size()));
            }

            LngLat hoverPoint = pathToDest.get(pathToDest.size() - 1);
            pathToDest.add(new LngLat(hoverPoint.getLng(), hoverPoint.getLat()));

            current = new Position(hoverPoint.getLng(), hoverPoint.getLat());

            int steps = pathToDest.size() - 1;
            totalMoves += steps;

            allDeliveries.add(new DeliveryResult(dispatch.getId(), pathToDest));

            logger.debug("Added delivery {} ({} steps, hovering at {}, {} - distance to target: {})",
                    dispatch.getId(), steps, current.getLng(), current.getLat(), closestDist);
        }

        List<LngLat> returnPath = buildPathAvoidingRestrictions(current, base);
        if (returnPath == null) {
            returnPath = buildPathWithRelaxedConstraints(current, base);
        }

        if (returnPath == null || returnPath.isEmpty()) {
            logger.error("Failed to find return path - cannot complete single-drone delivery");
            return null;
        }

        int returnSteps = returnPath.size() - 1;
        totalMoves += returnSteps;

        if (totalMoves > cap.getMaxMoves()) {
            logger.warn("Total moves {} exceeds drone {} maxMoves {}",
                    totalMoves, drone.getId(), cap.getMaxMoves());
            return null;
        }

        if (!allDeliveries.isEmpty()) {
            DeliveryResult lastDelivery = allDeliveries.get(allDeliveries.size() - 1);
            List<LngLat> lastPath = new ArrayList<>(lastDelivery.getFlightPath());
            for (int i = 1; i < returnPath.size(); i++) {
                lastPath.add(returnPath.get(i));
            }
            lastDelivery.setFlightPath(lastPath);
        }

        double totalCost = computeFlightCost(cap, totalMoves);

        DronePathResult dronePathResult = new DronePathResult(drone.getId(), allDeliveries);

        logger.info("Single drone {} completed all {} deliveries in {} moves, ${} cost",
                drone.getId(), allDeliveries.size(), totalMoves, totalCost);

        return new CalcDeliveryResult(totalCost, totalMoves, List.of(dronePathResult));
    }

    private CalcDeliveryResult planMultiDroneDelivery(List<MedDispatchRec> pending,
                                                      List<MedDispatchRec> allDispatches,
                                                      List<Drone> allDrones,
                                                      Position defaultBase) {
        double totalCost = 0.0;
        int totalMoves = 0;
        List<DronePathResult> dronePaths = new ArrayList<>();

        List<Drone> sortedDrones = allDrones.stream()
                .sorted(Comparator.comparingDouble((Drone dr) -> -safeGetCapabilityCapacity(dr)))
                .toList();

        logger.info("Processing with {} total drones", sortedDrones.size());

        for (Drone drone : sortedDrones) {
            if (pending.isEmpty()) break;

            Capability cap = drone.getCapability();
            if (cap == null) continue;

            Position base = defaultBase;
            List<DeliveryResult> allDeliveries = new ArrayList<>();
            int totalDroneMoves = 0;
            double totalDroneCost = 0.0;
            int flightNumber = 0;

            while (!pending.isEmpty()) {
                flightNumber++;
                logger.info("Drone {} starting flight #{}", drone.getId(), flightNumber);

                Position current = base;
                int movesLeft = safeGetMaxMoves(cap);
                int usedMovesThisFlight = 0;
                double capacityUsed = 0.0;

                List<DeliveryResult> flightDeliveries = new ArrayList<>();

                List<MedDispatchRec> candidates = pending.stream()
                        .filter(m -> {
                            if (!fitsRequirements(m.getRequirements(), cap)) {
                                return false;
                            }

                            List<String> available = droneAvailabilityService.queryAvailableDrones(List.of(m));
                            boolean isAvailable = available.contains(drone.getId());
                            if (!isAvailable) {
                                logger.trace("Drone {} not available for dispatch {}", drone.getId(), m.getId());
                            }
                            return isAvailable;
                        })
                        .collect(Collectors.toCollection(ArrayList::new));

                if (candidates.isEmpty()) {
                    logger.debug("Drone {} has no valid candidates for flight #{}", drone.getId(), flightNumber);
                    break;
                }

                logger.info("Drone {} has {} candidates for flight #{}", drone.getId(), candidates.size(), flightNumber);

                while (!candidates.isEmpty() && movesLeft > 0) {
                    MedDispatchRec next = nearest(current, candidates);
                    if (next == null) break;

                    Position dest = next.getDelivery();

                    logger.debug("Considering delivery {} from ({}, {}) to ({}, {})",
                            next.getId(), current.getLng(), current.getLat(),
                            dest.getLng(), dest.getLat());

                    if (capacityUsed + next.getRequirements().getCapacity() > cap.getCapacity() + EPS) {
                        logger.debug("Adding delivery {} would exceed capacity ({} + {} > {})",
                                next.getId(), capacityUsed, next.getRequirements().getCapacity(), cap.getCapacity());
                        candidates.remove(next);
                        continue;
                    }

                    List<LngLat> pathToDest = buildPathAvoidingRestrictions(current, dest);

                    if (pathToDest == null || pathToDest.isEmpty()) {
                        logger.warn("Failed to find path for delivery {}, trying relaxed", next.getId());
                        diagnoseDeliveryFailure(next, current);
                        pathToDest = buildPathWithRelaxedConstraints(current, dest);
                    }

                    if (pathToDest == null || pathToDest.isEmpty()) {
                        logger.error("All pathfinding failed for delivery {} - SKIPPING", next.getId());
                        candidates.remove(next);
                        pending.remove(next);
                        continue;
                    }

                    int closestIndex = 0;
                    double closestDist = Double.POSITIVE_INFINITY;
                    for (int i = 0; i < pathToDest.size(); i++) {
                        LngLat point = pathToDest.get(i);
                        Position pos = new Position(point.getLng(), point.getLat());
                        double d = dist(pos, dest);
                        if (d < closestDist) {
                            closestDist = d;
                            closestIndex = i;
                        }
                    }

                    pathToDest = new ArrayList<>(pathToDest.subList(0, closestIndex + 1));

                    if (!flightDeliveries.isEmpty() && !pathToDest.isEmpty()) {
                        pathToDest = new ArrayList<>(pathToDest.subList(1, pathToDest.size()));
                    }

                    LngLat hoverPoint = pathToDest.get(pathToDest.size() - 1);
                    pathToDest.add(new LngLat(hoverPoint.getLng(), hoverPoint.getLat()));

                    int toDest = pathToDest.size() - 1;
                    int back = estimateStepsBack(dest, base);

                    if (toDest + back > movesLeft) {
                        logger.debug("Not enough moves for delivery {} ({} + {} > {})",
                                next.getId(), toDest, back, movesLeft);
                        candidates.remove(next);
                        continue;
                    }

                    current = new Position(hoverPoint.getLng(), hoverPoint.getLat());

                    movesLeft -= toDest;
                    usedMovesThisFlight += toDest;
                    capacityUsed += next.getRequirements().getCapacity();

                    flightDeliveries.add(new DeliveryResult(next.getId(), pathToDest));
                    pending.remove(next);
                    candidates.remove(next);

                    logger.info("Delivery {} added ({} moves, {} moves left, {}/{} capacity used, hovering at {}, {} - distance to target: {})",
                            next.getId(), toDest, movesLeft, capacityUsed, cap.getCapacity(),
                            current.getLng(), current.getLat(), closestDist);
                }

                if (flightDeliveries.isEmpty()) {
                    logger.debug("No deliveries completed in flight #{}, stopping", flightNumber);
                    break;
                }

                List<LngLat> returnPath = buildPathAvoidingRestrictions(current, base);
                if (returnPath == null) {
                    returnPath = buildPathWithRelaxedConstraints(current, base);
                }

                int stepsBack = returnPath != null ? returnPath.size() - 1 : estimateStepsBack(current, base);

                if (stepsBack > movesLeft || returnPath == null) {
                    logger.warn("Not enough moves to return - removing deliveries from this flight");
                    for (DeliveryResult dr : flightDeliveries) {
                        pending.add(allDispatches.stream()
                                .filter(d -> d.getId().equals(dr.getDeliveryId()))
                                .findFirst()
                                .orElse(null));
                    }
                    break;
                }

                if (!flightDeliveries.isEmpty() && returnPath != null) {
                    DeliveryResult lastDelivery = flightDeliveries.get(flightDeliveries.size() - 1);
                    List<LngLat> lastPath = new ArrayList<>(lastDelivery.getFlightPath());

                    for (int i = 1; i < returnPath.size(); i++) {
                        lastPath.add(returnPath.get(i));
                    }

                    lastDelivery.setFlightPath(lastPath);
                }

                usedMovesThisFlight += stepsBack;
                totalDroneMoves += usedMovesThisFlight;

                double flightCost = computeFlightCost(cap, usedMovesThisFlight);
                totalDroneCost += flightCost;

                allDeliveries.addAll(flightDeliveries);

                logger.info("Flight #{} completed: {} deliveries, {} moves, ${} cost",
                        flightNumber, flightDeliveries.size(), usedMovesThisFlight, flightCost);

                LngLat baseHoverPoint = returnPath.get(returnPath.size() - 1);
                current = new Position(baseHoverPoint.getLng(), baseHoverPoint.getLat());
            }

            if (!allDeliveries.isEmpty()) {
                totalCost += totalDroneCost;
                totalMoves += totalDroneMoves;
                dronePaths.add(new DronePathResult(drone.getId(), allDeliveries));

                logger.info("Drone {} completed: {} deliveries, {} moves, ${} cost",
                        drone.getId(), allDeliveries.size(), totalDroneMoves, totalDroneCost);
            }
        }

        logger.info("=== Multi-drone completed: {} drones, {} moves, ${} cost ===",
                dronePaths.size(), totalMoves, totalCost);

        return new CalcDeliveryResult(totalCost, totalMoves, dronePaths);
    }

    private List<LngLat> buildPathAvoidingRestrictions(Position from, Position to) {
        if (from == null || to == null) {
            logger.error("Null position in buildPath: from={}, to={}", from, to);
            return null;
        }

        double totalDistance = dist(from, to);
        logger.debug("Building path from {} to {}, distance={}", from, to, totalDistance);

        List<LngLat> path = new ArrayList<>();
        path.add(new LngLat(from.getLng(), from.getLat()));

        Position current = new Position(from.getLng(), from.getLat());
        int iterations = 0;
        int consecutiveBlocked = 0;

        while (!isCloseEnough(current, to) && iterations < MAX_PATH_ITERATIONS) {
            iterations++;

            double targetAngle = calculateAngle(current, to);
            Position nextDirect = moveInDirection(current, targetAngle);

            double distanceToTarget = dist(current, to);
            if (distanceToTarget < STEP * 5 && iterations % 50 == 0) {
                logger.debug("Getting close to target: distance={}, iteration={}",
                        distanceToTarget, iterations);
            }

            if (!pathSegmentCrossesRestriction(current, nextDirect)) {
                current = nextDirect;
                path.add(new LngLat(current.getLng(), current.getLat()));
                consecutiveBlocked = 0;
            } else {
                logger.trace("Direct path blocked at iteration {}, trying alternatives", iterations);

                Position nextPos = findAlternativeMove(current, to, targetAngle);

                if (nextPos == null) {
                    logger.warn("No alternative move found at iteration {} (distance to target: {})",
                            iterations, distanceToTarget);

                    if (distanceToTarget < CLOSE_THRESHOLD * 1.5) {
                        logger.debug("Close enough to target, accepting current position");
                        break;
                    }

                    return null;
                }

                current = nextPos;
                path.add(new LngLat(current.getLng(), current.getLat()));
                consecutiveBlocked++;

                if (consecutiveBlocked > 30) {
                    logger.warn("Blocked {} consecutive times, may be stuck", consecutiveBlocked);
                }
            }

            if (iterations % 2000 == 0) {
                logger.debug("Pathfinding iteration {}, distance remaining: {}, consecutive blocked: {}",
                        iterations, dist(current, to), consecutiveBlocked);
            }
        }

        if (iterations >= MAX_PATH_ITERATIONS) {
            logger.warn("Exceeded max iterations building path from {} to {}", from, to);
            return null;
        }

        double finalDistance = dist(current, to);
        logger.debug("Path built with {} steps, final position {}, distance to target: {}",
                path.size(), current, finalDistance);

        return path;
    }

    private List<LngLat> buildPathWithRelaxedConstraints(Position from, Position to) {
        logger.info("Trying RELAXED pathfinding from {} to {} (distance={})",
                from, to, dist(from, to));

        List<LngLat> path = new ArrayList<>();
        path.add(new LngLat(from.getLng(), from.getLat()));

        Position current = new Position(from.getLng(), from.getLat());
        int iterations = 0;
        int stuckCounter = 0;
        double lastDistance = dist(current, to);
        double bestDistance = lastDistance;

        while (!isCloseEnough(current, to) && iterations < MAX_PATH_ITERATIONS) {
            iterations++;

            double targetAngle = calculateAngle(current, to);
            Position nextDirect = moveInDirection(current, targetAngle);

            if (!pathSegmentCrossesRestriction(current, nextDirect)) {
                current = nextDirect;
                path.add(new LngLat(current.getLng(), current.getLat()));
                stuckCounter = 0;
                lastDistance = dist(current, to);

                if (lastDistance < bestDistance) {
                    bestDistance = lastDistance;
                }
            } else {
                Position nextPos = findAlternativeMoveRelaxed(current, to, targetAngle, stuckCounter);

                if (nextPos == null) {
                    logger.warn("No alternative move in relaxed mode at iteration {} (stuck={}, dist={})",
                            iterations, stuckCounter, dist(current, to));

                    if (dist(current, to) < CLOSE_THRESHOLD * 2) {
                        logger.info("Relaxed: Close enough to target, accepting position");
                        break;
                    }

                    return null;
                }

                current = nextPos;
                path.add(new LngLat(current.getLng(), current.getLat()));

                double currentDistance = dist(current, to);
                if (currentDistance >= lastDistance - EPS) {
                    stuckCounter++;
                } else {
                    stuckCounter = Math.max(0, stuckCounter - 1);
                    if (currentDistance < bestDistance) {
                        bestDistance = currentDistance;
                    }
                }
                lastDistance = currentDistance;

                if (stuckCounter > 100) {
                    logger.warn("Stuck for {} iterations (dist={}, best={}), abandoning",
                            stuckCounter, currentDistance, bestDistance);
                    return null;
                }
            }

            if (iterations % 1000 == 0) {
                logger.debug("Relaxed iteration {}, distance: {}, best: {}, stuck: {}",
                        iterations, dist(current, to), bestDistance, stuckCounter);
            }
        }

        if (iterations >= MAX_PATH_ITERATIONS) {
            logger.warn("Relaxed pathfinding exceeded max iterations");
            return null;
        }

        logger.info("Relaxed pathfinding SUCCEEDED with {} steps, final distance: {}",
                path.size(), dist(current, to));

        return path;
    }

    private Position findAlternativeMove(Position current, Position target, double targetAngle) {
        double[] offsets = {
                -ANGLE_INCREMENT, ANGLE_INCREMENT,
                -2*ANGLE_INCREMENT, 2*ANGLE_INCREMENT,
                -3*ANGLE_INCREMENT, 3*ANGLE_INCREMENT,
                -4*ANGLE_INCREMENT, 4*ANGLE_INCREMENT,
                -5*ANGLE_INCREMENT, 5*ANGLE_INCREMENT
        };

        for (double offset : offsets) {
            double testAngle = normalizeAngle(targetAngle + offset);
            Position testPos = moveInDirection(current, testAngle);

            if (!pathSegmentCrossesRestriction(current, testPos)) {
                double distBefore = dist(current, target);
                double distAfter = dist(testPos, target);

                if (distAfter <= distBefore * 1.8) {
                    return testPos;
                }
            }
        }

        Position bestPos = null;
        double bestDist = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 16; i++) {
            double testAngle = i * ANGLE_INCREMENT;
            Position testPos = moveInDirection(current, testAngle);

            if (!pathSegmentCrossesRestriction(current, testPos)) {
                double distToTarget = dist(testPos, target);
                if (distToTarget < bestDist) {
                    bestDist = distToTarget;
                    bestPos = testPos;
                }
            }
        }

        return bestPos;
    }

    private Position findAlternativeMoveRelaxed(Position current, Position target,
                                                double targetAngle, int stuckCounter) {
        double[] offsets = {
                -ANGLE_INCREMENT, ANGLE_INCREMENT,
                -2*ANGLE_INCREMENT, 2*ANGLE_INCREMENT,
                -3*ANGLE_INCREMENT, 3*ANGLE_INCREMENT,
                -4*ANGLE_INCREMENT, 4*ANGLE_INCREMENT,
                -5*ANGLE_INCREMENT, 5*ANGLE_INCREMENT,
                -6*ANGLE_INCREMENT, 6*ANGLE_INCREMENT,
                -7*ANGLE_INCREMENT, 7*ANGLE_INCREMENT,
                -8*ANGLE_INCREMENT, 8*ANGLE_INCREMENT
        };

        double tolerance = stuckCounter > 50 ? 4.0 : (stuckCounter > 30 ? 3.0 : 2.0);

        for (double offset : offsets) {
            double testAngle = normalizeAngle(targetAngle + offset);
            Position testPos = moveInDirection(current, testAngle);

            if (!pathSegmentCrossesRestriction(current, testPos)) {
                double distBefore = dist(current, target);
                double distAfter = dist(testPos, target);

                if (distAfter <= distBefore * tolerance) {
                    return testPos;
                }
            }
        }

        List<Position> validMoves = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            double testAngle = i * ANGLE_INCREMENT;
            Position testPos = moveInDirection(current, testAngle);

            if (!pathSegmentCrossesRestriction(current, testPos)) {
                validMoves.add(testPos);
            }
        }

        if (validMoves.isEmpty()) {
            return null;
        }

        Position best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Position pos : validMoves) {
            double d = dist(pos, target);
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }

        return best;
    }

    private void diagnoseDeliveryFailure(MedDispatchRec dispatch, Position currentPos) {
        logger.info("=== DIAGNOSING DELIVERY FAILURE FOR ID {} ===", dispatch.getId());

        Position target = dispatch.getDelivery();
        logger.info("Target position: {}", target);
        logger.info("Current position: {}", currentPos);
        logger.info("Distance to target: {}", dist(currentPos, target));

        boolean targetInRestricted = restrictedAreaService.isInRestrictedArea(target);
        logger.info("Target in restricted area: {}", targetInRestricted);

        if (targetInRestricted) {
            logger.error("⚠️ DELIVERY POINT IS INSIDE RESTRICTED AREA - CANNOT BE COMPLETED");
            String areaName = restrictedAreaService.getRestrictedAreaNameForPath(target, target);
            logger.error("Restricted area: {}", areaName);
        }

        boolean pathBlocked = restrictedAreaService.pathCrossesRestrictedArea(currentPos, target);
        logger.info("Direct path blocked: {}", pathBlocked);

        if (pathBlocked) {
            String areaName = restrictedAreaService.getRestrictedAreaNameForPath(currentPos, target);
            logger.info("Blocked by restricted area: {}", areaName);
        }

        double targetAngle = calculateAngle(currentPos, target);
        logger.info("Target angle: {} degrees", targetAngle);

        for (int i = 1; i <= 5; i++) {
            Position testPos = currentPos;
            for (int j = 0; j < i; j++) {
                testPos = moveInDirection(testPos, targetAngle);
            }
            boolean stepBlocked = restrictedAreaService.pathCrossesRestrictedArea(currentPos, testPos);
            logger.info("After {} steps towards target: blocked={}, pos={}, dist to target={}",
                    i, stepBlocked, testPos, dist(testPos, target));
        }

        logger.info("=== END DIAGNOSIS ===");
    }

    private boolean pathSegmentCrossesRestriction(Position from, Position to) {
        return restrictedAreaService.pathCrossesRestrictedArea(from, to);
    }

    private Position moveInDirection(Position from, double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        double newLng = from.getLng() + STEP * Math.cos(angleRad);
        double newLat = from.getLat() + STEP * Math.sin(angleRad);
        return new Position(newLng, newLat);
    }

    private double calculateAngle(Position from, Position to) {
        double dx = to.getLng() - from.getLng();
        double dy = to.getLat() - from.getLat();
        double angleRad = Math.atan2(dy, dx);
        double angleDeg = Math.toDegrees(angleRad);
        return normalizeAngle(angleDeg);
    }

    private double normalizeAngle(double angle) {
        while (angle < 0) angle += 360;
        while (angle >= 360) angle -= 360;
        return Math.round(angle / ANGLE_INCREMENT) * ANGLE_INCREMENT;
    }

    private boolean isCloseEnough(Position p1, Position p2) {
        return dist(p1, p2) < CLOSE_THRESHOLD;
    }

    private int estimateStepsBack(Position from, Position to) {
        double d = dist(from, to);
        if (Double.isInfinite(d)) return Integer.MAX_VALUE;
        return (int) Math.ceil(d / STEP);
    }

    private Position safeGetPosition(ServicePoint sp) {
        if (sp == null || sp.getLocation() == null) return null;
        return new Position(sp.getLocation().getLng(), sp.getLocation().getLat());
    }

    private boolean fitsRequirements(Requirements req, Capability cap) {
        if (req == null || cap == null) return false;
        if (cap.getCapacity() + EPS < req.getCapacity()) return false;
        if (req.isCooling() && !cap.isCooling()) return false;
        if (req.isHeating() && !cap.isHeating()) return false;
        return true;
    }

    private MedDispatchRec nearest(Position from, List<MedDispatchRec> list) {
        if (from == null || list == null || list.isEmpty()) return null;
        MedDispatchRec best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (MedDispatchRec d : list) {
            if (d == null || d.getDelivery() == null) continue;
            double dist = dist(from, d.getDelivery());
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    private double dist(Position a, Position b) {
        if (a == null || b == null) return Double.POSITIVE_INFINITY;
        double dx = a.getLng() - b.getLng();
        double dy = a.getLat() - b.getLat();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double computeFlightCost(Capability cap, int usedMoves) {
        if (cap == null) return 0.0;
        return cap.getCostInitial() + cap.getCostFinal() + usedMoves * cap.getCostPerMove();
    }

    private int safeGetMaxMoves(Capability cap) {
        return cap == null ? 0 : cap.getMaxMoves();
    }

    private double safeGetCapabilityCapacity(Drone d) {
        if (d == null || d.getCapability() == null) return 0.0;
        return d.getCapability().getCapacity();
    }

    private boolean isInNoFly(Position p) {
        if (p == null) return false;
        return restrictedAreaService.isInRestrictedArea(p);
    }
}