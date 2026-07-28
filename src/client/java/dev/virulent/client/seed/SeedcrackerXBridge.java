package dev.virulent.client.seed;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional bridge: when SeedcrackerX is installed and cracks a seed, we import it.
 * Wired via reflection so SeedcrackerX is not a hard dependency.
 */
public final class SeedcrackerXBridge {
	private SeedcrackerXBridge() {
	}

	public static boolean isSeedcrackerXPresent() {
		return FabricLoader.getInstance().isModLoaded("seedcrackerx");
	}

	/** Called from Fabric entrypoint class via reflection-friendly API. */
	public static void onCrackedSeed(long seed) {
		SeedState.get().setWorldSeed(seed, "seedcrackerx");
	}
}
