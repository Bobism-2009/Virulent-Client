package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.lwjgl.glfw.GLFW;

public final class BoatFly extends Module {
	private final NumberSetting horizontal = addSetting(new NumberSetting("Horizontal", 1.2, 0.1, 10.0, 0.1));
	private final NumberSetting vertical = addSetting(new NumberSetting("Vertical", 0.6, 0.1, 5.0, 0.1));
	private final BooleanSetting hover = addSetting(new BooleanSetting("Hover", true));

	private Entity controlledBoat;

	public BoatFly() {
		super("BoatFly", "Fly while riding a boat.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	protected void onDisable() {
		restoreGravity();
		controlledBoat = null;
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null) {
			return;
		}

		Entity vehicle = mc().player.getVehicle();
		if (!(vehicle instanceof AbstractBoat)) {
			restoreGravity();
			controlledBoat = null;
			return;
		}

		if (controlledBoat != null && controlledBoat != vehicle) {
			controlledBoat.setNoGravity(false);
		}
		controlledBoat = vehicle;
		vehicle.setNoGravity(true);
		vehicle.fallDistance = 0.0f;
		mc().player.fallDistance = 0.0f;

		float yaw = mc().player.getYRot();
		double forward = 0.0;
		double strafe = 0.0;
		if (mc().player.input != null) {
			var move = mc().player.input.getMoveVector();
			forward = move.y;
			strafe = move.x;
		}

		double motionY = vehicle.getDeltaMovement().y;
		if (mc().options.keyJump.isDown()) {
			motionY = vertical.getValue();
		} else if (mc().options.keyShift.isDown()) {
			motionY = -vertical.getValue();
		} else if (hover.getValue()) {
			motionY = 0.0;
		}

		double magnitude = Math.hypot(forward, strafe);
		if (magnitude < 0.01) {
			vehicle.setDeltaMovement(0.0, motionY, 0.0);
			return;
		}

		forward /= magnitude;
		strafe /= magnitude;
		double speed = horizontal.getValue();
		double sin = Math.sin(Math.toRadians(yaw));
		double cos = Math.cos(Math.toRadians(yaw));
		vehicle.setDeltaMovement(
			(strafe * cos - forward * sin) * speed,
			motionY,
			(strafe * sin + forward * cos) * speed
		);
	}

	private void restoreGravity() {
		if (controlledBoat != null && controlledBoat.isAlive()) {
			controlledBoat.setNoGravity(false);
		}
	}
}
