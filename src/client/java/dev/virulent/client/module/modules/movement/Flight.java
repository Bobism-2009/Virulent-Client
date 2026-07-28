package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class Flight extends Module {
	private static Flight instance;

	private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.0, 0.1, 100.0, 0.1));

	public Flight() {
		super("Flight", "Allows you to fly in survival.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	@Override
	protected void onEnable() {
		if (mc().player == null) {
			return;
		}

		var abilities = mc().player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
	}

	@Override
	protected void onDisable() {
		if (mc().player == null || mc().player.isCreative() || mc().player.isSpectator()) {
			return;
		}

		var abilities = mc().player.getAbilities();
		abilities.mayfly = false;
		abilities.flying = false;
	}

	@Override
	public void onTick() {
		if (mc().player == null) {
			return;
		}

		var abilities = mc().player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
		abilities.setFlyingSpeed((float) (speed.getValue() * 0.05f));
		mc().player.fallDistance = 0.0f;
	}
}
