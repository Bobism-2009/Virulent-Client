package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class NoClip extends Module {
	private static NoClip instance;

	private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.0, 0.1, 100.0, 0.1));

	private boolean prevMayfly;
	private boolean prevFlying;
	private boolean prevNoPhysics;

	public NoClip() {
		super("NoClip", "Fly through blocks like a spectator.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
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
		prevMayfly = abilities.mayfly;
		prevFlying = abilities.flying;
		prevNoPhysics = mc().player.noPhysics;

		abilities.mayfly = true;
		abilities.flying = true;
		mc().player.noPhysics = true;
		mc().player.fallDistance = 0.0f;
	}

	@Override
	protected void onDisable() {
		if (mc().player == null) {
			return;
		}

		mc().player.noPhysics = prevNoPhysics || mc().player.isSpectator();

		if (mc().player.isCreative() || mc().player.isSpectator()) {
			return;
		}

		var abilities = mc().player.getAbilities();
		abilities.mayfly = prevMayfly;
		abilities.flying = prevFlying;
		abilities.setFlyingSpeed(0.05f);
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
		mc().player.noPhysics = true;
		mc().player.fallDistance = 0.0f;
		mc().player.setOnGround(false);
	}
}
