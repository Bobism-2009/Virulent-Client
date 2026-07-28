package dev.virulent.client.module.modules.misc;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class Freecam extends Module {
	private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1));

	private double savedX;
	private double savedY;
	private double savedZ;
	private double camX;
	private double camY;
	private double camZ;
	private boolean wasFlying;
	private boolean previousMayFly;
	private boolean previousNoPhysics;

	public Freecam() {
		super("Freecam", "Detach camera and fly through blocks.", Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
	}

	public static boolean isActive() {
		Module module = VirulentClient.getInstance().getModuleManager().getModule("Freecam");
		return module instanceof Freecam freecam && freecam.isEnabled();
	}

	public double getCamX() {
		return camX;
	}

	public double getCamY() {
		return camY;
	}

	public double getCamZ() {
		return camZ;
	}

	@Override
	protected void onEnable() {
		if (mc().player == null) {
			return;
		}

		savedX = mc().player.getX();
		savedY = mc().player.getY();
		savedZ = mc().player.getZ();
		camX = savedX;
		camY = savedY;
		camZ = savedZ;

		wasFlying = mc().player.getAbilities().flying;
		previousMayFly = mc().player.getAbilities().mayfly;
		previousNoPhysics = mc().player.noPhysics;

		mc().player.getAbilities().mayfly = true;
		mc().player.getAbilities().flying = true;
		mc().player.noPhysics = true;
	}

	@Override
	protected void onDisable() {
		if (mc().player == null) {
			return;
		}

		mc().player.setPos(savedX, savedY, savedZ);
		mc().player.setDeltaMovement(Vec3.ZERO);
		mc().player.getAbilities().mayfly = previousMayFly;
		mc().player.getAbilities().flying = wasFlying;
		mc().player.noPhysics = previousNoPhysics;
		mc().player.fallDistance = 0.0f;
	}

	@Override
	public void onTick() {
		if (mc().player == null) {
			return;
		}

		mc().player.noPhysics = true;
		mc().player.getAbilities().flying = true;
		mc().player.setDeltaMovement(Vec3.ZERO);
		mc().player.fallDistance = 0.0f;
		mc().player.setPos(savedX, savedY, savedZ);

		double moveSpeed = speed.getValue() * 0.5;
		double forward = 0;
		double strafe = 0;
		double vertical = 0;

		if (mc().options.keyUp.isDown()) {
			forward++;
		}
		if (mc().options.keyDown.isDown()) {
			forward--;
		}
		if (mc().options.keyLeft.isDown()) {
			strafe++;
		}
		if (mc().options.keyRight.isDown()) {
			strafe--;
		}
		if (mc().options.keyJump.isDown()) {
			vertical += moveSpeed;
		}
		if (mc().options.keyShift.isDown()) {
			vertical -= moveSpeed;
		}

		float yaw = mc().player.getYRot();
		float pitch = mc().player.getXRot();
		double sin = Math.sin(Math.toRadians(yaw));
		double cos = Math.cos(Math.toRadians(yaw));
		double pitchFactor = Math.cos(Math.toRadians(pitch));

		camX += (strafe * cos - forward * sin) * pitchFactor * moveSpeed;
		camY += vertical + (-forward * Math.sin(Math.toRadians(pitch)) * moveSpeed);
		camZ += (strafe * sin + forward * cos) * pitchFactor * moveSpeed;
	}
}
