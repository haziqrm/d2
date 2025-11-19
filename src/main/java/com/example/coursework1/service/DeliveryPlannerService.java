package com.example.coursework1.service;

import com.example.coursework1.dto.*;
import com.example.coursework1.model.Position;
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
    private static final double EPS = 1e-12;
    private static final int MAX_PATH_ITERATIONS = 20000;

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

        List<MedDispatchRec> pending = new ArrayList<>(dispatches.stream()
                .filter(d -> d != null && d.getId() != null &&
                        d.getRequirements() != null && d.getDelivery() != null)
                .toList());

        List<Drone> drones = droneService.fetchAllDrones();
        List<ServicePoint> servicePoints = servicePointService.fetchAllServicePoints();

        Position defaultBase = servicePoints.isEmpty() ?
                new Position(0.0, 0.0) : safeGetPosition(servicePoints.get(0));

        double totalCost = 0.0;
        int totalMoves = 0;
        List<DronePathResult> dronePaths = new ArrayList<>();

        // REMOVED: Upfront availability filtering that used AND logic
        // Instead, we sort by capacity and check availability per-dispatch
        drones = drones.stream()
                .sorted(Comparator.comparingDouble((Drone dr) -> -safeGetCapabilityCapacity(dr)))
                .toList();

        logger.info("Processing with {} total drones", drones.size());

        for (Drone drone : drones) {
            if (pending.isEmpty()) break;

            Capability cap = drone.getCapability();
            if (cap == null) continue;

            Position base = defaultBase;
            List<DeliveryResult> allDeliveries = new ArrayList<>();
            int totalDroneMoves = 0;
            double totalDroneCost = 0.0;
            int flightNumber = 0;

            // Allow multiple flights per drone
            while (!pending.isEmpty()) {
                flightNumber++;
                logger.info("Drone {} starting flight #{}", drone.getId(), flightNumber);

                Position current = base;
                int movesLeft = safeGetMaxMoves(cap);
                int usedMovesThisFlight = 0;
                double capacityUsed = 0.0;

                List<DeliveryResult> flightDeliveries = new ArrayList<>();

                // NEW: Filter candidates for THIS specific drone, checking availability per-dispatch
                List<MedDispatchRec> candidates = pending.stream()
                        .filter(m -> {
                            if (!fitsRequirements(m.getRequirements(), cap)) {
                                return false;
                            }
                            // Check if THIS drone is available for THIS specific dispatch
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

                    if (isInNoFly(dest)) {
                        logger.warn("Delivery {} destination is in restricted area - SKIPPING",
                                next.getId());
                        candidates.remove(next);
                        pending.remove(next);
                        continue;
                    }

                    // Check capacity constraint
                    if (capacityUsed + next.getRequirements().getCapacity() > cap.getCapacity() + EPS) {
                        logger.debug("Adding delivery {} would exceed capacity ({} + {} > {})",
                                next.getId(), capacityUsed, next.getRequirements().getCapacity(), cap.getCapacity());
                        candidates.remove(next);
                        continue;
                    }

                    List<LngLat> pathToDest = buildPathAvoidingRestrictions(current, dest);

                    if (pathToDest == null || pathToDest.isEmpty()) {
                        logger.warn("Failed to find path for delivery {}, trying relaxed", next.getId());
                        pathToDest = buildPathWithRelaxedConstraints(current, dest);
                    }

                    if (pathToDest == null || pathToDest.isEmpty()) {
                        logger.error("All pathfinding failed for delivery {} - SKIPPING", next.getId());
                        candidates.remove(next);
                        pending.remove(next);
                        continue;
                    }

                    int toDest = pathToDest.size() - 1;
                    int back = estimateStepsBack(dest, base);

                    if (toDest + back > movesLeft) {
                        logger.debug("Not enough moves for delivery {} ({} + {} > {})",
                                next.getId(), toDest, back, movesLeft);
                        candidates.remove(next);
                        continue;
                    }

                    // Add hover at EXACT delivery location (2 identical points)
                    pathToDest.add(new LngLat(dest.getLng(), dest.getLat()));

                    movesLeft -= toDest;
                    usedMovesThisFlight += toDest;
                    capacityUsed += next.getRequirements().getCapacity();

                    flightDeliveries.add(new DeliveryResult(next.getId(), pathToDest));
                    pending.remove(next);
                    candidates.remove(next);

                    current = dest;
                    logger.info("Delivery {} added ({} moves, {} moves left, {}/{} capacity used)",
                            next.getId(), toDest, movesLeft, capacityUsed, cap.getCapacity());
                }

                if (flightDeliveries.isEmpty()) {
                    logger.debug("No deliveries completed in flight #{}, stopping", flightNumber);
                    break;
                }

                // Return to base
                List<LngLat> returnPath = buildPathAvoidingRestrictions(current, base);
                if (returnPath == null) {
                    returnPath = buildPathWithRelaxedConstraints(current, base);
                }

                int stepsBack = returnPath != null ? returnPath.size() - 1 : estimateStepsBack(current, base);

                if (stepsBack > movesLeft || returnPath == null) {
                    logger.warn("Not enough moves to return - removing deliveries from this flight");
                    // Remove deliveries from this failed flight back to pending
                    for (DeliveryResult dr : flightDeliveries) {
                        pending.add(dispatches.stream()
                                .filter(d -> d.getId().equals(dr.getDeliveryId()))
                                .findFirst()
                                .orElse(null));
                    }
                    break;
                }

                // Append return path to last delivery
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

                // Reset for next flight
                current = base;
            }

            if (!allDeliveries.isEmpty()) {
                totalCost += totalDroneCost;
                totalMoves += totalDroneMoves;
                dronePaths.add(new DronePathResult(drone.getId(), allDeliveries));

                logger.info("Drone {} completed: {} deliveries, {} moves, ${} cost",
                        drone.getId(), allDeliveries.size(), totalDroneMoves, totalDroneCost);
            }
        }

        logger.info("=== Completed: {} drones, {} moves, ${} cost ===",
                dronePaths.size(), totalMoves, totalCost);

        return new CalcDeliveryResult(totalCost, totalMoves, dronePaths);
    }

    private List<LngLat> buildPathAvoidingRestrictions(Position from, Position to) {
        if (from == null || to == null) {
            logger.error("Null position in buildPath: from={}, to={}", from, to);
            return null;
        }

        logger.debug("Building path from ({}, {}) to ({}, {})",
                from.getLng(), from.getLat(), to.getLng(), to.getLat());

        List<LngLat> path = new ArrayList<>();
        path.add(new LngLat(from.getLng(), from.getLat()));

        Position current = new Position(from.getLng(), from.getLat());
        int iterations = 0;

        while (!isCloseEnough(current, to) && iterations < MAX_PATH_ITERATIONS) {
            iterations++;

            double targetAngle = calculateAngle(current, to);
            Position nextDirect = moveInDirection(current, targetAngle);

            if (!pathSegmentCrossesRestriction(current, nextDirect)) {
                current = nextDirect;
                path.add(new LngLat(current.getLng(), current.getLat()));
            } else {
                Position nextPos = findAlternativeMove(current, to, targetAngle);

                if (nextPos == null) {
                    logger.warn("No alternative move at iteration {}", iterations);
                    return null;
                }

                current = nextPos;
                path.add(new LngLat(current.getLng(), current.getLat()));
            }

            if (iterations % 1000 == 0) {
                logger.debug("Pathfinding iteration {}, distance remaining: {}",
                        iterations, dist(current, to));
            }
        }

        // ALWAYS add exact destination coordinates as final point
        LngLat lastPoint = path.get(path.size() - 1);
        if (Math.abs(lastPoint.getLng() - to.getLng()) > EPS ||
                Math.abs(lastPoint.getLat() - to.getLat()) > EPS) {
            path.add(new LngLat(to.getLng(), to.getLat()));
        }

        logger.debug("Path built with {} steps, ending at exact destination ({}, {})",
                path.size(), to.getLng(), to.getLat());
        return path;
    }

    private List<LngLat> buildPathWithRelaxedConstraints(Position from, Position to) {
        logger.debug("Trying relaxed pathfinding");

        List<LngLat> path = new ArrayList<>();
        path.add(new LngLat(from.getLng(), from.getLat()));

        Position current = new Position(from.getLng(), from.getLat());
        int iterations = 0;
        int stuckCounter = 0;
        double lastDistance = dist(current, to);

        while (!isCloseEnough(current, to) && iterations < MAX_PATH_ITERATIONS) {
            iterations++;

            double targetAngle = calculateAngle(current, to);
            Position nextDirect = moveInDirection(current, targetAngle);

            if (!pathSegmentCrossesRestriction(current, nextDirect)) {
                current = nextDirect;
                path.add(new LngLat(current.getLng(), current.getLat()));
                stuckCounter = 0;
                lastDistance = dist(current, to);
            } else {
                Position nextPos = findAlternativeMoveRelaxed(current, to, targetAngle, stuckCounter);

                if (nextPos == null) {
                    logger.warn("No alternative move in relaxed mode at iteration {}", iterations);
                    return null;
                }

                current = nextPos;
                path.add(new LngLat(current.getLng(), current.getLat()));

                double currentDistance = dist(current, to);
                if (currentDistance >= lastDistance) {
                    stuckCounter++;
                } else {
                    stuckCounter = Math.max(0, stuckCounter - 1);
                }
                lastDistance = currentDistance;

                if (stuckCounter > 50) {
                    logger.warn("Stuck for {} iterations, abandoning", stuckCounter);
                    return null;
                }
            }
        }

        // ALWAYS add exact destination coordinates as final point
        LngLat lastPoint = path.get(path.size() - 1);
        if (Math.abs(lastPoint.getLng() - to.getLng()) > EPS ||
                Math.abs(lastPoint.getLat() - to.getLat()) > EPS) {
            path.add(new LngLat(to.getLng(), to.getLat()));
        }

        logger.debug("Relaxed pathfinding succeeded with {} steps, ending at exact destination ({}, {})",
                path.size(), to.getLng(), to.getLat());
        return path;
    }

    private Position findAlternativeMove(Position current, Position target, double targetAngle) {
        double[] offsets = {-ANGLE_INCREMENT, ANGLE_INCREMENT,
                -2*ANGLE_INCREMENT, 2*ANGLE_INCREMENT,
                -3*ANGLE_INCREMENT, 3*ANGLE_INCREMENT,
                -4*ANGLE_INCREMENT, 4*ANGLE_INCREMENT};

        for (double offset : offsets) {
            double testAngle = normalizeAngle(targetAngle + offset);
            Position testPos = moveInDirection(current, testAngle);

            if (!pathSegmentCrossesRestriction(current, testPos)) {
                double distBefore = dist(current, target);
                double distAfter = dist(testPos, target);

                if (distAfter <= distBefore * 1.5) {
                    return testPos;
                }
            }
        }

        for (int i = 0; i < 16; i++) {
            double testAngle = i * ANGLE_INCREMENT;
            Position testPos = moveInDirection(current, testAngle);

            if (!pathSegmentCrossesRestriction(current, testPos)) {
                return testPos;
            }
        }

        return null;
    }

    private Position findAlternativeMoveRelaxed(Position current, Position target,
                                                double targetAngle, int stuckCounter) {
        double[] offsets = {
                -ANGLE_INCREMENT, ANGLE_INCREMENT,
                -2*ANGLE_INCREMENT, 2*ANGLE_INCREMENT,
                -3*ANGLE_INCREMENT, 3*ANGLE_INCREMENT,
                -4*ANGLE_INCREMENT, 4*ANGLE_INCREMENT,
                -5*ANGLE_INCREMENT, 5*ANGLE_INCREMENT,
                -6*ANGLE_INCREMENT, 6*ANGLE_INCREMENT
        };

        for (double offset : offsets) {
            double testAngle = normalizeAngle(targetAngle + offset);
            Position testPos = moveInDirection(current, testAngle);

            if (!pathSegmentCrossesRestriction(current, testPos)) {
                double distBefore = dist(current, target);
                double distAfter = dist(testPos, target);

                double tolerance = stuckCounter > 20 ? 2.0 : 1.5;
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
        return dist(p1, p2) < STEP * 1.5;
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