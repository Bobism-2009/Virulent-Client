package dev.virulent.client.module.modules.render;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import org.lwjgl.glfw.GLFW;

public final class NoFire extends Module {
	private static NoFire instance;

	public NoFire() {
		super("NoFire", "Hides the fire overlay when burning.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}
}
