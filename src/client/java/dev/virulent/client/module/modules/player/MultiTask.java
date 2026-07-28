package dev.virulent.client.module.modules.player;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import org.lwjgl.glfw.GLFW;

public final class MultiTask extends Module {
	private static MultiTask instance;

	private final BooleanSetting attackingEntities = addSetting(new BooleanSetting("Attacking Entities", true));

	public MultiTask() {
		super("MultiTask", "Lets you use items and attack or break at the same time.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static boolean attackingEntities() {
		return isActive() && instance.attackingEntities.getValue();
	}
}
