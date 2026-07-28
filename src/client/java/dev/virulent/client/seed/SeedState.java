package dev.virulent.client.seed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.virulent.client.VirulentClient;
import dev.virulent.client.mixin.PrimaryLevelDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tracks the known world seed / hashed seed for structure tools.
 * Full reverse-cracking (structures → seed) is handled by SeedcrackerX when installed;
 * this stores the result and powers structure locate.
 */
public final class SeedState {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final SeedState INSTANCE = new SeedState();

	private Long worldSeed;
	private Long hashedSeed;
	private String source = "none";

	private SeedState() {
	}

	public static SeedState get() {
		return INSTANCE;
	}

	public Long getWorldSeed() {
		return worldSeed;
	}

	public Long getHashedSeed() {
		return hashedSeed;
	}

	public String getSource() {
		return source;
	}

	public boolean hasWorldSeed() {
		return worldSeed != null;
	}

	public void setWorldSeed(long seed, String source) {
		this.worldSeed = seed;
		this.source = source;
		scheduleSave();
	}

	public void clearWorldSeed() {
		worldSeed = null;
		source = hashedSeed != null ? "hashed" : "none";
		scheduleSave();
	}

	public void setHashedSeed(long hashed) {
		this.hashedSeed = hashed;
	}

	/** Prefer integrated-server seed when in singleplayer. */
	public void syncFromGame() {
		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			return;
		}
		WorldData data = server.getWorldData();
		if (data instanceof PrimaryLevelData primary) {
			long seed = ((PrimaryLevelDataAccessor) (Object) primary).virulent$getWorldOptions().seed();
			setWorldSeed(seed, "singleplayer");
		}
	}

	public boolean tryParseAndSet(String raw) {
		if (raw == null) {
			return false;
		}
		String text = raw.trim();
		if (text.isEmpty()) {
			return false;
		}
		try {
			long seed;
			if (text.chars().allMatch(ch -> ch == '-' || Character.isDigit(ch))) {
				seed = Long.parseLong(text);
			} else {
				seed = text.hashCode(); // string seeds like vanilla
			}
			setWorldSeed(seed, "manual");
			return true;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	public void load() {
		Path path = path();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
			if (root == null) {
				return;
			}
			if (root.has("worldSeed") && !root.get("worldSeed").isJsonNull()) {
				worldSeed = root.get("worldSeed").getAsLong();
			}
			if (root.has("source")) {
				source = root.get("source").getAsString();
			}
		} catch (Exception exception) {
			VirulentClient.LOGGER.error("Failed to load seed state", exception);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		if (worldSeed != null) {
			root.addProperty("worldSeed", worldSeed);
		}
		root.addProperty("source", source);
		try {
			Path path = path();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to save seed state", exception);
		}
	}

	private void scheduleSave() {
		save();
	}

	private static Path path() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("virulent").resolve("seed.json");
	}
}
