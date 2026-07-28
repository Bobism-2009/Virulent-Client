package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import org.lwjgl.glfw.GLFW;

public final class AirJump extends Module {
	private boolean wasJumpDown;

	public AirJump() {
		super("AirJump", "Jump again while in the air.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	protected void onDisable() {
		wasJumpDown = false;
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().options == null) {
			return;
		}

		boolean jumpDown = mc().options.keyJump.isDown();
		if (!mc().player.onGround() && jumpDown && !wasJumpDown) {
			mc().player.jumpFromGround();
			mc().player.fallDistance = 0.0f;
		}

		wasJumpDown = jumpDown;
	}
}
