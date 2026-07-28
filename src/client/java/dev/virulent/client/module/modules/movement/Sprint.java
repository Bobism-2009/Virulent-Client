package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import org.lwjgl.glfw.GLFW;

public final class Sprint extends Module {
	public Sprint() {
		super("Sprint", "Automatically sprints while moving.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player == null) {
			return;
		}

		if (mc().player.input != null && (mc().player.input.hasForwardImpulse() || mc().player.input.getMoveVector().x != 0)) {
			mc().player.setSprinting(true);
		}
	}
}
