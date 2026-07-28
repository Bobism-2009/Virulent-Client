package dev.virulent.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class TreeUtil {
	private TreeUtil() {
	}

	public static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	public static boolean isLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES);
	}

	public static List<BlockPos> collectConnectedLogs(Level level, BlockPos start) {
		List<BlockPos> logs = new ArrayList<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();

		queue.add(start);
		visited.add(start);

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			logs.add(pos);

			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (visited.contains(next)) {
					continue;
				}

				BlockState state = level.getBlockState(next);
				if (isLog(state)) {
					visited.add(next);
					queue.add(next);
				}
			}
		}

		return logs;
	}

	public static List<BlockPos> collectConnectedTree(Level level, BlockPos start, boolean includeLeaves) {
		List<BlockPos> blocks = new ArrayList<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();

		queue.add(start);
		visited.add(start);

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			BlockState state = level.getBlockState(pos);
			boolean logBlock = isLog(state);
			boolean leafBlock = includeLeaves && isLeaves(state);
			if (!logBlock && !leafBlock) {
				continue;
			}

			blocks.add(pos);

			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (visited.contains(next)) {
					continue;
				}

				BlockState nextState = level.getBlockState(next);
				if (isLog(nextState) || (includeLeaves && isLeaves(nextState))) {
					visited.add(next);
					queue.add(next);
				}
			}
		}

		return blocks;
	}

	public static List<BlockPos> sortForHarvest(List<BlockPos> blocks, BlockPos playerPos, Level level) {
		return blocks.stream()
			.sorted(Comparator
				.<BlockPos>comparingInt(pos -> isLog(level.getBlockState(pos)) ? 0 : 1)
				.thenComparingInt(BlockPos::getY)
				.thenComparingDouble(pos -> horizontalDistSqr(pos, playerPos)))
			.toList();
	}

	public static double horizontalDistSqr(BlockPos pos, BlockPos origin) {
		int dx = pos.getX() - origin.getX();
		int dz = pos.getZ() - origin.getZ();
		return dx * dx + dz * dz;
	}

	public static Vec3 horizontalCenter(BlockPos pos, double y) {
		return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
	}

	public static boolean hasLineOfSightToBlock(Level level, Entity viewer, BlockPos target) {
		Vec3 eye = viewer.getEyePosition();
		Vec3 end = Vec3.atCenterOf(target);
		BlockHitResult hit = level.clip(new ClipContext(
			eye,
			end,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			viewer
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return true;
		}
		return hit.getBlockPos().equals(target);
	}

	public static BlockPos findObstructingBlock(Level level, Entity viewer, BlockPos target) {
		Vec3 eye = viewer.getEyePosition();
		Vec3 end = Vec3.atCenterOf(target);
		BlockHitResult hit = level.clip(new ClipContext(
			eye,
			end,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			viewer
		));
		if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target)) {
			return null;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (isLeaves(state)) {
			return hit.getBlockPos();
		}
		return null;
	}

	public static Direction getClosestFace(BlockPos pos, Vec3 eye) {
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 diff = center.subtract(eye);
		double absX = Math.abs(diff.x);
		double absY = Math.abs(diff.y);
		double absZ = Math.abs(diff.z);

		if (absX > absY && absX > absZ) {
			return diff.x > 0 ? Direction.EAST : Direction.WEST;
		}
		if (absY > absZ) {
			return diff.y > 0 ? Direction.UP : Direction.DOWN;
		}
		return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	public static float[] getRotationsToBlock(Vec3 eye, BlockPos pos) {
		return CombatUtil.getRotations(eye, Vec3.atCenterOf(pos));
	}
}
