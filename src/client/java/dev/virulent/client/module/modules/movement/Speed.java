package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class Speed extends Module {
	private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Strafe", "Strafe", "Bhop"));
	private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.2, 1.0, 3.0, 0.1));

	public Speed() {
		super("Speed", "Move faster on the ground.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().player.input == null) {
			return;
		}

		if (!mc().player.onGround() || !mc().player.input.hasForwardImpulse()) {
			return;
		}

		if (mode.getValue().equals("Bhop") && mc().options.keyJump.isDown()) {
			mc().player.jumpFromGround();
		}

		float yaw = mc().player.getYRot();
		double forward = mc().player.input.getMoveVector().y;
		double strafe = mc().player.input.getMoveVector().x;
		double magnitude = Math.hypot(forward, strafe);
		if (magnitude < 0.01) {
			return;
		}

		forward /= magnitude;
		strafe /= magnitude;
		double multiplier = speed.getValue() * 0.1;
		double sin = Math.sin(Math.toRadians(yaw));
		double cos = Math.cos(Math.toRadians(yaw));
		Vec3 movement = new Vec3(
			(strafe * cos - forward * sin) * multiplier,
			mc().player.getDeltaMovement().y,
			(strafe * sin + forward * cos) * multiplier
		);
		mc().player.setDeltaMovement(movement);
	}
}
