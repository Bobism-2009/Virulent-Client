package dev.virulent.client.module.modules.misc;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class Zoom extends Module {
	private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Hold", "Hold", "Toggle"));
	private final NumberSetting level = addSetting(new NumberSetting("Level", 3.0, 1.5, 10.0, 0.5));

	private boolean zooming;
	private int previousFov;

	public Zoom() {
		super("Zoom", "Hold to zoom your FOV (bind a key).", Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
	}

	public boolean isHoldMode() {
		return "Hold".equals(mode.getValue());
	}

	public boolean isZooming() {
		return zooming;
	}

	public void startZoom() {
		if (mc().options == null || zooming) {
			return;
		}

		if (!zooming) {
			previousFov = mc().options.fov().get();
		}
		zooming = true;
		applyZoom();
	}

	public void stopZoom() {
		if (mc().options == null || !zooming) {
			return;
		}

		zooming = false;
		mc().options.fov().set(previousFov);
	}

	@Override
	public boolean isEnabled() {
		if (isHoldMode()) {
			return zooming;
		}
		return super.isEnabled();
	}

	@Override
	protected void onEnable() {
		if (!isHoldMode()) {
			startZoom();
		}
	}

	@Override
	protected void onDisable() {
		stopZoom();
	}

	@Override
	public void toggle() {
		if (isHoldMode()) {
			return;
		}
		super.toggle();
	}

	@Override
	public void onTick() {
		if (mc().options == null) {
			return;
		}

		if (isHoldMode()) {
			if (zooming) {
				applyZoom();
			}
			return;
		}

		if (super.isEnabled()) {
			applyZoom();
		}
	}

	private void applyZoom() {
		mc().options.fov().set((int) (previousFov / level.getValue()));
	}
}
