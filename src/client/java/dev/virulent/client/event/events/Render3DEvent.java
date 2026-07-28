package dev.virulent.client.event.events;

import dev.virulent.client.event.Event;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public final class Render3DEvent implements Event {
	private final LevelRenderContext context;

	public Render3DEvent(LevelRenderContext context) {
		this.context = context;
	}

	public LevelRenderContext getContext() {
		return context;
	}

	public CameraRenderState getCameraState() {
		if (context == null || context.levelState() == null) {
			return null;
		}
		return context.levelState().cameraRenderState;
	}

	public Vec3 getCameraPos() {
		CameraRenderState state = getCameraState();
		if (state != null && state.pos != null) {
			return state.pos;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer != null && client.gameRenderer.getMainCamera() != null) {
			return client.gameRenderer.getMainCamera().position();
		}
		if (client.player != null) {
			return client.player.getEyePosition();
		}
		return Vec3.ZERO;
	}
}
