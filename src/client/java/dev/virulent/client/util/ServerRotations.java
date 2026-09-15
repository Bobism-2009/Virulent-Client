package dev.virulent.client.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class ServerRotations {
	private static float yaw;
	private static float pitch;
	private static boolean tracking;

	private ServerRotations() {
	}

	public static void onOutgoing(Packet<?> packet) {
		if (packet instanceof ServerboundMovePlayerPacket move && move.hasRotation()) {
			yaw = move.getYRot(yaw);
			pitch = move.getXRot(pitch);
			tracking = true;
		}
	}

	/**
	 * Drops any rotation state carried over from a previous connection. Without this the
	 * last server-side yaw/pitch (and the tracking latch) survive into the next session,
	 * so consumers like HandView would render against a stale rotation until the first
	 * move packet of the new server arrives.
	 */
	public static void reset() {
		yaw = 0.0f;
		pitch = 0.0f;
		tracking = false;
	}

	public static boolean isTracking() {
		return tracking;
	}

	public static float getYaw() {
		return yaw;
	}

	public static float getPitch() {
		return pitch;
	}
}
