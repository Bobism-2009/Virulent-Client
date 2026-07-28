package dev.virulent.client.module.modules.render;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import org.lwjgl.glfw.GLFW;

public final class NoHurtCam extends Module {
	private static NoHurtCam instance;

	public NoHurtCam() {
		super("NoHurtCam", "Disables the hurt camera shake.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}
}
