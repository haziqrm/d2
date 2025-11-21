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
    private static final double CLOSE_THRESHOLD = 0.00015;
    private static final double EPS = 1e-12;
    private static final int MAX_PATH_ITERATIONS = 50000;

    // All 16 compass directions
    private static final double[] ANGLES = {
            0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
            180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5
    };

    public DeliveryPlannerService(DroneService droneService,
                                  ServicePointService servicePointService,
                                  RestrictedAreaService restrictedAreaService,
                                  DroneAvailabilityService droneAvailabilityService) {
        this.droneService = droneService;
        this.servicePointService = servicePointService;
        this.restrictedAreaService = restrictedAreaService;
        this.droneAvailabilityService = droneAvailabilityService;
    }

    /**
     * A* Node class for pathfinding
     */
    private static class Node implements Comparable<Node> {
        Position position;
        Node parent;
        double gCost; // Actual cost from start
        double hCost; // Heuristic cost to goal
        double fCost; // Total cost (g + h)

        Node(Position position) {
            this.position = position;
            this.gCost = Double.POSITIVE_INFINITY;
            this.hCost = 0;
            this.fCost = Double.POSITIVE_INFINITY;
        }

        @Override
        public int compareTo(Node other) {
            int fCompare = Double.compare(this.fCost, other.fCost);
            if (fCompare != 0) return fCompare;
            // Tie-breaker: prefer nodes closer to goal
            return Double.compare(this.hCost, other.hCost);
        }
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

            List<LngLat> pathToDest = findPathAStar(current, dest);

            if (pathToDest == null || pathToDest.isEmpty()) {
                logger.error("A* pathfinding failed for delivery {} - cannot complete single-drone delivery",
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

            logger.debug("Added delivery {} ({} steps, distance to target: {})",
                    dispatch.getId(), steps, closestDist);
        }

        List<LngLat> returnPath = findPathAStar(current, base);

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

                    List<LngLat> pathToDest = findPathAStar(current, dest);

                    if (pathToDest == null || pathToDest.isEmpty()) {
                        logger.error("A* pathfinding failed for delivery {} - SKIPPING", next.getId());
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

                    logger.info("Delivery {} added ({} moves, {} moves left, {}/{} capacity used, distance to target: {})",
                            next.getId(), toDest, movesLeft, capacityUsed, cap.getCapacity(), closestDist);
                }

                if (flightDeliveries.isEmpty()) {
                    logger.debug("No deliveries completed in flight #{}, stopping", flightNumber);
                    break;
                }

                List<LngLat> returnPath = findPathAStar(current, base);

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

    /**
     * A* pathfinding algorithm - finds optimal path avoiding restricted areas
     */
    private List<LngLat> findPathAStar(Position start, Position end) {
        logger.debug("A* starting path from ({}, {}) to ({}, {})",
                start.getLng(), start.getLat(), end.getLng(), end.getLat());

        Node startNode = new Node(start);
        Node endNode = new Node(end);

        // Use Position as key, not Node - this fixes the HashMap contract issue
        Map<Position, Node> openSetMap = new HashMap<>();
        PriorityQueue<Node> openSetQueue = new PriorityQueue<>();
        HashSet<Position> closedSet = new HashSet<>();

        startNode.gCost = 0;
        startNode.hCost = dist(start, end);
        startNode.fCost = startNode.hCost;

        openSetQueue.add(startNode);
        openSetMap.put(startNode.position, startNode);

        int iterations = 0;

        while (!openSetQueue.isEmpty() && iterations < MAX_PATH_ITERATIONS) {
            iterations++;

            if (iterations % 10000 == 0) {
                logger.debug("A* iteration {}, open set size: {}, closed set size: {}",
                        iterations, openSetQueue.size(), closedSet.size());
            }

            Node currentNode = openSetQueue.poll();
            openSetMap.remove(currentNode.position);

            if (checkPointsClose(currentNode.position, endNode.position)) {
                logger.debug("A* found path in {} iterations", iterations);
                return reconstructPath(currentNode);
            }

            closedSet.add(currentNode.position);

            for (double angle : ANGLES) {
                Position neighborPos = calculateNextPosition(currentNode.position, angle);

                if (closedSet.contains(neighborPos)) {
                    continue;
                }

                // Check both the point and the line segment
                if (isInvalidMove(currentNode.position, neighborPos)) {
                    continue;
                }

                double tentativeGCost = currentNode.gCost + STEP;
                Node neighborNode = openSetMap.get(neighborPos);

                if (neighborNode == null) {
                    neighborNode = new Node(neighborPos);
                    neighborNode.parent = currentNode;
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.hCost = dist(neighborPos, end);
                    neighborNode.fCost = neighborNode.gCost + neighborNode.hCost;
                    openSetQueue.add(neighborNode);
                    openSetMap.put(neighborPos, neighborNode);
                } else if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.parent = currentNode;
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.fCost = neighborNode.gCost + neighborNode.hCost;
                    openSetQueue.remove(neighborNode);
                    openSetQueue.add(neighborNode);
                }
            }
        }

        if (iterations >= MAX_PATH_ITERATIONS) {
            logger.warn("A* exceeded max iterations ({}) from ({}, {}) to ({}, {})",
                    MAX_PATH_ITERATIONS, start.getLng(), start.getLat(), end.getLng(), end.getLat());
        } else {
            logger.warn("A* could not find path from ({}, {}) to ({}, {}) (exhausted search space after {} iterations)",
                    start.getLng(), start.getLat(), end.getLng(), end.getLat(), iterations);
        }

        return Collections.emptyList();
    }

    /**
     * Check if a move from 'from' to 'to' is invalid.
     * A move is invalid if:
     * 1. The destination point is inside a no-fly zone, OR
     * 2. The line segment from 'from' to 'to' crosses a no-fly zone boundary
     */
    private boolean isInvalidMove(Position from, Position to) {
        // Check if the destination point is inside a restricted zone
        if (restrictedAreaService.isInRestrictedArea(to)) {
            return true;
        }

        // Check if the line segment crosses a restricted zone boundary
        return restrictedAreaService.pathCrossesRestrictedArea(from, to);
    }

    /**
     * Calculate next position based on current position and angle
     */
    private Position calculateNextPosition(Position current, double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        double newLng = current.getLng() + STEP * Math.cos(angleRad);
        double newLat = current.getLat() + STEP * Math.sin(angleRad);
        return new Position(newLng, newLat);
    }

    /**
     * Check if two points are close enough (within CLOSE_THRESHOLD)
     */
    private boolean checkPointsClose(Position p1, Position p2) {
        return dist(p1, p2) < CLOSE_THRESHOLD;
    }

    /**
     * Reconstruct path from goal node by following parent pointers
     */
    private List<LngLat> reconstructPath(Node endNode) {
        List<LngLat> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(new LngLat(current.position.getLng(), current.position.getLat()));
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
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
}