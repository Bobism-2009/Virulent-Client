package dev.virulent.client.module.modules.player;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import org.lwjgl.glfw.GLFW;

public final class NoInteract extends Module {
	public NoInteract() {
		super("NoInteract", "Prevents interacting with blocks and entities.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().options != null) {
			mc().options.keyUse.setDown(false);
		}
	}
}
