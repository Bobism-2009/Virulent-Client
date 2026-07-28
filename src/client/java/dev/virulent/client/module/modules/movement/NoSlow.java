package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import org.lwjgl.glfw.GLFW;

public final class NoSlow extends Module {
	private static NoSlow instance;

	public NoSlow() {
		super("NoSlow", "Removes item-use movement slowdown.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}
}
