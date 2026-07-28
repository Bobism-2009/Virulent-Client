package dev.virulent.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class PathFinder {
	private static final Direction[] HORIZONTAL = {
		Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
	};

	private PathFinder() {
	}

	public static List<BlockPos> findPath(Level level, BlockPos start, BlockPos goal, int maxNodes) {
		if (start.equals(goal)) {
			return List.of(start);
		}

		BlockPos walkStart = findStandable(level, start);
		BlockPos walkGoal = findStandable(level, goal);
		if (walkStart == null || walkGoal == null) {
			return List.of();
		}

		PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));
		Map<BlockPos, Node> nodes = new HashMap<>();
		Set<BlockPos> closed = new HashSet<>();

		Node startNode = new Node(walkStart, null, 0.0, heuristic(walkStart, walkGoal));
		open.add(startNode);
		nodes.put(walkStart, startNode);

		int explored = 0;
		while (!open.isEmpty() && explored < maxNodes) {
			Node current = open.poll();
			explored++;

			if (current.pos.equals(walkGoal) || horizontalDist(current.pos, walkGoal) <= 1.5 && Math.abs(current.pos.getY() - walkGoal.getY()) <= 2) {
				return reconstruct(current);
			}

			if (!closed.add(current.pos)) {
				continue;
			}

			for (Direction direction : HORIZONTAL) {
				consider(level, current, current.pos.relative(direction), walkGoal, open, nodes, closed, 1.0);
			}

			for (Direction direction : HORIZONTAL) {
				BlockPos stepUp = current.pos.relative(direction).above();
				consider(level, current, stepUp, walkGoal, open, nodes, closed, 1.4);
			}

			for (Direction direction : HORIZONTAL) {
				for (int drop = 1; drop <= 3; drop++) {
					BlockPos dropPos = current.pos.relative(direction).below(drop);
					consider(level, current, dropPos, walkGoal, open, nodes, closed, 1.0 + drop * 0.2);
				}
			}
		}

		return List.of();
	}

	private static void consider(
		Level level,
		Node current,
		BlockPos next,
		BlockPos goal,
		PriorityQueue<Node> open,
		Map<BlockPos, Node> nodes,
		Set<BlockPos> closed,
		double cost
	) {
		if (closed.contains(next) || !isWalkable(level, next)) {
			return;
		}

		double g = current.g + cost;
		Node existing = nodes.get(next);
		if (existing != null && existing.g <= g) {
			return;
		}

		Node node = new Node(next, current, g, g + heuristic(next, goal));
		nodes.put(next, node);
		open.add(node);
	}

	public static boolean isWalkable(Level level, BlockPos feet) {
		BlockPos below = feet.below();
		BlockPos head = feet.above();
		return canStandOn(level, below) && isPassable(level, feet) && isPassable(level, head);
	}

	public static boolean canStandOn(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return !shape.isEmpty();
	}

	public static boolean isPassable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().isEmpty() && !state.getFluidState().is(Fluids.WATER)) {
			return false;
		}
		if (TreeUtil.isLeaves(state)) {
			return true;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty();
	}

	public static BlockPos findStandable(Level level, BlockPos origin) {
		BlockPos cursor = origin;
		for (int i = 0; i < 8; i++) {
			if (isWalkable(level, cursor)) {
				return cursor;
			}
			cursor = cursor.below();
		}

		cursor = origin;
		for (int i = 0; i < 4; i++) {
			if (isWalkable(level, cursor)) {
				return cursor;
			}
			cursor = cursor.above();
		}
		return null;
	}

	private static double heuristic(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
	}

	private static double horizontalDist(BlockPos a, BlockPos b) {
		int dx = a.getX() - b.getX();
		int dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static List<BlockPos> reconstruct(Node end) {
		List<BlockPos> path = new ArrayList<>();
		Node current = end;
		while (current != null) {
			path.add(current.pos);
			current = current.parent;
		}
		Collections.reverse(path);
		return path;
	}

	private static final class Node {
		private final BlockPos pos;
		private final Node parent;
		private final double g;
		private final double f;

		private Node(BlockPos pos, Node parent, double g, double f) {
			this.pos = pos;
			this.parent = parent;
			this.g = g;
			this.f = f;
		}
	}
}
