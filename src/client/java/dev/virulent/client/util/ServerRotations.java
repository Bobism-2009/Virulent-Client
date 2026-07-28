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
