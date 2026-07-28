package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class Step extends Module {
	private static Step instance;

	private final NumberSetting height = addSetting(new NumberSetting("Height", 1.0, 0.6, 2.5, 0.1));

	public Step() {
		super("Step", "Step up full blocks instantly.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static float getHeight() {
		return instance.height.getValue().floatValue();
	}
}
