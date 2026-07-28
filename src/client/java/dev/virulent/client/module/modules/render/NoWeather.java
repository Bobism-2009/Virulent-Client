package dev.virulent.client.module.modules.render;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import org.lwjgl.glfw.GLFW;

public final class NoWeather extends Module {
	private static NoWeather instance;

	public NoWeather() {
		super("NoWeather", "Hides rain, snow, and thunder.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}
}
