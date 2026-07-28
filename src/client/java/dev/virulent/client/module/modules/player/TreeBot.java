/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * Copyright (c) 2026 Virulent Client contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Wurst Client TreeBot (https://github.com/Wurst-Imperium/Wurst7)
 * and adapted for Virulent Client's module system, pathfinding, and rendering.
 */
package dev.virulent.client.module.modules.player;

import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.mixin.ClientInputAccessor;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.module.modules.player.treebot.Tree;
import dev.virulent.client.module.modules.player.treebot.TreeBotUtils;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.BotMovement;
import dev.virulent.client.util.CombatUtil;
import dev.virulent.client.util.PathFinder;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.util.TreeUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class TreeBot extends Module {
	private static final int BREAK_TIMEOUT_TICKS = 100;
	private static final int STUCK_JUMP_TICKS = 12;
	// After this many stuck ticks, jumping clearly isn't helping — stop mashing it.
	private static final int STOP_JUMPING_TICKS = 40;
	private static final int STUCK_REPATH_TICKS = 30;
	private static final int SKIP_COOLDOWN_TICKS = 200;
	private static final int IGNORE_BREAK_TICKS = 60;
	// Total time we'll spend on a single tree (walking + chopping) before abandoning it
	// when no logs are getting broken. Prevents stuck-in-canopy / stuck-on-hilltop loops.
	private static final int TREE_STALL_ABANDON_TICKS = 100;

	private final NumberSetting range = addSetting(new NumberSetting("Range", 4.5, 1.0, 6.0, 0.05));
	private final NumberSetting maxLogs = addSetting(new NumberSetting("Max Logs", 48.0, 3.0, 128.0, 1.0));
	private final NumberSetting searchRange = addSetting(new NumberSetting("Search Range", 32.0, 8.0, 64.0, 1.0));
	private final dev.virulent.client.setting.BooleanSetting collectDrops =
		addSetting(new dev.virulent.client.setting.BooleanSetting("Collect Drops", true));
	private final NumberSetting collectRange = addSetting(new NumberSetting("Collect Range", 16.0, 4.0, 48.0, 1.0));

	private final Map<BlockPos, Integer> skippedStumps = new HashMap<>();
	private final Map<BlockPos, Integer> ignoredBlocks = new HashMap<>();

	private Phase phase = Phase.SEARCH;
	private Tree tree;
	private BlockPos walkGoal;
	private List<BlockPos> path = new ArrayList<>();
	private int pathIndex;
	private int stuckTicks;
	private int breakTicks;
	private BlockPos currentBlock;
	private Vec3 lastMovementPos;
	private int tickCounter;
	private int lastLogCount = -1;
	private int lastProgressTick;
	private ItemEntity collectTarget;
	private int collectStartTick;

	private record BreakParams(BlockPos pos, Direction side, Vec3 hitVec, double distanceSq, boolean los) {
	}

	private enum Phase {
		SEARCH,
		GO_TO_TREE,
		CHOP,
		GO_TO_ANGLE,
		COLLECT
	}

	public TreeBot() {
		super("TreeBot", "Wurst-style tree chopper: find stump, path in, chop with LOS.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render3DEvent.class, this::onRender3D);
	}

	@Override
	protected void onEnable() {
		reset();
	}

	@Override
	protected void onDisable() {
		releaseControls();
		resetBreak();
		reset();
	}

	private void reset() {
		tree = null;
		walkGoal = null;
		path.clear();
		pathIndex = 0;
		stuckTicks = 0;
		breakTicks = 0;
		currentBlock = null;
		lastMovementPos = null;
		skippedStumps.clear();
		ignoredBlocks.clear();
		phase = Phase.SEARCH;
		tickCounter = 0;
		lastLogCount = -1;
		lastProgressTick = 0;
		collectTarget = null;
		collectStartTick = 0;
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().gameMode == null) {
			return;
		}

		tickCounter++;
		decaySkips();
		ignoredBlocks.entrySet().removeIf(entry -> entry.getValue() <= tickCounter);

		// Global stall watchdog: if we have a tree assigned but haven't broken a log
		// in TREE_STALL_ABANDON_TICKS ticks, drop it regardless of phase.
		if (tree != null) {
			int currentLogCount = tree.getLogs().size();
			if (lastLogCount == -1 || currentLogCount < lastLogCount) {
				lastLogCount = currentLogCount;
				lastProgressTick = tickCounter;
			} else if (tickCounter - lastProgressTick >= TREE_STALL_ABANDON_TICKS) {
				dev.virulent.client.VirulentClient.LOGGER.info(
					"[TreeBot] abandoning stump {} — stalled in phase {} for {}t, {} logs left: {}",
					tree.getStump(), phase, TREE_STALL_ABANDON_TICKS, currentLogCount, tree.getLogs()
				);
				skipStump(tree.getStump());
				for (BlockPos log : tree.getLogs()) {
					ignoreBlock(log);
				}
				releaseControls();
				resetBreak();
				tree = null;
				walkGoal = null;
				path.clear();
				pathIndex = 0;
				stuckTicks = 0;
				lastMovementPos = null;
				lastLogCount = -1;
				phase = Phase.SEARCH;
			}
		} else {
			lastLogCount = -1;
		}

		switch (phase) {
			case SEARCH -> searchForTree();
			case GO_TO_TREE, GO_TO_ANGLE -> followPath();
			case CHOP -> chopTree();
			case COLLECT -> collectDrops();
		}

		if (tickCounter % 20 == 0) {
			BlockPos playerPos = mc().player.blockPosition();
			int pathLen = path.size();
			BlockPos target = walkGoal;
			dev.virulent.client.VirulentClient.LOGGER.info(
				"[TreeBot] phase={} player={} walkGoal={} pathIdx={}/{} stuck={} tree={}",
				phase, playerPos, target, pathIndex, pathLen, stuckTicks,
				tree == null ? "null" : tree.getStump() + " logs=" + tree.getLogs().size()
			);
		}
	}

	private void decaySkips() {
		skippedStumps.entrySet().removeIf(entry -> entry.getValue() <= tickCounter);
	}

	private void searchForTree() {
		releaseMovement();
		resetBreak();
		LocalPlayer player = mc().player;

		// Prefer sweeping nearby drops before hunting for another tree.
		if (collectDrops.getValue()) {
			ItemEntity drop = findNearbyDrop();
			if (drop != null) {
				collectTarget = drop;
				collectStartTick = tickCounter;
				phase = Phase.COLLECT;
				return;
			}
		}

		BlockPos origin = player.blockPosition();
		int radius = searchRange.getValue().intValue();

		BlockPos reachable = findReachableLogNear(origin);
		if (reachable != null) {
			BlockPos stump = findStumpFrom(reachable);
			ArrayList<BlockPos> logs = analyzeTree(stump);
			if (!logs.isEmpty() && logs.size() <= maxLogs.getValue().intValue()) {
				tree = new Tree(stump, logs);
				lastLogCount = -1;
				phase = Phase.CHOP;
				return;
			}
		}

		BlockPos stump = findNearbyStump(origin, radius);
		if (stump == null) {
			return;
		}

		ArrayList<BlockPos> logs = analyzeTree(stump);
		if (logs.isEmpty() || logs.size() > maxLogs.getValue().intValue()) {
			skipStump(stump);
			return;
		}

		tree = new Tree(stump, logs);
		lastLogCount = -1;
		if (canReachAnyLog(logs)) {
			phase = Phase.CHOP;
			return;
		}

		BlockPos stand = bestAdjacentStand(stump);
		if (stand == null) {
			stand = stump;
		}

		beginWalk(stand, Phase.GO_TO_TREE);
	}

	private void followPath() {
		if (tree == null) {
			phase = Phase.SEARCH;
			releaseControls();
			return;
		}

		if (canReachAnyLog(tree.getLogs())) {
			releaseMovement();
			phase = Phase.CHOP;
			return;
		}

		// PathFinder treats leaves as passable, but they still collide — clear them first.
		if (clearBlockingLeaves()) {
			return;
		}

		BlockPos goal = walkGoal != null ? walkGoal : tree.getStump();
		// Arrive only when standing on the goal — manhattan<=1 was ending walks early.
		if (!path.isEmpty() && pathIndex < path.size()) {
			while (pathIndex < path.size() - 1) {
				BlockPos node = path.get(pathIndex);
				if (sameColumn(mc().player.blockPosition(), node)
					&& Math.abs(mc().player.blockPosition().getY() - node.getY()) <= 1) {
					pathIndex++;
					stuckTicks = 0;
				} else {
					break;
				}
			}
			BlockPos next = path.get(Math.min(pathIndex, path.size() - 1));
			if (sameColumn(mc().player.blockPosition(), next)
				&& Math.abs(mc().player.blockPosition().getY() - next.getY()) <= 1
				&& pathIndex >= path.size() - 1) {
				releaseMovement();
				phase = Phase.CHOP;
				return;
			}
			walkToward(next);
		} else {
			if (sameColumn(mc().player.blockPosition(), goal)
				&& Math.abs(mc().player.blockPosition().getY() - goal.getY()) <= 1) {
				releaseMovement();
				phase = Phase.CHOP;
				return;
			}
			walkToward(goal);
		}

		if (stuckTicks >= STUCK_REPATH_TICKS) {
			recomputePath(goal);
			stuckTicks = 0;
		}
	}

	private void collectDrops() {
		resetBreak();
		LocalPlayer player = mc().player;
		if (collectTarget == null || !collectTarget.isAlive() || collectTarget.isRemoved()) {
			collectTarget = findNearbyDrop();
			if (collectTarget == null) {
				releaseMovement();
				phase = Phase.SEARCH;
				return;
			}
			collectStartTick = tickCounter;
		}

		// Vanilla pickup radius is ~1 block from bounding box center.
		double dx = collectTarget.getX() - player.getX();
		double dz = collectTarget.getZ() - player.getZ();
		double horizDistSq = dx * dx + dz * dz;
		if (horizDistSq <= 0.5 * 0.5) {
			// Reached this drop — grab the next one or bail out to SEARCH.
			collectTarget = findNearbyDrop();
			if (collectTarget == null) {
				releaseMovement();
				phase = Phase.SEARCH;
				return;
			}
			collectStartTick = tickCounter;
			return;
		}

		// Safety: don't loop forever on unreachable items (e.g. drops in a pit).
		if (tickCounter - collectStartTick >= 100) {
			ignoreDrop(collectTarget);
			collectTarget = null;
			releaseMovement();
			phase = Phase.SEARCH;
			return;
		}

		BlockPos step = BlockPos.containing(collectTarget.getX(), player.getY(), collectTarget.getZ());
		walkToward(step);
	}

	private final Set<Integer> ignoredDrops = new HashSet<>();

	private void ignoreDrop(ItemEntity entity) {
		ignoredDrops.add(entity.getId());
	}

	private ItemEntity findNearbyDrop() {
		if (!collectDrops.getValue()) {
			return null;
		}
		LocalPlayer player = mc().player;
		double radius = collectRange.getValue();
		AABB box = player.getBoundingBox().inflate(radius, radius / 2.0, radius);
		ItemEntity best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (ItemEntity entity : mc().level.getEntitiesOfClass(ItemEntity.class, box, this::isTreeDrop)) {
			if (ignoredDrops.contains(entity.getId())) {
				continue;
			}
			double distSq = entity.distanceToSqr(player);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = entity;
			}
		}
		return best;
	}

	private boolean isTreeDrop(ItemEntity entity) {
		if (entity == null || !entity.isAlive() || entity.isRemoved()) {
			return false;
		}
		ItemStack stack = entity.getItem();
		if (stack.isEmpty()) {
			return false;
		}
		return stack.is(ItemTags.LOGS)
			|| stack.is(ItemTags.LEAVES)
			|| stack.is(ItemTags.SAPLINGS)
			|| stack.getItem() == net.minecraft.world.item.Items.STICK
			|| stack.getItem() == net.minecraft.world.item.Items.APPLE;
	}

	private void chopTree() {
		releaseMovement();
		if (tree == null) {
			phase = Phase.SEARCH;
			return;
		}

		tree.getLogs().removeIf(Predicate.not(TreeBotUtils::isLog));
		if (tree.getLogs().isEmpty()) {
			tree = null;
			resetBreak();
			phase = Phase.SEARCH;
			lastLogCount = -1;
			return;
		}

		if (currentBlock != null && canMine(currentBlock)) {
			if (breakBlock(currentBlock)) {
				return;
			}
		} else if (currentBlock != null) {
			resetBreak();
		}

		List<BlockPos> ordered = tree.getLogs().stream()
			.sorted(Comparator
				.<BlockPos>comparingInt(BlockPos::getY)
				.thenComparingDouble(pos -> mc().player.blockPosition().distSqr(pos)))
			.toList();

		for (BlockPos log : ordered) {
			if (!canMine(log)) {
				BlockPos obstructing = TreeUtil.findObstructingBlock(mc().level, mc().player, log);
				if (obstructing != null && canMine(obstructing) && breakBlock(obstructing)) {
					return;
				}
				continue;
			}
			BlockPos obstructing = TreeUtil.findObstructingBlock(mc().level, mc().player, log);
			if (obstructing != null) {
				if (canMine(obstructing) && breakBlock(obstructing)) {
					return;
				}
				continue;
			}
			if (breakBlock(log)) {
				return;
			}
		}

		resetBreak();
		BlockPos angle = findAnglePosition();
		if (angle != null) {
			beginWalk(angle, Phase.GO_TO_ANGLE);
			return;
		}

		BlockPos stand = bestAdjacentStand(tree.getStump());
		if (stand == null) {
			stand = tree.getStump();
		}
		if (mc().player.blockPosition().distManhattan(stand) <= 1) {
			// Already there but still can't chop — cool down this stump.
			skipStump(tree.getStump());
			tree = null;
			phase = Phase.SEARCH;
			return;
		}
		beginWalk(stand, Phase.GO_TO_ANGLE);
	}

	private static boolean sameColumn(BlockPos a, BlockPos b) {
		return a.getX() == b.getX() && a.getZ() == b.getZ();
	}

	private void beginWalk(BlockPos goal, Phase next) {
		walkGoal = goal;
		recomputePath(goal);
		phase = next;
	}

	private void walkToward(BlockPos target) {
		mc().options.keyAttack.setDown(false);
		LocalPlayer player = mc().player;
		Vec3 goal = TreeUtil.horizontalCenter(target, player.getY());
		Vec3 playerPos = player.position();
		Vec3 diff = goal.subtract(playerPos);
		float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
		CombatUtil.applyRotations(player, yaw, 0.0f);
		player.yBodyRot = yaw;
		player.yBodyRotO = yaw;

		if (lastMovementPos == null) {
			lastMovementPos = playerPos;
			stuckTicks = 0;
		}
		if (playerPos.distanceToSqr(lastMovementPos) < 0.0025) {
			stuckTicks++;
		} else {
			stuckTicks = 0;
			lastMovementPos = playerPos;
		}

		boolean needStepUp = target.getY() > player.blockPosition().getY();
		// Pulse-jump on odd stuck-ticks and stop entirely once it's clearly not helping,
		// so we don't hammer the corner forever waiting on the watchdog.
		boolean stuckJumpWindow = stuckTicks >= STUCK_JUMP_TICKS
			&& stuckTicks < STOP_JUMPING_TICKS
			&& (stuckTicks % 4) < 2;
		boolean shouldJump = (stuckJumpWindow || needStepUp) && findNearbyLeaf(true) == null;
		mc().options.keyUp.setDown(true);
		mc().options.keySprint.setDown(true);
		mc().options.keyJump.setDown(shouldJump);
		BotMovement.set(true, shouldJump, true);
		// Also write directly to the player's input so movement isn't lost when
		// KeyboardInput.tick rebuilds keyPresses from KeyMappings.
		player.input.keyPresses = new Input(true, false, false, false, shouldJump, false, true);
		((ClientInputAccessor) (Object) player.input).virulent$setMoveVector(new Vec2(0.0f, 1.0f));
	}

	private void releaseMovement() {
		BotMovement.clear();
		mc().options.keyUp.setDown(false);
		mc().options.keyDown.setDown(false);
		mc().options.keyLeft.setDown(false);
		mc().options.keyRight.setDown(false);
		mc().options.keyJump.setDown(false);
		mc().options.keySprint.setDown(false);
	}

	private void releaseControls() {
		releaseMovement();
		mc().options.keyAttack.setDown(false);
	}

	/**
	 * Only mine blocks that are breakable, in vanilla reach, and have LOS to a face hit.
	 */
	private boolean breakBlock(BlockPos pos) {
		BreakParams params = getBreakParams(pos);
		if (params == null || !canMine(pos, params)) {
			ignoreBlock(pos);
			resetBreak();
			return false;
		}

		equipBestTool(pos);
		float[] rotations = CombatUtil.getRotations(mc().player.getEyePosition(), params.hitVec());
		CombatUtil.applyRotations(mc().player, rotations[0], rotations[1]);
		mc().options.keyAttack.setDown(true);

		if (currentBlock == null || !currentBlock.equals(pos)) {
			currentBlock = pos;
			breakTicks = 0;
		} else {
			breakTicks++;
			if (breakTicks >= BREAK_TIMEOUT_TICKS) {
				ignoreBlock(pos);
				resetBreak();
				if (TreeBotUtils.isLog(pos)) {
					skipStump(findStumpFrom(pos));
				}
				return false;
			}
		}

		if (!mc().gameMode.continueDestroyBlock(pos, params.side())) {
			ignoreBlock(pos);
			resetBreak();
			return false;
		}
		mc().player.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	private void ignoreBlock(BlockPos pos) {
		ignoredBlocks.put(pos.immutable(), tickCounter + IGNORE_BREAK_TICKS);
	}

	private boolean canMine(BlockPos pos) {
		return canMine(pos, getBreakParams(pos));
	}

	private boolean canMine(BlockPos pos, BreakParams params) {
		if (params == null || ignoredBlocks.containsKey(pos)) {
			return false;
		}
		if (!TreeBotUtils.isLog(pos) && !TreeBotUtils.isLeaves(pos)) {
			return false;
		}
		BlockState state = mc().level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(mc().level, pos) < 0.0f) {
			return false;
		}
		if (state.getDestroyProgress(mc().player, mc().level, pos) <= 0.0f) {
			return false;
		}
		if (!params.los()) {
			return false;
		}
		double maxRange = Math.min(range.getValue(), mc().player.blockInteractionRange());
		if (params.distanceSq() > maxRange * maxRange) {
			return false;
		}
		return mc().player.isWithinBlockInteractionRange(pos, 0.0);
	}

	private BreakParams getBreakParams(BlockPos pos) {
		BlockState state = mc().level.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}
		var shape = state.getShape(mc().level, pos);
		AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
		Vec3 eyes = mc().player.getEyePosition();
		Vec3 center = box.getCenter();
		double centerDist = eyes.distanceToSqr(center);

		Direction bestSide = Direction.NORTH;
		Vec3 bestHit = center;
		double bestDist = Double.MAX_VALUE;
		boolean bestLos = false;

		for (Direction side : Direction.values()) {
			Vec3 hit = faceHitVec(box, side);
			double dist = eyes.distanceToSqr(hit);
			// Rear faces can't have LOS.
			boolean los = dist < centerDist && hasLosTo(eyes, hit, pos);
			if (!bestLos && los) {
				bestLos = true;
				bestSide = side;
				bestHit = hit;
				bestDist = dist;
				continue;
			}
			if (bestLos && !los) {
				continue;
			}
			if (dist < bestDist) {
				bestDist = dist;
				bestSide = side;
				bestHit = hit;
				bestLos = los;
			}
		}
		return new BreakParams(pos, bestSide, bestHit, bestDist, bestLos);
	}

	private static Vec3 faceHitVec(AABB box, Direction side) {
		return switch (side) {
			case DOWN -> new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
			case UP -> new Vec3(box.getCenter().x, box.maxY, box.getCenter().z);
			case NORTH -> new Vec3(box.getCenter().x, box.getCenter().y, box.minZ);
			case SOUTH -> new Vec3(box.getCenter().x, box.getCenter().y, box.maxZ);
			case WEST -> new Vec3(box.minX, box.getCenter().y, box.getCenter().z);
			case EAST -> new Vec3(box.maxX, box.getCenter().y, box.getCenter().z);
		};
	}

	private boolean hasLosTo(Vec3 eyes, Vec3 hitVec, BlockPos target) {
		BlockHitResult hit = mc().level.clip(new ClipContext(
			eyes,
			hitVec,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			mc().player
		));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}

	private void resetBreak() {
		if (mc().gameMode != null && mc().gameMode.isDestroying()) {
			mc().gameMode.stopDestroyBlock();
		}
		mc().options.keyAttack.setDown(false);
		currentBlock = null;
		breakTicks = 0;
	}

	/**
	 * Only clear leaves that are physically trapping the player.
	 * Path-ahead leaves are mined only after we get stuck — otherwise we never walk.
	 */
	private boolean clearBlockingLeaves() {
		BlockPos leaf = findNearbyLeaf(true);
		if (leaf == null && stuckTicks >= 8) {
			leaf = findNearbyLeaf(false);
			if (leaf == null) {
				leaf = findLeafOnPath();
			}
		}
		if (leaf == null) {
			return false;
		}
		releaseMovement();
		return breakBlock(leaf);
	}

	private BlockPos findNearbyLeaf(boolean onlyColliding) {
		LocalPlayer player = mc().player;
		AABB body = player.getBoundingBox().inflate(0.15, 0.05, 0.15);
		BlockPos feet = player.blockPosition();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;

		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos pos = feet.offset(dx, dy, dz);
					if (!TreeBotUtils.isLeaves(pos) || !canMine(pos)) {
						continue;
					}
					AABB leafBox = new AABB(pos);
					boolean colliding = body.intersects(leafBox);
					if (onlyColliding && !colliding) {
						continue;
					}
					double dist = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
					double score = colliding ? dist - 100.0 : dist;
					if (score < bestScore) {
						bestScore = score;
						best = pos;
					}
				}
			}
		}
		return best;
	}

	private BlockPos findLeafOnPath() {
		if (!mc().player.onGround()) {
			return null;
		}
		List<BlockPos> candidates = new ArrayList<>();
		if (!path.isEmpty() && pathIndex < path.size()) {
			int end = Math.min(path.size(), pathIndex + 6);
			for (int i = pathIndex; i < end; i++) {
				BlockPos pos = path.get(i);
				candidates.add(pos);
				candidates.add(pos.above());
			}
		} else if (walkGoal != null) {
			candidates.add(walkGoal);
			candidates.add(walkGoal.above());
			BlockPos ahead = BlockPos.containing(
				mc().player.position().add(mc().player.getLookAngle().multiply(1.0, 0.0, 1.0)));
			candidates.add(ahead);
			candidates.add(ahead.above());
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos candidate : candidates) {
			if (!TreeBotUtils.isLeaves(candidate) || !canMine(candidate)) {
				continue;
			}
			double dist = mc().player.blockPosition().distSqr(candidate);
			if (dist < bestDist) {
				bestDist = dist;
				best = candidate;
			}
		}
		return best;
	}

	private boolean canReachAnyLog(List<BlockPos> logs) {
		for (BlockPos log : logs) {
			if (canMine(log)) {
				return true;
			}
		}
		return false;
	}

	private boolean isReachable(BlockPos pos) {
		return canMine(pos);
	}

	private BlockPos findReachableLogNear(BlockPos origin) {
		int r = 5;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -2; dy <= 6; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					if (!TreeBotUtils.isLog(pos) || !isReachable(pos)) {
						continue;
					}
					double dist = origin.distSqr(pos);
					if (dist < bestDist) {
						bestDist = dist;
						best = pos;
					}
				}
			}
		}
		return best;
	}

	private BlockPos findAnglePosition() {
		if (tree == null) {
			return null;
		}
		LocalPlayer player = mc().player;
		BlockPos origin = player.blockPosition();
		double rangeSq = range.getValue() * range.getValue();
		// Which logs can we already hit from where we're standing? Angle positions must
		// let us reach at least one *new* log, or there's no point moving.
		Set<BlockPos> alreadyReachable = new HashSet<>();
		Vec3 currentEyes = player.getEyePosition();
		for (BlockPos log : tree.getLogs()) {
			if (currentEyes.distanceToSqr(Vec3.atCenterOf(log)) <= rangeSq && hasLosFrom(currentEyes, log)) {
				alreadyReachable.add(log);
			}
		}

		BlockPos best = null;
		int bestReachCount = 0;
		double bestDist = Double.MAX_VALUE;

		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					BlockPos stand = origin.offset(dx, dy, dz);
					if (stand.equals(origin) || !PathFinder.isWalkable(mc().level, stand)) {
						continue;
					}
					Vec3 eyes = Vec3.atBottomCenterOf(stand).add(0.0, player.getEyeHeight(player.getPose()), 0.0);
					int newReach = 0;
					for (BlockPos log : tree.getLogs()) {
						if (alreadyReachable.contains(log)) {
							continue;
						}
						if (eyes.distanceToSqr(Vec3.atCenterOf(log)) <= rangeSq && hasLosFrom(eyes, log)) {
							newReach++;
						}
					}
					if (newReach == 0) {
						continue;
					}
					double dist = origin.distSqr(stand);
					if (newReach > bestReachCount || (newReach == bestReachCount && dist < bestDist)) {
						bestReachCount = newReach;
						bestDist = dist;
						best = stand;
					}
				}
			}
		}
		return best;
	}

	private boolean hasLosFrom(Vec3 eyes, BlockPos target) {
		BlockHitResult hit = mc().level.clip(new ClipContext(
			eyes,
			Vec3.atCenterOf(target),
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			mc().player
		));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}

	private BlockPos findNearbyStump(BlockPos origin, int radius) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -3; dy <= 6; dy++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					if (skippedStumps.containsKey(pos) || !isTreeStump(pos)) {
						continue;
					}
					double dist = origin.distSqr(pos);
					if (dist < bestDist) {
						bestDist = dist;
						best = pos;
					}
				}
			}
		}
		return best;
	}

	private void skipStump(BlockPos pos) {
		skippedStumps.put(pos.immutable(), tickCounter + SKIP_COOLDOWN_TICKS);
	}

	private boolean isTreeStump(BlockPos pos) {
		return TreeBotUtils.isLog(pos) && !TreeBotUtils.isLog(pos.below());
	}

	private BlockPos findStumpFrom(BlockPos log) {
		BlockPos cursor = log;
		while (TreeBotUtils.isLog(cursor.below())) {
			cursor = cursor.below();
		}
		return cursor;
	}

	private ArrayList<BlockPos> analyzeTree(BlockPos stump) {
		ArrayList<BlockPos> logs = new ArrayList<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		logs.add(stump);
		queue.add(stump);
		visited.add(stump);

		for (int i = 0; i < 2048 && !queue.isEmpty(); i++) {
			BlockPos current = queue.pollFirst();
			for (BlockPos next : getNeighbors(current)) {
				if (!visited.add(next)) {
					continue;
				}
				logs.add(next);
				queue.add(next);
			}
		}
		return logs;
	}

	private ArrayList<BlockPos> getNeighbors(BlockPos pos) {
		ArrayList<BlockPos> neighbors = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					BlockPos next = pos.offset(dx, dy, dz);
					if (TreeBotUtils.isLog(next)) {
						neighbors.add(next);
					}
				}
			}
		}
		return neighbors;
	}

	private BlockPos bestAdjacentStand(BlockPos stump) {
		return Stream.of(
				stump.north(), stump.east(), stump.south(), stump.west(),
				stump.north().east(), stump.north().west(),
				stump.south().east(), stump.south().west())
			.filter(pos -> PathFinder.isWalkable(mc().level, pos))
			.min(Comparator.comparingDouble(pos -> mc().player.blockPosition().distSqr(pos)))
			.orElse(null);
	}

	private void recomputePath(BlockPos goal) {
		path.clear();
		pathIndex = 0;
		stuckTicks = 0;
		lastMovementPos = null;
		List<BlockPos> found = PathFinder.findPath(
			mc().level,
			mc().player.blockPosition(),
			goal,
			3000
		);
		path.addAll(found);
	}

	private void equipBestTool(BlockPos pos) {
		BlockState state = mc().level.getBlockState(pos);
		int bestSlot = -1;
		float bestSpeed = 1.0f;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc().player.getInventory().getItem(slot);
			float speed = stack.getDestroySpeed(state);
			if (stack.is(ItemTags.AXES)) {
				speed += 0.5f;
			}
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = slot;
			}
		}
		if (bestSlot != -1) {
			mc().player.getInventory().setSelectedSlot(bestSlot);
		}
	}

	private void onRender3D(Render3DEvent event) {
		RenderUtil.beginLines(event.getContext());
		if (tree != null) {
			RenderUtil.addBox(new AABB(tree.getStump()).deflate(1.0 / 16.0), 0xFF00FF00);
			for (BlockPos log : tree.getLogs()) {
				RenderUtil.addBox(new AABB(log).deflate(1.0 / 16.0), 0x8000FF00);
			}
		}
		if (currentBlock != null) {
			RenderUtil.addBox(new AABB(currentBlock), 0xFFFFAA00);
		}
		for (int i = pathIndex; i < path.size(); i++) {
			RenderUtil.addBox(new AABB(path.get(i)).deflate(0.2), 0x804CFF66);
		}
		RenderUtil.endLines();
	}
}
