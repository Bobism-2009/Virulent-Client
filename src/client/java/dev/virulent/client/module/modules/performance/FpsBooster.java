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

	private final BooleanSetting cappedRenderDistance = addSetting(new BooleanSetting("Cap Render Distance", true));
	private final NumberSetting renderDistance = addSetting(new NumberSetting("Render Distance", 8.0, 2.0, 32.0, 1.0));
	private final BooleanSetting cappedSimulationDistance = addSetting(new BooleanSetting("Cap Simulation Distance", true));
	private final NumberSetting simulationDistance = addSetting(new NumberSetting("Simulation Distance", 6.0, 2.0, 32.0, 1.0));

	private final BooleanSetting noBiomeBlend = addSetting(new BooleanSetting("No Biome Blend", true));
	private final BooleanSetting unlockedFps = addSetting(new BooleanSetting("Unlock FPS", true));
	private final NumberSetting framerateLimit = addSetting(new NumberSetting("Framerate Limit", 260.0, 30.0, 260.0, 5.0));
	private final BooleanSetting noFovEffects = addSetting(new BooleanSetting("No FOV Effects", true));
	private final BooleanSetting noScreenEffects = addSetting(new BooleanSetting("No Screen Effects", true));

	private final BooleanSetting noWeather = addSetting(new BooleanSetting("No Weather", true));
	private final BooleanSetting noBreakParticles = addSetting(new BooleanSetting("No Break Particles", true));
	private final BooleanSetting noAmbientParticles = addSetting(new BooleanSetting("No Ambient Particles", true));
	private final BooleanSetting noTotemAnimation = addSetting(new BooleanSetting("No Totem Animation", true));
	private final BooleanSetting noPumpkinOverlay = addSetting(new BooleanSetting("No Pumpkin Overlay", true));
	private final BooleanSetting noEnchantmentGlint = addSetting(new BooleanSetting("No Enchantment Glint", true));
	private final BooleanSetting cullDistantEntities = addSetting(new BooleanSetting("Cull Distant Entities", true));
	private final NumberSetting cullDistance = addSetting(new NumberSetting("Cull Distance", 64.0, 8.0, 256.0, 1.0));

	private boolean hasSnapshot;
	private CloudStatus savedClouds;
	private ParticleStatus savedParticles;
	private boolean savedEntityShadows;
	private boolean savedVignette;
	private double savedEntityDistance;
	private int savedRenderDistance;
	private int savedSimulationDistance;
	private int savedBiomeBlend;
	private int savedFramerateLimit;
	private double savedFovEffectScale;
	private double savedScreenEffectScale;

	public FpsBooster() {
		super("FPSBooster", "Lowers expensive graphics options and skips heavy effects for more FPS.", Category.PERFORMANCE, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;

		noClouds.onChange(v -> applyOptions());
		minimalParticles.onChange(v -> applyOptions());
		noEntityShadows.onChange(v -> applyOptions());
		noVignette.onChange(v -> applyOptions());
		lowerEntityDistance.onChange(v -> applyOptions());
		entityDistance.onChange(v -> applyOptions());
		cappedRenderDistance.onChange(v -> applyOptions());
		renderDistance.onChange(v -> applyOptions());
		cappedSimulationDistance.onChange(v -> applyOptions());
		simulationDistance.onChange(v -> applyOptions());
		noBiomeBlend.onChange(v -> applyOptions());
		unlockedFps.onChange(v -> applyOptions());
		framerateLimit.onChange(v -> applyOptions());
		noFovEffects.onChange(v -> applyOptions());
		noScreenEffects.onChange(v -> applyOptions());
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

	public static boolean hideAmbientParticles() {
		return isActive() && instance.noAmbientParticles.getValue();
	}

	public static boolean hideTotemAnimation() {
		return isActive() && instance.noTotemAnimation.getValue();
	}

	public static boolean hidePumpkinOverlay() {
		return isActive() && instance.noPumpkinOverlay.getValue();
	}

	public static boolean hideEnchantmentGlint() {
		return isActive() && instance.noEnchantmentGlint.getValue();
	}

	public static boolean cullEntities() {
		return isActive() && instance.cullDistantEntities.getValue();
	}

	public static double cullDistanceSq() {
		if (!cullEntities()) {
			return Double.MAX_VALUE;
		}
		double d = instance.cullDistance.getValue();
		return d * d;
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
		savedRenderDistance = options.renderDistance().get();
		savedSimulationDistance = options.simulationDistance().get();
		savedBiomeBlend = options.biomeBlendRadius().get();
		savedFramerateLimit = options.framerateLimit().get();
		savedFovEffectScale = options.fovEffectScale().get();
		savedScreenEffectScale = options.screenEffectScale().get();
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

		int desiredRender = cappedRenderDistance.getValue()
			? Math.min(savedRenderDistance, renderDistance.getValue().intValue())
			: savedRenderDistance;
		if (options.renderDistance().get() != desiredRender) {
			options.renderDistance().set(desiredRender);
		}

		int desiredSimulation = cappedSimulationDistance.getValue()
			? Math.min(savedSimulationDistance, simulationDistance.getValue().intValue())
			: savedSimulationDistance;
		if (options.simulationDistance().get() != desiredSimulation) {
			options.simulationDistance().set(desiredSimulation);
		}

		options.biomeBlendRadius().set(noBiomeBlend.getValue() ? 0 : savedBiomeBlend);
		options.framerateLimit().set(unlockedFps.getValue() ? framerateLimit.getValue().intValue() : savedFramerateLimit);
		options.fovEffectScale().set(noFovEffects.getValue() ? 0.0 : savedFovEffectScale);
		options.screenEffectScale().set(noScreenEffects.getValue() ? 0.0 : savedScreenEffectScale);
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
			options.renderDistance().set(savedRenderDistance);
			options.simulationDistance().set(savedSimulationDistance);
			options.biomeBlendRadius().set(savedBiomeBlend);
			options.framerateLimit().set(savedFramerateLimit);
			options.fovEffectScale().set(savedFovEffectScale);
			options.screenEffectScale().set(savedScreenEffectScale);
		}
		hasSnapshot = false;
	}
}
