package dev.virulent.client.module.modules.player;

import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.BotMovement;
import dev.virulent.client.util.CombatUtil;
import dev.virulent.client.util.PathFinder;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.util.TreeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wurst-style tunneller: digs a sized tunnel, fills floor holes, places torches,
 * dodges liquids, pathfinds forward, and ESP-highlights work targets.
 */
public final class Tunneler extends Module {
	private static final int BREAK_TIMEOUT_TICKS = 120;
	private static final int PATH_RECOMPUTE_TICKS = 15;

	private final ModeSetting size = addSetting(new ModeSetting(
		"Tunnel Size",
		"3x3",
		"1x2", "1x3", "1x4", "1x5",
		"2x2", "2x3", "2x4", "2x5",
		"3x2", "3x3", "3x4", "3x5",
		"4x2", "4x3", "4x4", "4x5",
		"5x2", "5x3", "5x4", "5x5"
	));
	private final NumberSetting limit = addSetting(new NumberSetting("Limit", 0.0, 0.0, 1000.0, 1.0));
	private final BooleanSetting placeTorches = addSetting(new BooleanSetting("Place Torches", false));
	private final BooleanSetting autoTool = addSetting(new BooleanSetting("Auto Tool", true));
	private final BooleanSetting fillFloor = addSetting(new BooleanSetting("Fill Floor", true));
	private final BooleanSetting pathfinding = addSetting(new BooleanSetting("Pathfinding", true));
	private final BooleanSetting renderEsp = addSetting(new BooleanSetting("Render ESP", true));

	private final List<BlockPos> digBlocks = new ArrayList<>();
	private final List<BlockPos> floorHoles = new ArrayList<>();
	private final List<BlockPos> path = new ArrayList<>();
	private final Set<BlockPos> liquids = new HashSet<>();

	private BlockPos start;
	private BlockPos currentBlock;
	private BlockPos lastTorch;
	private BlockPos nextTorch;
	private BlockPos walkGoal;
	private Direction direction = Direction.NORTH;
	private int length;
	private int breakTicks;
	private int pathTicks;
	private int pathIndex;
	private int liquidDisableTimer;
	private Vec3 lastMovementPos;
	private int stuckTicks;

	public Tunneler() {
		super("Tunneler", "Automatically digs a tunnel with pathfinding and ESP.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render3DEvent.class, this::onRender3D);
	}

	@Override
	protected void onEnable() {
		if (mc().player == null) {
			setEnabled(false);
			return;
		}

		start = BlockPos.containing(mc().player.position());
		direction = mc().player.getDirection();
		length = 0;
		lastTorch = null;
		nextTorch = start;
		liquidDisableTimer = 60;
		resetBreak();
		path.clear();
		pathIndex = 0;
		liquids.clear();
	}

	@Override
	protected void onDisable() {
		releaseMovement();
		resetBreak();
		digBlocks.clear();
		floorHoles.clear();
		path.clear();
		liquids.clear();
		start = null;
		walkGoal = null;
		lastTorch = null;
		nextTorch = null;
	}

	private void resetBreak() {
		currentBlock = null;
		breakTicks = 0;
		mc().options.keyAttack.setDown(false);
		if (mc().gameMode != null) {
			mc().gameMode.stopDestroyBlock();
		}
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().gameMode == null || start == null) {
			return;
		}

		releaseKeysOnly();
		mc().options.keyAttack.setDown(false);

		if (!liquids.isEmpty() || scanLiquids()) {
			dodgeLiquid();
			return;
		}

		if (hasNearbyFallingBlocks()) {
			return;
		}

		if (fillFloor.getValue() && fillFloorHole()) {
			return;
		}

		if (placeTorches.getValue() && placeTorchIfNeeded()) {
			return;
		}

		BlockPos base = start.relative(direction, length);
		int distance = manhattan(mc().player.blockPosition(), base);
		TunnelSize tunnel = TunnelSize.from(size.getValue());

		if (distance <= tunnel.maxRange) {
			if (digTunnelSlice(base, tunnel)) {
				return;
			}
		}

		if (distance > 1) {
			walkTowardBase(base);
			return;
		}

		// Nothing to dig and we're at the face — advance.
		advanceLength();
	}

	private void advanceLength() {
		resetBreak();
		length++;
		if (limit.getValue() > 0 && length >= limit.getValue().intValue()) {
			setEnabled(false);
		}
	}

	private boolean digTunnelSlice(BlockPos base, TunnelSize tunnel) {
		BlockPos player = mc().player.blockPosition();
		BlockPos from = offset(player, tunnel.from);
		BlockPos to = offset(base, tunnel.to);

		digBlocks.clear();
		for (BlockPos pos : getAllInBox(from, to)) {
			if (!canMine(pos)) {
				continue;
			}
			if ((pos.equals(nextTorch) || pos.equals(lastTorch)) && isTorch(pos)) {
				continue;
			}
			digBlocks.add(pos);
		}

		if (digBlocks.isEmpty()) {
			if (distanceToBase() <= 1) {
				advanceLength();
			} else {
				walkTowardBase(base);
			}
			return true;
		}

		currentBlock = digBlocks.getFirst();
		if (autoTool.getValue()) {
			selectBestTool(currentBlock);
		}
		breakBlock(currentBlock);
		return true;
	}

	private int distanceToBase() {
		return manhattan(mc().player.blockPosition(), start.relative(direction, length));
	}

	private boolean fillFloorHole() {
		floorHoles.clear();
		TunnelSize tunnel = TunnelSize.from(size.getValue());
		BlockPos player = mc().player.blockPosition();
		BlockPos from = offsetFloor(player, tunnel.from);
		BlockPos to = offsetFloor(player, tunnel.to);

		for (BlockPos pos : getAllInBox(from, to)) {
			BlockState state = mc().level.getBlockState(pos);
			if (!state.isCollisionShapeFullBlock(mc().level, pos)) {
				floorHoles.add(pos.immutable());
			}
		}

		if (floorHoles.isEmpty()) {
			return false;
		}

		mc().options.keyShift.setDown(true);
		Vec3 velocity = mc().player.getDeltaMovement();
		mc().player.setDeltaMovement(0.0, velocity.y, 0.0);

		Vec3 eyes = mc().player.getEyePosition().add(-0.5, -0.5, -0.5);
		BlockPos pos = floorHoles.stream()
			.max(Comparator.comparingDouble(p -> eyes.distanceToSqr(Vec3.atLowerCornerOf(p))))
			.orElse(null);
		if (pos == null) {
			return true;
		}

		BlockState state = mc().level.getBlockState(pos);
		if (state.canBeReplaced() || state.isAir()) {
			if (!equipSolidBlock(pos)) {
				return true;
			}
			placeBlock(pos);
		} else {
			if (autoTool.getValue()) {
				selectBestTool(pos);
			}
			breakBlock(pos);
		}
		return true;
	}

	private boolean placeTorchIfNeeded() {
		if (nextTorch != null && isTorch(nextTorch)) {
			lastTorch = nextTorch;
		}

		TunnelSize tunnel = TunnelSize.from(size.getValue());
		if (lastTorch != null) {
			nextTorch = lastTorch.relative(direction, tunnel.torchDistance);
		} else if (nextTorch == null) {
			nextTorch = BlockPos.containing(mc().player.position());
		}

		if (manhattan(mc().player.blockPosition(), nextTorch) > 4) {
			return false;
		}

		BlockState state = mc().level.getBlockState(nextTorch);
		if (!state.canBeReplaced() && !state.isAir()) {
			return false;
		}
		if (!Blocks.TORCH.defaultBlockState().canSurvive(mc().level, nextTorch)) {
			return false;
		}
		if (!equipTorch()) {
			return false;
		}

		mc().options.keyShift.setDown(true);
		placeBlock(nextTorch);
		return true;
	}

	private boolean scanLiquids() {
		liquids.clear();
		TunnelSize tunnel = TunnelSize.from(size.getValue());
		BlockPos base = start.relative(direction, length);
		BlockPos from = offset(base, tunnel.from);
		BlockPos to = offset(base, tunnel.to);
		int maxY = Math.max(from.getY(), to.getY());

		for (BlockPos pos : getAllInBox(from, to)) {
			int maxOffset = Math.min(tunnel.maxRange, length);
			for (int i = 0; i <= maxOffset; i++) {
				BlockPos check = pos.relative(direction.getOpposite(), i);
				if (!mc().level.getBlockState(check).getFluidState().isEmpty()) {
					liquids.add(check.immutable());
				}
			}

			if (!mc().level.getBlockState(pos).isCollisionShapeFullBlock(mc().level, pos)) {
				BlockPos ahead = pos.relative(direction);
				if (!mc().level.getBlockState(ahead).getFluidState().isEmpty()) {
					liquids.add(ahead.immutable());
				}
			}

			if (pos.getY() == maxY) {
				BlockPos above = pos.above();
				if (!mc().level.getBlockState(above).getFluidState().isEmpty()) {
					liquids.add(above.immutable());
				}
			}
		}
		return !liquids.isEmpty();
	}

	private void dodgeLiquid() {
		BlockPos player = mc().player.blockPosition();
		Vec3 diff = Vec3.atLowerCornerOf(player.subtract(start));
		Vec3 dir = Vec3.atLowerCornerOf(direction.getUnitVec3i());
		int along = (int) Math.max(0, diff.dot(dir));

		BlockPos retreat = start.relative(direction, Math.max(0, Math.min(along, length) - 8));
		if (player.distManhattan(retreat) > 0) {
			walkToward(retreat);
			return;
		}

		if (liquidDisableTimer > 0) {
			liquidDisableTimer--;
			return;
		}
		setEnabled(false);
	}

	private boolean hasNearbyFallingBlocks() {
		AABB box = mc().player.getBoundingBox().inflate(6.0);
		return !mc().level.getEntitiesOfClass(FallingBlockEntity.class, box, FallingBlockEntity::isAlive).isEmpty();
	}

	private void walkTowardBase(BlockPos base) {
		walkGoal = base;
		pathTicks++;
		if (pathfinding.getValue() && (path.isEmpty() || pathTicks >= PATH_RECOMPUTE_TICKS || pathIndex >= path.size())) {
			recomputePath(base);
		}

		if (pathfinding.getValue() && !path.isEmpty()) {
			while (pathIndex < path.size() - 1) {
				BlockPos node = path.get(pathIndex);
				if (mc().player.blockPosition().distManhattan(node) <= 1) {
					pathIndex++;
				} else {
					break;
				}
			}
			walkToward(path.get(Math.min(pathIndex, path.size() - 1)));
			return;
		}

		walkToward(base);
	}

	private void recomputePath(BlockPos goal) {
		pathTicks = 0;
		pathIndex = 0;
		path.clear();
		path.addAll(PathFinder.findPath(mc().level, mc().player.blockPosition(), goal, 2000));
	}

	private void walkToward(BlockPos target) {
		Vec3 goal = TreeUtil.horizontalCenter(target, mc().player.getY());
		Vec3 playerPos = mc().player.position();
		Vec3 diff = goal.subtract(playerPos);
		float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
		CombatUtil.applyRotations(mc().player, yaw, 0.0f);

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

		mc().options.keyUp.setDown(true);
		mc().options.keySprint.setDown(true);
		boolean jump = stuckTicks >= 12;
		if (jump) {
			mc().options.keyJump.setDown(true);
		}
		BotMovement.set(true, jump, true);
	}

	private void breakBlock(BlockPos pos) {
		Vec3 eye = mc().player.getEyePosition();
		float[] rotations = TreeUtil.getRotationsToBlock(eye, pos);
		CombatUtil.applyRotations(mc().player, rotations[0], rotations[1]);
		Direction face = TreeUtil.getClosestFace(pos, eye);

		mc().options.keyAttack.setDown(true);

		if (currentBlock == null || !currentBlock.equals(pos)) {
			currentBlock = pos;
			breakTicks = 0;
		} else {
			breakTicks++;
			if (breakTicks >= BREAK_TIMEOUT_TICKS) {
				resetBreak();
				return;
			}
		}
		if (mc().gameMode.continueDestroyBlock(pos, face)) {
			mc().player.swing(InteractionHand.MAIN_HAND);
		}
	}

	private void placeBlock(BlockPos placePos) {
		PlacementTarget target = findPlacementTarget(placePos);
		if (target == null) {
			return;
		}

		Vec3 hitVec = Vec3.atCenterOf(target.support()).add(
			target.face().getStepX() * 0.5,
			target.face().getStepY() * 0.5,
			target.face().getStepZ() * 0.5
		);
		float[] rotations = TreeUtil.getRotationsToBlock(mc().player.getEyePosition(), target.support());
		CombatUtil.applyRotations(mc().player, rotations[0], rotations[1]);

		BlockHitResult hit = new BlockHitResult(hitVec, target.face(), target.support(), false);
		var result = mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, hit);
		if (result.consumesAction()) {
			mc().player.swing(InteractionHand.MAIN_HAND);
		}
	}

	private record PlacementTarget(BlockPos support, Direction face) {
	}

	private PlacementTarget findPlacementTarget(BlockPos placePos) {
		for (Direction face : Direction.values()) {
			BlockPos support = placePos.relative(face);
			BlockState state = mc().level.getBlockState(support);
			if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty()) {
				return new PlacementTarget(support, face.getOpposite());
			}
		}
		return null;
	}

	private void selectBestTool(BlockPos pos) {
		BlockState state = mc().level.getBlockState(pos);
		int bestSlot = -1;
		float bestSpeed = 1.0f;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc().player.getInventory().getItem(slot);
			float speed = stack.getDestroySpeed(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = slot;
			}
		}
		if (bestSlot != -1) {
			mc().player.getInventory().setSelectedSlot(bestSlot);
		}
	}

	private boolean equipTorch() {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc().player.getInventory().getItem(slot);
			if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
				continue;
			}
			if (blockItem.getBlock() instanceof TorchBlock) {
				mc().player.getInventory().setSelectedSlot(slot);
				return true;
			}
		}
		return false;
	}

	private boolean equipSolidBlock(BlockPos placePos) {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc().player.getInventory().getItem(slot);
			if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
				continue;
			}
			Block block = blockItem.getBlock();
			BlockState state = block.defaultBlockState();
			if (!state.isCollisionShapeFullBlock(mc().level, BlockPos.ZERO)) {
				continue;
			}
			if (block instanceof FallingBlock && FallingBlock.isFree(mc().level.getBlockState(placePos.below()))) {
				continue;
			}
			if (block instanceof TorchBlock || TreeUtil.isLog(state) || TreeUtil.isLeaves(state)) {
				continue;
			}
			mc().player.getInventory().setSelectedSlot(slot);
			return true;
		}
		return false;
	}

	private boolean canMine(BlockPos pos) {
		BlockState state = mc().level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(mc().level, pos) < 0) {
			return false;
		}
		return !state.getCollisionShape(mc().level, pos).isEmpty() || !state.getFluidState().isEmpty();
	}

	private boolean isTorch(BlockPos pos) {
		return mc().level.getBlockState(pos).getBlock() instanceof TorchBlock;
	}

	private BlockPos offset(BlockPos pos, Vec3i vec) {
		return pos.relative(direction.getCounterClockWise(), vec.getX()).above(vec.getY());
	}

	private BlockPos offsetFloor(BlockPos pos, Vec3i vec) {
		return pos.relative(direction.getCounterClockWise(), vec.getX()).below();
	}

	private List<BlockPos> getAllInBox(BlockPos from, BlockPos to) {
		List<BlockPos> blocks = new ArrayList<>();
		Direction front = direction;
		Direction left = front.getCounterClockWise();

		int fromFront = from.getX() * front.getStepX() + from.getZ() * front.getStepZ();
		int toFront = to.getX() * front.getStepX() + to.getZ() * front.getStepZ();
		int fromLeft = from.getX() * left.getStepX() + from.getZ() * left.getStepZ();
		int toLeft = to.getX() * left.getStepX() + to.getZ() * left.getStepZ();

		int minFront = Math.min(fromFront, toFront);
		int maxFront = Math.max(fromFront, toFront);
		int minY = Math.min(from.getY(), to.getY());
		int maxY = Math.max(from.getY(), to.getY());
		int minLeft = Math.min(fromLeft, toLeft);
		int maxLeft = Math.max(fromLeft, toLeft);

		for (int f = minFront; f <= maxFront; f++) {
			for (int y = maxY; y >= minY; y--) {
				for (int l = maxLeft; l >= minLeft; l--) {
					int x = f * front.getStepX() + l * left.getStepX();
					int z = f * front.getStepZ() + l * left.getStepZ();
					blocks.add(new BlockPos(x, y, z));
				}
			}
		}
		return blocks;
	}

	private static int manhattan(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
	}

	private void releaseKeysOnly() {
		BotMovement.clear();
		mc().options.keyUp.setDown(false);
		mc().options.keyDown.setDown(false);
		mc().options.keyLeft.setDown(false);
		mc().options.keyRight.setDown(false);
		mc().options.keyJump.setDown(false);
		mc().options.keyShift.setDown(false);
		mc().options.keySprint.setDown(false);
	}

	private void releaseMovement() {
		releaseKeysOnly();
		lastMovementPos = null;
		stuckTicks = 0;
	}

	private void onRender3D(Render3DEvent event) {
		if (!renderEsp.getValue() || mc().player == null || start == null) {
			return;
		}

		RenderUtil.beginLines(event.getContext());

		// Cyan: start + dig progress arrow
		RenderUtil.addBox(new AABB(start).deflate(0.25), 0xFF00FFFF);
		BlockPos tip = start.relative(direction, Math.max(1, length));
		RenderUtil.addLine(Vec3.atCenterOf(start), Vec3.atCenterOf(tip), 0xFF00FFFF);

		// Green: blocks to dig
		for (BlockPos pos : digBlocks) {
			RenderUtil.addBox(new AABB(pos).deflate(0.05), 0xFF39FF14);
		}
		if (currentBlock != null) {
			RenderUtil.addFilledBox(new AABB(currentBlock).deflate(0.02), 0x8839FF14);
			RenderUtil.addBox(new AABB(currentBlock).inflate(0.02), 0xFFFF2222);
		}

		// Yellow: floor holes
		for (BlockPos pos : floorHoles) {
			RenderUtil.addBox(new AABB(pos).deflate(0.05), 0xFFFFAA00);
		}

		// Yellow arrow: next torch
		if (placeTorches.getValue() && nextTorch != null) {
			Vec3 base = Vec3.atBottomCenterOf(nextTorch);
			RenderUtil.addLine(base, base.add(0, 0.7, 0), 0xFFFFFF00);
			RenderUtil.addBox(new AABB(nextTorch).deflate(0.2), 0xFFFFFF00);
		}

		// Red: liquids
		for (BlockPos pos : liquids) {
			RenderUtil.addBox(new AABB(pos).deflate(0.05), 0xFFFF2222);
			RenderUtil.addFilledBox(new AABB(pos).deflate(0.1), 0x55FF2222);
		}

		// Cyan path
		if (path.size() >= 2) {
			for (int i = 0; i < path.size() - 1; i++) {
				RenderUtil.addLine(
					Vec3.atBottomCenterOf(path.get(i)).add(0, 0.1, 0),
					Vec3.atBottomCenterOf(path.get(i + 1)).add(0, 0.1, 0),
					0xFF00E5FF
				);
			}
		}
		if (walkGoal != null) {
			RenderUtil.addBox(new AABB(walkGoal).deflate(0.2), 0xFF00E5FF);
		}

		RenderUtil.endLines();
	}

	private enum TunnelSize {
		SIZE_1X2("1x2", new Vec3i(0, 1, 0), new Vec3i(0, 0, 0), 4, 13),
		SIZE_1X3("1x3", new Vec3i(0, 2, 0), new Vec3i(0, 0, 0), 4, 13),
		SIZE_1X4("1x4", new Vec3i(0, 3, 0), new Vec3i(0, 0, 0), 4, 13),
		SIZE_1X5("1x5", new Vec3i(0, 4, 0), new Vec3i(0, 0, 0), 3, 13),
		SIZE_2X2("2x2", new Vec3i(1, 1, 0), new Vec3i(0, 0, 0), 4, 11),
		SIZE_2X3("2x3", new Vec3i(1, 2, 0), new Vec3i(0, 0, 0), 4, 11),
		SIZE_2X4("2x4", new Vec3i(1, 3, 0), new Vec3i(0, 0, 0), 4, 11),
		SIZE_2X5("2x5", new Vec3i(1, 4, 0), new Vec3i(0, 0, 0), 3, 11),
		SIZE_3X2("3x2", new Vec3i(1, 1, 0), new Vec3i(-1, 0, 0), 4, 11),
		SIZE_3X3("3x3", new Vec3i(1, 2, 0), new Vec3i(-1, 0, 0), 4, 11),
		SIZE_3X4("3x4", new Vec3i(1, 3, 0), new Vec3i(-1, 0, 0), 4, 11),
		SIZE_3X5("3x5", new Vec3i(1, 4, 0), new Vec3i(-1, 0, 0), 3, 11),
		SIZE_4X2("4x2", new Vec3i(2, 1, 0), new Vec3i(-1, 0, 0), 4, 9),
		SIZE_4X3("4x3", new Vec3i(2, 2, 0), new Vec3i(-1, 0, 0), 4, 9),
		SIZE_4X4("4x4", new Vec3i(2, 3, 0), new Vec3i(-1, 0, 0), 4, 9),
		SIZE_4X5("4x5", new Vec3i(2, 4, 0), new Vec3i(-1, 0, 0), 3, 9),
		SIZE_5X2("5x2", new Vec3i(2, 1, 0), new Vec3i(-2, 0, 0), 4, 9),
		SIZE_5X3("5x3", new Vec3i(2, 2, 0), new Vec3i(-2, 0, 0), 4, 9),
		SIZE_5X4("5x4", new Vec3i(2, 3, 0), new Vec3i(-2, 0, 0), 4, 9),
		SIZE_5X5("5x5", new Vec3i(2, 4, 0), new Vec3i(-2, 0, 0), 3, 9);

		private final String label;
		private final Vec3i from;
		private final Vec3i to;
		private final int maxRange;
		private final int torchDistance;

		TunnelSize(String label, Vec3i from, Vec3i to, int maxRange, int torchDistance) {
			this.label = label;
			this.from = from;
			this.to = to;
			this.maxRange = maxRange;
			this.torchDistance = torchDistance;
		}

		static TunnelSize from(String label) {
			for (TunnelSize value : values()) {
				if (value.label.equals(label)) {
					return value;
				}
			}
			return SIZE_3X3;
		}
	}
}
