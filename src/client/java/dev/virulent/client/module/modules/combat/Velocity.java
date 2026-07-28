package dev.virulent.client.module.modules.combat;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class Velocity extends Module {
	private static Velocity instance;

	private final NumberSetting horizontal = addSetting(new NumberSetting("Horizontal", 0.0, 0.0, 1.0, 0.05));
	private final NumberSetting vertical = addSetting(new NumberSetting("Vertical", 0.0, 0.0, 1.0, 0.05));
	private final BooleanSetting entityPush = addSetting(new BooleanSetting("Entity Push", true));
	private final NumberSetting entityPushAmount = addSetting(new NumberSetting("Push Amount", 0.0, 0.0, 1.0, 0.05));

	public Velocity() {
		super("Velocity", "Reduces knockback and entity push.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static double horizontal() {
		return isActive() ? instance.horizontal.getValue() : 1.0;
	}

	public static double vertical() {
		return isActive() ? instance.vertical.getValue() : 1.0;
	}

	public static boolean cancelsKnockback() {
		return isActive() && instance.horizontal.getValue() == 0.0 && instance.vertical.getValue() == 0.0;
	}

	public static boolean reducesEntityPush() {
		return isActive() && instance.entityPush.getValue();
	}

	public static double entityPushScale() {
		return reducesEntityPush() ? instance.entityPushAmount.getValue() : 1.0;
	}
}
