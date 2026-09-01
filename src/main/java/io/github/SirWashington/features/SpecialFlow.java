package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class SpecialFlow {

    private static final int MAX_WATER_LEVEL = 8;

    public static boolean pushWater(ServerLevel level, List<BlockPos> waterPositions, Direction pistonDirection) {
        return pushWater(level, waterPositions, pistonDirection, Set.copyOf(waterPositions));
    }

    public static boolean pushWater(ServerLevel level, List<BlockPos> waterPositions, Direction pistonDirection,
                                    Set<BlockPos> pistonOccupiedPositions) {
        Map<BlockPos, Vec3> flowTransfers = new HashMap<>();
        Map<BlockPos, Integer> plannedLevels = planPush(
                waterPositions,
                pistonDirection,
                pos -> FiniteWaterPhysics.getWaterLevel(level, pos),
                WaterPhysicsConfig.pistonPressureMaxDepth(),
                WaterPhysicsConfig.pistonPressureMaxVisitedWaterCells(),
                pos -> level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4),
                pistonOccupiedPositions,
                flowTransfers
        );
        if (plannedLevels == null) {
            return false;
        }

        applyPlan(level, plannedLevels);
        flowTransfers.forEach((pos, units) -> FiniteWaterPhysics.applyCurrent(level, pos, units));
        return true;
    }

    static Map<BlockPos, Integer> planPush(List<BlockPos> waterPositions, Direction pistonDirection,
                                            ToIntFunction<BlockPos> levelAt) {
        return planPush(
                waterPositions,
                pistonDirection,
                levelAt,
                WaterPhysicsConfig.pistonPressureMaxDepth(),
                WaterPhysicsConfig.pistonPressureMaxVisitedWaterCells(),
                pos -> true,
                Set.of()
        );
    }

    static Map<BlockPos, Integer> planPush(List<BlockPos> waterPositions, Direction pistonDirection,
                                            ToIntFunction<BlockPos> levelAt, int maxDepth,
                                            int maxVisitedWaterCells, Predicate<BlockPos> isLoaded) {
        return planPush(waterPositions, pistonDirection, levelAt, maxDepth,
                maxVisitedWaterCells, isLoaded, Set.of());
    }

    static Map<BlockPos, Integer> planPush(List<BlockPos> waterPositions, Direction pistonDirection,
                                            ToIntFunction<BlockPos> levelAt, int maxDepth,
                                            int maxVisitedWaterCells, Predicate<BlockPos> isLoaded,
                                            Set<BlockPos> pistonOccupiedPositions) {
        return planPush(waterPositions, pistonDirection, levelAt, maxDepth, maxVisitedWaterCells,
                isLoaded, pistonOccupiedPositions, new HashMap<>());
    }

    static Map<BlockPos, Integer> planPush(List<BlockPos> waterPositions, Direction pistonDirection,
                                            ToIntFunction<BlockPos> levelAt, int maxDepth,
                                            int maxVisitedWaterCells, Predicate<BlockPos> isLoaded,
                                            Set<BlockPos> pistonOccupiedPositions,
                                            Map<BlockPos, Vec3> flowTransfers) {
        if (maxDepth <= 0 || maxVisitedWaterCells <= 0) {
            throw new IllegalArgumentException("Piston-pressure limits must be positive");
        }

        flowTransfers.clear();
        Map<BlockPos, Vec3> acceptedFlowTransfers = new HashMap<>();
        Map<BlockPos, Integer> volumes = new LinkedHashMap<>();
        Map<BlockPos, Integer> plannedLevels = new HashMap<>();
        for (BlockPos pos : waterPositions) {
            int volume = levelAt.applyAsInt(pos);
            if (volume > 0) {
                volumes.put(pos, volume);
                plannedLevels.put(pos, 0);
            }
        }

        Set<BlockPos> unassignedStarts = new LinkedHashSet<>(volumes.keySet());
        Set<BlockPos> visitedWaterCells = new HashSet<>();
        for (BlockPos start : volumes.keySet()) {
            if (!unassignedStarts.contains(start)) {
                continue;
            }

            SearchResult search = findComponent(
                    start, volumes, pistonDirection, levelAt, maxDepth,
                    maxVisitedWaterCells, isLoaded, pistonOccupiedPositions, visitedWaterCells
            );
            if (search == null) {
                return null;
            }
            unassignedStarts.removeAll(search.starts());

            int volume = search.starts().stream().mapToInt(volumes::get).sum();
            Map<BlockPos, Integer> candidate = new HashMap<>(plannedLevels);
            List<Target> targets = new ArrayList<>(search.targets().values());
            targets.sort(Comparator.comparingInt(Target::priority).thenComparingInt(Target::sequence));
            for (Target target : targets) {
                int previousVolume = volume;
                volume = simulateAdd(candidate, target.pos(), volume, levelAt);
                int accepted = previousVolume - volume;
                if (accepted > 0) {
                    recordFlowTransfers(acceptedFlowTransfers, target.path(), accepted);
                }
                if (volume == 0) {
                    break;
                }
            }
            if (volume != 0) {
                return null;
            }
            plannedLevels = candidate;
        }
        flowTransfers.putAll(acceptedFlowTransfers);
        return plannedLevels;
    }

    private static void applyPlan(ServerLevel level, Map<BlockPos, Integer> plannedLevels) {
        for (var entry : plannedLevels.entrySet()) {
            if (entry.getValue() == 0) {
                FiniteWaterPhysics.setWaterLevel(level, entry.getKey(), 0);
            }
        }
        for (var entry : plannedLevels.entrySet()) {
            if (entry.getValue() > 0) {
                FiniteWaterPhysics.setWaterLevel(level, entry.getKey(), entry.getValue());
            }
        }
    }

    private static SearchResult findComponent(BlockPos seed, Map<BlockPos, Integer> volumes,
                                                Direction pistonDirection, ToIntFunction<BlockPos> levelAt,
                                                int maxDepth, int maxVisitedWaterCells,
                                                Predicate<BlockPos> isLoaded, Set<BlockPos> pistonOccupiedPositions,
                                                Set<BlockPos> visitedWaterCells) {
        if (!isLoaded.test(seed) || !visitWater(seed, visitedWaterCells, maxVisitedWaterCells)) {
            return null;
        }

        Direction[] directions = getPushDirections(pistonDirection);
        int rootPriority = directions.length;
        ArrayDeque<SearchState> queue = new ArrayDeque<>();
        Map<BlockPos, int[]> bestDepths = new HashMap<>();
        Set<BlockPos> starts = new LinkedHashSet<>();
        Map<BlockPos, Target> targets = new LinkedHashMap<>();
        starts.add(seed);
        enqueue(queue, bestDepths, seed, seed, 0, rootPriority, directions.length, List.of(seed));

        while (!queue.isEmpty()) {
            SearchState state = queue.removeFirst();
            if (bestDepths.get(state.pos())[state.priority()] != state.depth() || state.depth() >= maxDepth) {
                continue;
            }

            for (int index = 0; index < directions.length; index++) {
                Direction direction = directions[index];
                BlockPos next = state.pos().relative(direction);
                if (!isLoaded.test(next)) {
                    continue;
                }

                int level = levelAt.applyAsInt(next);
                if (level < 0) {
                    continue;
                }

                int priority = state.priority() == rootPriority ? index : state.priority();
                if (level == 0) {
                    addOutletTargets(
                            targets, next, direction, state.depth() + 1, maxDepth,
                            priority, levelAt, isLoaded, pistonOccupiedPositions, state.path()
                    );
                    continue;
                }

                if (!visitWater(next, visitedWaterCells, maxVisitedWaterCells)) {
                    return null;
                }
                List<BlockPos> nextPath = appendPath(state.path(), next);
                if (!pistonOccupiedPositions.contains(next)
                        && volumes.containsKey(next) && !next.equals(state.origin())) {
                    addTarget(targets, next, priority, nextPath);
                } else if (!pistonOccupiedPositions.contains(next)
                        && !volumes.containsKey(next) && level < MAX_WATER_LEVEL) {
                    addTarget(targets, next, priority, nextPath);
                }

                if (volumes.containsKey(next)) {
                    if (starts.add(next)) {
                        enqueue(queue, bestDepths, next, next, 0, rootPriority,
                                directions.length, List.of(next));
                    }
                } else {
                    enqueue(queue, bestDepths, next, state.origin(), state.depth() + 1,
                            priority, directions.length, nextPath);
                }
            }
        }

        return new SearchResult(starts, targets);
    }

    private static void addOutletTargets(Map<BlockPos, Target> targets, BlockPos firstAir,
                                         Direction direction, int firstDepth, int maxDepth, int priority,
                                         ToIntFunction<BlockPos> levelAt, Predicate<BlockPos> isLoaded,
                                         Set<BlockPos> pistonOccupiedPositions, List<BlockPos> waterPath) {
        BlockPos cursor = firstAir;
        List<BlockPos> path = waterPath;
        for (int depth = firstDepth; depth <= maxDepth; depth++) {
            if (!isLoaded.test(cursor)
                    || pistonOccupiedPositions.contains(cursor)
                    || levelAt.applyAsInt(cursor) != 0) {
                return;
            }
            path = appendPath(path, cursor);
            addTarget(targets, cursor, priority, path);
            cursor = cursor.relative(direction);
        }
    }

    private static void enqueue(ArrayDeque<SearchState> queue, Map<BlockPos, int[]> bestDepths,
                                BlockPos pos, BlockPos origin, int depth, int priority, int directionCount,
                                List<BlockPos> path) {
        int[] depths = bestDepths.computeIfAbsent(pos, ignored -> {
            int[] values = new int[directionCount + 1];
            java.util.Arrays.fill(values, Integer.MAX_VALUE);
            return values;
        });
        if (depth < depths[priority]) {
            depths[priority] = depth;
            queue.addLast(new SearchState(pos, origin, depth, priority, path));
        }
    }

    private static boolean visitWater(BlockPos pos, Set<BlockPos> visitedWaterCells, int limit) {
        return !visitedWaterCells.add(pos) || visitedWaterCells.size() <= limit;
    }

    private static void addTarget(Map<BlockPos, Target> targets, BlockPos pos, int priority,
                                  List<BlockPos> path) {
        Target existing = targets.get(pos);
        if (existing == null) {
            targets.put(pos, new Target(pos, priority, targets.size(), path));
        } else if (priority < existing.priority()
                || priority == existing.priority() && path.size() < existing.path().size()) {
            targets.put(pos, new Target(pos, priority, existing.sequence(), path));
        }
    }

    private static List<BlockPos> appendPath(List<BlockPos> path, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(path.size() + 1);
        result.addAll(path);
        result.add(pos);
        return List.copyOf(result);
    }

    private static void recordFlowTransfers(Map<BlockPos, Vec3> transfers,
                                            List<BlockPos> path, int accepted) {
        Map<BlockPos, Vec3> pathTransfers = new HashMap<>();
        for (int index = 1; index < path.size(); index++) {
            BlockPos previous = path.get(index - 1);
            BlockPos current = path.get(index);
            Vec3 units = new Vec3(
                    Integer.signum(current.getX() - previous.getX()) * accepted,
                    Integer.signum(current.getY() - previous.getY()) * accepted,
                    Integer.signum(current.getZ() - previous.getZ()) * accepted
            );
            pathTransfers.merge(previous, units, FiniteWaterPhysics::combineCurrentUnits);
            pathTransfers.merge(current, units, FiniteWaterPhysics::combineCurrentUnits);
        }
        pathTransfers.forEach((pos, units) -> transfers.merge(
                pos, units, FiniteWaterPhysics::combineCurrentUnits
        ));
    }

    private static int simulateAdd(Map<BlockPos, Integer> plan, BlockPos pos, int volume,
                                   ToIntFunction<BlockPos> levelAt) {
        int current = plan.containsKey(pos) ? plan.get(pos) : levelAt.applyAsInt(pos);
        if (current < 0) {
            return -1;
        }
        int accepted = Math.min(MAX_WATER_LEVEL - current, volume);
        if (accepted > 0) {
            plan.put(pos, current + accepted);
        }
        return volume - accepted;
    }

    private static Direction[] getPushDirections(Direction pistonDirection) {
        if (pistonDirection.getAxis() == Direction.Axis.Y) {
            return new Direction[]{
                    pistonDirection, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
                    pistonDirection.getOpposite()
            };
        }

        return new Direction[]{
                pistonDirection,
                pistonDirection.getClockWise(),
                pistonDirection.getCounterClockWise(),
                Direction.UP,
                Direction.DOWN,
                pistonDirection.getOpposite()
        };
    }

    private record SearchState(BlockPos pos, BlockPos origin, int depth, int priority, List<BlockPos> path) {
    }

    private record Target(BlockPos pos, int priority, int sequence, List<BlockPos> path) {
    }

    private record SearchResult(Set<BlockPos> starts, Map<BlockPos, Target> targets) {
    }
}
