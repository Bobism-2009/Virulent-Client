package dev.virulent.client.util;

import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Projects world positions to GUI space using the same bobbed view the player sees.
 */
public final class WorldToScreen {
	private WorldToScreen() {
	}

	/**
	 * @return {@code [guiX, guiY]} or {@code null} if behind the camera
	 */
	public static float[] project(Vec3 world, float tickDelta) {
		float[] full = projectInternal(world, tickDelta);
		if (full == null || full[2] < 0.5f) {
			return null;
		}
		return new float[] {full[0], full[1]};
	}

	/**
	 * Projects to GUI space and clamps to the screen edge when off-screen / behind camera.
	 *
	 * @return {@code [guiX, guiY, onScreenFlag]} where onScreenFlag is 1 when inside the view frustum
	 */
	public static float[] projectClamped(Vec3 world, float tickDelta, float pad) {
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() == null) {
			return null;
		}
		float[] full = projectInternal(world, tickDelta);
		if (full == null) {
			return null;
		}

		float guiW = client.getWindow().getGuiScaledWidth();
		float guiH = client.getWindow().getGuiScaledHeight();
		float x = full[0];
		float y = full[1];
		boolean onScreen = full[2] >= 0.5f
			&& x >= pad && x <= guiW - pad
			&& y >= pad && y <= guiH - pad;

		if (!onScreen) {
			float cx = guiW * 0.5f;
			float cy = guiH * 0.5f;
			float dx = x - cx;
			float dy = y - cy;
			if (Math.abs(dx) < 0.001f && Math.abs(dy) < 0.001f) {
				dy = -1.0f;
			}
			float scaleX = dx == 0.0f ? Float.POSITIVE_INFINITY : (guiW * 0.5f - pad) / Math.abs(dx);
			float scaleY = dy == 0.0f ? Float.POSITIVE_INFINITY : (guiH * 0.5f - pad) / Math.abs(dy);
			float scale = Math.min(scaleX, scaleY);
			x = cx + dx * scale;
			y = cy + dy * scale;
		}

		return new float[] {x, y, onScreen ? 1.0f : 0.0f};
	}

	/** @return {@code [guiX, guiY, onScreenFlag]} or null */
	private static float[] projectInternal(Vec3 world, float tickDelta) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null || client.getWindow() == null) {
			return null;
		}

		Camera camera = client.gameRenderer.getMainCamera();
		Matrix4f viewRot = camera.getViewRotationMatrix(new Matrix4f());
		Matrix4f projView = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Matrix4f projection = new Matrix4f(projView).mul(new Matrix4f(viewRot).invert());
		Matrix4f worldToClip = new Matrix4f(projection).mul(viewBobMatrix(tickDelta)).mul(viewRot);

		Vec3 cameraPos = camera.position();
		Vector4f clip = new Vector4f(
			(float) (world.x - cameraPos.x),
			(float) (world.y - cameraPos.y),
			(float) (world.z - cameraPos.z),
			1.0f
		);
		worldToClip.transform(clip);
		if (clip.w == 0.0f) {
			return null;
		}

		boolean behind = clip.w < 0.0f;
		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		float ndcZ = clip.z / clip.w;
		if (behind) {
			ndcX = -ndcX;
			ndcY = -ndcY;
		}

		float guiW = client.getWindow().getGuiScaledWidth();
		float guiH = client.getWindow().getGuiScaledHeight();
		float guiX = (ndcX + 1.0f) * 0.5f * guiW;
		float guiY = (1.0f - ndcY) * 0.5f * guiH;
		boolean onScreen = !behind && ndcZ >= -1.0f && ndcZ <= 1.0f
			&& ndcX >= -1.0f && ndcX <= 1.0f
			&& ndcY >= -1.0f && ndcY <= 1.0f;
		return new float[] {guiX, guiY, onScreen ? 1.0f : 0.0f};
	}

	private static Matrix4f viewBobMatrix(float tickDelta) {
		Matrix4f bob = new Matrix4f().identity();
		Minecraft client = Minecraft.getInstance();
		if (client.options == null || !client.options.bobView().get()) {
			return bob;
		}
		if (!(client.getCameraEntity() instanceof AbstractClientPlayer player)) {
			return bob;
		}

		ClientAvatarState avatar = player.avatarState();
		float walk = avatar.getBackwardsInterpolatedWalkDistance(tickDelta);
		float bobAmt = avatar.getInterpolatedBob(tickDelta);
		float walkPi = walk * (float) Math.PI;
		float sin = Mth.sin(walkPi);
		float cos = Mth.cos(walkPi);

		bob.translate(sin * bobAmt * 0.5f, -Math.abs(cos * bobAmt), 0.0f);
		bob.rotate(Axis.ZP.rotationDegrees(sin * bobAmt * 3.0f));
		bob.rotate(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walkPi - 0.2f) * bobAmt) * 5.0f));
		return bob;
	}
}
