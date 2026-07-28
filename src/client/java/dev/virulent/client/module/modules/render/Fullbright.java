package dev.virulent.client.module.modules.render;

import dev.virulent.client.mixin.GameRendererAccessor;
import dev.virulent.client.mixin.LightmapRenderStateExtractorAccessor;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class Fullbright extends Module {
	private static Fullbright instance;

	private final NumberSetting brightness = addSetting(new NumberSetting("Brightness", 100.0, 0.0, 100.0, 1.0));
	private int reloadCooldown;

	public Fullbright() {
		super("Fullbright", "Brightens the world, including night lighting.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
		brightness.onChange(value -> reloadCooldown = 8);
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/** 0..1 blend toward full light. */
	public static float brightnessFactor() {
		if (!isActive()) {
			return 0.0f;
		}
		return Mth.clamp(instance.brightness.getValue().floatValue() / 100.0f, 0.0f, 1.0f);
	}

	public static float lift(float original) {
		float factor = brightnessFactor();
		if (factor <= 0.0f) {
			return original;
		}
		return Mth.lerp(factor, original, 1.0f);
	}

	/**
	 * Boost packed lightmap coords. Uses the block-light axis so night sky tint
	 * cannot darken entities/items relative to fullbright terrain.
	 */
	public static int boostPackedLight(int packed) {
		float factor = brightnessFactor();
		if (factor <= 0.0f) {
			return packed;
		}

		int block = LightCoordsUtil.block(packed);
		int sky = LightCoordsUtil.sky(packed);
		int brightest = Math.max(block, sky);
		int boosted = Mth.lerpInt(factor, brightest, 15);
		int outSky = Mth.lerpInt(factor, sky, 0);
		return LightCoordsUtil.pack(boosted, outSky);
	}

	@Override
	protected void onEnable() {
		reloadCooldown = 0;
		markLightmapDirty();
		reloadChunks();
	}

	@Override
	protected void onDisable() {
		reloadCooldown = 0;
		markLightmapDirty();
		reloadChunks();
	}

	@Override
	public void onTick() {
		markLightmapDirty();

		if (reloadCooldown > 0) {
			reloadCooldown--;
			if (reloadCooldown == 0) {
				reloadChunks();
			}
		}
	}

	private void markLightmapDirty() {
		var client = mc();
		if (client == null || client.gameRenderer == null) {
			return;
		}
		var extractor = ((GameRendererAccessor) client.gameRenderer).virulent$getLightmapRenderStateExtractor();
		if (extractor instanceof LightmapRenderStateExtractorAccessor accessor) {
			accessor.virulent$setNeedsUpdate(true);
		}
	}

	private void reloadChunks() {
		var client = mc();
		if (client != null && client.levelRenderer != null) {
			client.levelRenderer.allChanged();
		}
	}
}
