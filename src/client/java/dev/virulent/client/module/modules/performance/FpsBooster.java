package dev.virulent.client.module.modules.performance;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;
import org.lwjgl.glfw.GLFW;

public final class FpsBooster extends Module {
	private static FpsBooster instance;

	private final BooleanSetting noClouds = addSetting(new BooleanSetting("No Clouds", true));
	private final BooleanSetting minimalParticles = addSetting(new BooleanSetting("Minimal Particles", true));
	private final BooleanSetting noEntityShadows = addSetting(new BooleanSetting("No Entity Shadows", true));
	private final BooleanSetting noVignette = addSetting(new BooleanSetting("No Vignette", true));
	private final BooleanSetting lowerEntityDistance = addSetting(new BooleanSetting("Lower Entity Distance", true));
	private final NumberSetting entityDistance = addSetting(new NumberSetting("Entity Distance", 0.5, 0.1, 1.0, 0.05));
	private final BooleanSetting noWeather = addSetting(new BooleanSetting("No Weather", true));
	private final BooleanSetting noBreakParticles = addSetting(new BooleanSetting("No Break Particles", true));
	private final BooleanSetting noTotemAnimation = addSetting(new BooleanSetting("No Totem Animation", true));
	private final BooleanSetting noPumpkinOverlay = addSetting(new BooleanSetting("No Pumpkin Overlay", true));

	private boolean hasSnapshot;
	private CloudStatus savedClouds;
	private ParticleStatus savedParticles;
	private boolean savedEntityShadows;
	private boolean savedVignette;
	private double savedEntityDistance;

	public FpsBooster() {
		super("FPSBooster", "Lowers expensive graphics options and skips heavy effects for more FPS.", Category.PERFORMANCE, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;

		noClouds.onChange(v -> applyOptions());
		minimalParticles.onChange(v -> applyOptions());
		noEntityShadows.onChange(v -> applyOptions());
		noVignette.onChange(v -> applyOptions());
		lowerEntityDistance.onChange(v -> applyOptions());
		entityDistance.onChange(v -> applyOptions());
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static boolean hideWeather() {
		return isActive() && instance.noWeather.getValue();
	}

	public static boolean hideBreakParticles() {
		return isActive() && instance.noBreakParticles.getValue();
	}

	public static boolean hideTotemAnimation() {
		return isActive() && instance.noTotemAnimation.getValue();
	}

	public static boolean hidePumpkinOverlay() {
		return isActive() && instance.noPumpkinOverlay.getValue();
	}

	@Override
	protected void onEnable() {
		snapshotOptions();
		applyOptions();
	}

	@Override
	protected void onDisable() {
		restoreOptions();
	}

	private void snapshotOptions() {
		Options options = mc().options;
		if (options == null) {
			hasSnapshot = false;
			return;
		}

		savedClouds = options.cloudStatus().get();
		savedParticles = options.particles().get();
		savedEntityShadows = options.entityShadows().get();
		savedVignette = options.vignette().get();
		savedEntityDistance = options.entityDistanceScaling().get();
		hasSnapshot = true;
	}

	private void applyOptions() {
		if (!isEnabled() || !hasSnapshot) {
			return;
		}

		Options options = mc().options;
		if (options == null) {
			return;
		}

		options.cloudStatus().set(noClouds.getValue() ? CloudStatus.OFF : savedClouds);
		options.particles().set(minimalParticles.getValue() ? ParticleStatus.MINIMAL : savedParticles);
		options.entityShadows().set(noEntityShadows.getValue() ? false : savedEntityShadows);
		options.vignette().set(noVignette.getValue() ? false : savedVignette);
		options.entityDistanceScaling().set(
			lowerEntityDistance.getValue() ? entityDistance.getValue() : savedEntityDistance
		);
	}

	private void restoreOptions() {
		if (!hasSnapshot) {
			return;
		}

		Options options = mc().options;
		if (options != null) {
			options.cloudStatus().set(savedClouds);
			options.particles().set(savedParticles);
			options.entityShadows().set(savedEntityShadows);
			options.vignette().set(savedVignette);
			options.entityDistanceScaling().set(savedEntityDistance);
		}
		hasSnapshot = false;
	}
}
