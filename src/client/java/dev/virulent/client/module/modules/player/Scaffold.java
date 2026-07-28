package dev.virulent.client.module.modules.player;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.CombatUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class Scaffold extends Module {
	private final NumberSetting extend = addSetting(new NumberSetting("Extend", 0.0, 0.0, 4.0, 1.0));
	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));
	private final BooleanSetting tower = addSetting(new BooleanSetting("Tower", true));
	private final BooleanSetting rotate = addSetting(new BooleanSetting("Rotate", true));
	private final BooleanSetting swing = addSetting(new BooleanSetting("Swing", true));
	private final BooleanSetting autoSelect = addSetting(new BooleanSetting("Auto Select", true));
	private final BooleanSetting keepY = addSetting(new BooleanSetting("Keep Y", false));

	private int placeCooldown;
	private Integer lockedY;

	public Scaffold() {
		super("Scaffold", "Places blocks under your feet while moving.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	protected void onEnable() {
		placeCooldown = 0;
		lockedY = null;
	}

	@Override
	protected void onDisable() {
		placeCooldown = 0;
		lockedY = null;
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().gameMode == null) {
			return;
		}

		if (placeCooldown > 0) {
			placeCooldown--;
			return;
		}

		if (autoSelect.getValue() && !ensureBlockSelected()) {
			return;
		}
		if (!isScaffoldStack(mc().player.getMainHandItem())) {
			return;
		}

		boolean jumping = mc().options.keyJump.isDown();
		if (tower.getValue() && jumping && !mc().player.onGround()) {
			mc().player.setDeltaMovement(mc().player.getDeltaMovement().x, 0.42, mc().player.getDeltaMovement().z);
		}

		BlockPos feet = mc().player.blockPosition();
		if (keepY.getValue()) {
			if (lockedY == null || mc().player.onGround()) {
				lockedY = feet.getY();
			}
		} else {
			lockedY = null;
		}

		int placeY = lockedY != null ? lockedY - 1 : feet.getY() - 1;
		BlockPos below = new BlockPos(feet.getX(), placeY, feet.getZ());

		if (tryPlace(below)) {
			return;
		}

		int extendBlocks = extend.getValue().intValue();
		if (extendBlocks <= 0) {
			return;
		}

		Vec3 velocity = mc().player.getDeltaMovement();
		double speedSq = velocity.x * velocity.x + velocity.z * velocity.z;
		if (speedSq < 0.001 && !jumping) {
			return;
		}

		Direction moveDir = getMoveDirection();
		for (int i = 1; i <= extendBlocks; i++) {
			BlockPos ahead = below.relative(moveDir, i);
			if (tryPlace(ahead)) {
				return;
			}
		}
	}

	private boolean tryPlace(BlockPos placePos) {
		BlockState current = mc().level.getBlockState(placePos);
		if (!current.isAir() && !current.canBeReplaced()) {
			return false;
		}

		PlacementTarget target = findPlacementTarget(placePos);
		if (target == null) {
			return false;
		}

		Vec3 hitVec = Vec3.atCenterOf(target.support()).add(
			target.face().getStepX() * 0.5,
			target.face().getStepY() * 0.5,
			target.face().getStepZ() * 0.5
		);

		if (rotate.getValue()) {
			float[] rotations = CombatUtil.getRotations(mc().player.getEyePosition(), hitVec);
			CombatUtil.applyRotations(mc().player, rotations[0], rotations[1]);
		}

		BlockHitResult hit = new BlockHitResult(hitVec, target.face(), target.support(), false);
		var result = mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, hit);
		if (result.consumesAction()) {
			if (swing.getValue()) {
				mc().player.swing(InteractionHand.MAIN_HAND);
			}
			placeCooldown = delay.getValue().intValue();
			return true;
		}
		return false;
	}

	private PlacementTarget findPlacementTarget(BlockPos placePos) {
		BlockPos below = placePos.below();
		if (isSolidForPlace(below)) {
			return new PlacementTarget(below, Direction.UP);
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = placePos.relative(direction);
			if (isSolidForPlace(side)) {
				return new PlacementTarget(side, direction.getOpposite());
			}
		}

		BlockPos above = placePos.above();
		if (isSolidForPlace(above)) {
			return new PlacementTarget(above, Direction.DOWN);
		}

		BlockPos feetBelow = mc().player.blockPosition().below();
		if (isSolidForPlace(feetBelow)) {
			return new PlacementTarget(feetBelow, Direction.UP);
		}
		return null;
	}

	private Direction getMoveDirection() {
		float yaw = mc().player.getYRot();
		Vec3 look = Vec3.directionFromRotation(0.0f, yaw);
		Vec3 velocity = mc().player.getDeltaMovement();
		double vx = velocity.x;
		double vz = velocity.z;
		if (vx * vx + vz * vz > 0.001) {
			return Direction.getApproximateNearest((float) vx, 0.0f, (float) vz);
		}
		return Direction.getApproximateNearest((float) look.x, 0.0f, (float) look.z);
	}

	private boolean ensureBlockSelected() {
		if (isScaffoldStack(mc().player.getMainHandItem())) {
			return true;
		}
		int slot = findScaffoldSlot();
		if (slot == -1) {
			return false;
		}
		mc().player.getInventory().setSelectedSlot(slot);
		return isScaffoldStack(mc().player.getMainHandItem());
	}

	private int findScaffoldSlot() {
		for (int slot = 0; slot < 9; slot++) {
			if (isScaffoldStack(mc().player.getInventory().getItem(slot))) {
				return slot;
			}
		}
		return -1;
	}

	private boolean isScaffoldStack(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}

		Block block = blockItem.getBlock();
		BlockState state = block.defaultBlockState();
		if (block == Blocks.AIR || block instanceof TorchBlock || block instanceof FallingBlock) {
			return false;
		}
		if (block instanceof WallBlock
			|| block instanceof FenceBlock
			|| block instanceof SlabBlock
			|| block instanceof StairBlock
			|| state.is(BlockTags.WALLS)
			|| state.is(BlockTags.FENCES)
			|| state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.SLABS)
			|| state.is(BlockTags.STAIRS)
			|| state.is(BlockTags.DOORS)
			|| state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.BUTTONS)
			|| state.is(BlockTags.PRESSURE_PLATES)) {
			return false;
		}
		return state.isCollisionShapeFullBlock(mc().level, BlockPos.ZERO);
	}

	private boolean isSolidForPlace(BlockPos pos) {
		BlockState state = mc().level.getBlockState(pos);
		return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	private record PlacementTarget(BlockPos support, Direction face) {
	}
}
