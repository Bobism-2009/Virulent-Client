package dev.virulent.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CombatUtil {
	private CombatUtil() {
	}

	public static float wrapDegrees(float degrees) {
		float wrapped = degrees % 360.0f;
		if (wrapped >= 180.0f) {
			wrapped -= 360.0f;
		}
		if (wrapped < -180.0f) {
			wrapped += 360.0f;
		}
		return wrapped;
	}

	public static float[] getRotations(Vec3 from, Vec3 to) {
		Vec3 diff = to.subtract(from);
		double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, horizontal));
		return new float[] {wrapDegrees(yaw), Math.clamp(pitch, -90.0f, 90.0f)};
	}

	public static Vec3 getTargetPoint(Entity entity) {
		return entity.getBoundingBox().getCenter();
	}

	public static float[] getRotationsToEntity(LocalPlayer player, Entity entity) {
		return getRotations(player.getEyePosition(), getTargetPoint(entity));
	}

	public static double getAngleTo(LocalPlayer player, Entity entity) {
		float[] needed = getRotationsToEntity(player, entity);
		float yawDiff = Math.abs(wrapDegrees(needed[0] - player.getYRot()));
		float pitchDiff = Math.abs(needed[1] - player.getXRot());
		return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
	}

	public static boolean hasLineOfSight(LocalPlayer player, Entity target) {
		return player.hasLineOfSight(target);
	}

	public static float lerpAngle(float current, float target, float factor) {
		float diff = wrapDegrees(target - current);
		return current + diff * factor;
	}

	public static void applyRotations(LocalPlayer player, float yaw, float pitch) {
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yHeadRot = yaw;
	}
}
