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

    private static final double STEP = 0.00015;
    private static final double ANGLE_INCREMENT = 22.5;
    private static final double EPS = 1e-12;
    private static final int MAX_PATH_ITERATIONS = 10000;

    public DeliveryPlannerService(DroneService droneService,
                                  ServicePointService servicePointService,
                                  RestrictedAreaService restrictedAreaService) {
        this.droneService = droneService;
        this.servicePointService = servicePointService;
        this.restrictedAreaService = restrictedAreaService;
    }

    public CalcDeliveryResult calcDeliveryPath(List<MedDispatchRec> dispatches) {
        logger.info("Calculating delivery path for {} dispatches",
                dispatches != null ? dispatches.size() : 0);

        List<MedDispatchRec> originalList = dispatches == null ? List.of() : new ArrayList<>(dispatches);

        Map<Integer, MedDispatchRec> originalById = originalList.stream()
                .filter(Objects::nonNull)
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(MedDispatchRec::getId, d -> d, (a, b) -> a));

        List<MedDispatchRec> pending = originalList.stream()
                .filter(d -> d != null && d.getId() != null &&
                        d.getRequirements() != null && d.getDelivery() != null)
                .collect(Collectors.toCollection(ArrayList::new));

        List<Drone> drones = Optional.ofNullable(droneService.fetchAllDrones()).orElse(List.of());
        List<ServicePoint> servicePoints = Optional.ofNullable(servicePointService.fetchAllServicePoints()).orElse(List.of());

        Position defaultBase = servicePoints.isEmpty() ?
                new Position(0.0, 0.0) : safeGetPosition(servicePoints.get(0));

        double totalCost = 0.0;
        int totalMoves = 0;
        List<DronePathResult> dronePaths = new ArrayList<>();

        Map<String, Position> droneHome = new HashMap<>();
        for (Drone d : drones) {
            droneHome.put(d.getId(), pickHomeForDrone(servicePoints, defaultBase));
        }

        drones.sort(Comparator.comparingDouble((Drone dr) -> -safeGetCapabilityCapacity(dr)));

        for (Drone drone : drones) {
            Capability cap = drone.getCapability();
            if (cap == null) continue;

            Position base = droneHome.getOrDefault(drone.getId(), defaultBase);
            if (base == null) base = defaultBase;
            Position current = base;

            int movesLeft = safeGetMaxMoves(cap);
            int usedMovesThisFlight = 0;

            List<DeliveryResult> assigned = new ArrayList<>();

            List<MedDispatchRec> candidates = pending.stream()
                    .filter(m -> fitsRequirements(m.getRequirements(), cap))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (candidates.isEmpty()) continue;

            while (!candidates.isEmpty()) {

                MedDispatchRec next = nearest(current, candidates);
                if (next == null) break;

                Position dest = next.getDelivery();

                if (isInNoFly(dest)) {
                    candidates.remove(next);
                    continue;
                }

                List<LngLat> pathToDest = buildPathAvoidingRestrictions(current, dest);

                if (pathToDest == null || pathToDest.isEmpty()) {
                    candidates.remove(next);
                    continue;
                }

                int toDest = pathToDest.size() - 1;
                int back = estimateStepsBack(dest, base);

                if (toDest + back > movesLeft) {
                    candidates.remove(next);
                    continue;
                }

                // Add hover indicator
                if (!pathToDest.isEmpty()) {
                    LngLat last = pathToDest.get(pathToDest.size() - 1);
                    pathToDest.add(new LngLat(last.getLng(), last.getLat()));
                } else {
                    pathToDest.add(new LngLat(dest.getLng(), dest.getLat()));
                    pathToDest.add(new LngLat(dest.getLng(), dest.getLat()));
                }

                movesLeft -= toDest;
                usedMovesThisFlight += toDest;
                totalMoves += toDest;

                assigned.add(new DeliveryResult(next.getId(), pathToDest));
                pending.remove(next);
                candidates.remove(next);

                current = dest;
            }

            List<LngLat> returnPath = buildPathAvoidingRestrictions(current, base);
            int stepsBack = returnPath != null ? returnPath.size() - 1 : estimateStepsBack(current, base);

            if (stepsBack > movesLeft || returnPath == null) {
                while (!assigned.isEmpty() && (stepsBack > movesLeft || returnPath == null)) {
                    DeliveryResult last = assigned.remove(assigned.size() - 1);
                    MedDispatchRec original = originalById.get(last.getDeliveryId());
                    if (original != null) {
                        pending.add(original);
                    }

                    current = assigned.isEmpty() ? base : lastDeliveryPosition(assigned.get(assigned.size() - 1));
                    returnPath = buildPathAvoidingRestrictions(current, base);
                    stepsBack = returnPath != null ? returnPath.size() - 1 : estimateStepsBack(current, base);
                }

                if (assigned.isEmpty()) {
                    continue;
                }
            }

            if (!assigned.isEmpty() && returnPath != null) {
                DeliveryResult lastDelivery = assigned.get(assigned.size() - 1);
                List<LngLat> lastPath = new ArrayList<>(lastDelivery.getFlightPath());

                for (int i = 1; i < returnPath.size(); i++) {
                    lastPath.add(returnPath.get(i));
                }

                lastDelivery.setFlightPath(lastPath);
            }

            movesLeft -= stepsBack;
            usedMovesThisFlight += stepsBack;
            totalMoves += stepsBack;

            if (!assigned.isEmpty()) {
                double flightCost = computeFlightCost(cap, usedMovesThisFlight);
                totalCost += flightCost;
                dronePaths.add(new DronePathResult(drone.getId(), assigned));
            }
        }

        logger.info("Delivery planning complete: {} drones used, {} total moves, ${} total cost",
                dronePaths.size(), totalMoves, totalCost);

        return new CalcDeliveryResult(totalCost, totalMoves, dronePaths);
    }

    private List<LngLat> buildPathAvoidingRestrictions(Position from, Position to) {
        if (from == null || to == null) return null;

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
                    return null;
                }

                current = nextPos;
                path.add(new LngLat(current.getLng(), current.getLat()));
            }
        }

        if (!isCloseEnough(current, to)) {
            path.add(new LngLat(to.getLng(), to.getLat()));
        }

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

    private Position pickHomeForDrone(List<ServicePoint> sps, Position fallback) {
        if (sps == null || sps.isEmpty()) return fallback;
        ServicePoint sp = sps.get(0);
        Position p = safeGetPosition(sp);
        return p == null ? fallback : p;
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

    private Position lastDeliveryPosition(DeliveryResult r) {
        if (r == null || r.getFlightPath() == null || r.getFlightPath().isEmpty()) return null;

        List<LngLat> path = r.getFlightPath();

        for (int i = 0; i < path.size() - 1; i++) {
            LngLat curr = path.get(i);
            LngLat next = path.get(i + 1);
            if (Math.abs(curr.getLng() - next.getLng()) < EPS &&
                    Math.abs(curr.getLat() - next.getLat()) < EPS) {
                return new Position(curr.getLng(), curr.getLat());
            }
        }

        LngLat last = path.get(path.size() - 1);
        return new Position(last.getLng(), last.getLat());
    }

    private double computeFlightCost(Capability cap, int usedMoves) {
        if (cap == null) return 0.0;
        double initial = cap.getCostInitial();
        double fin = cap.getCostFinal();
        double perMove = cap.getCostPerMove();
        return initial + fin + usedMoves * perMove;
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