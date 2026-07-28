package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.ModeSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.lwjgl.glfw.GLFW;

public final class Jesus extends Module {
	private static Jesus instance;

	private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Bounce", "Bounce", "Solid"));

	public Jesus() {
		super("Jesus", "Walk on water and lava.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean shouldSolidify(BlockState state, CollisionContext context) {
		if (instance == null || !instance.isEnabled() || !instance.isSolidMode()) {
			return false;
		}
		if (!(state.getBlock() instanceof LiquidBlock)) {
			return false;
		}
		if (!(context instanceof EntityCollisionContext entityContext)) {
			return false;
		}
		if (!(entityContext.getEntity() instanceof LocalPlayer player)) {
			return false;
		}
		// Sneak to sink through the solid liquid.
		return !player.isShiftKeyDown();
	}

	private boolean isSolidMode() {
		return mode.getValue().equals("Solid");
	}

	@Override
	public void onTick() {
		if (mc().player == null || isSolidMode()) {
			return;
		}

		if (mc().player.isInWater() || mc().player.isInLava()) {
			if (mc().player.getDeltaMovement().y < 0.0) {
				mc().player.setDeltaMovement(
					mc().player.getDeltaMovement().x,
					0.1,
					mc().player.getDeltaMovement().z
				);
			}
			mc().player.setOnGround(true);
		}
	}
}
