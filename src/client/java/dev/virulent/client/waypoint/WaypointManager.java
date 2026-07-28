package dev.virulent.client.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.virulent.client.VirulentClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WaypointManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DEBOUNCE_MS = 300;
	private static final int[] PALETTE = {
		0xFF4CFF66, 0xFF5CC8FF, 0xFFFFD24A, 0xFFFF6B6B, 0xFFE14CFF, 0xFFFFFFFF, 0xFFFF9944
	};

	private final List<Waypoint> waypoints = new CopyOnWriteArrayList<>();
	private long saveDeadline;
	private int colorIndex;

	public List<Waypoint> getWaypoints() {
		return Collections.unmodifiableList(waypoints);
	}

	public int size() {
		return waypoints.size();
	}

	public void add(Waypoint waypoint) {
		waypoints.add(waypoint);
		scheduleSave();
	}

	public void remove(Waypoint waypoint) {
		waypoints.remove(waypoint);
		scheduleSave();
	}

	public void clear() {
		waypoints.clear();
		scheduleSave();
	}

	public Waypoint findByNameIgnoreCase(String name) {
		for (Waypoint waypoint : waypoints) {
			if (waypoint.getName().equalsIgnoreCase(name)) {
				return waypoint;
			}
		}
		return null;
	}

	/** Creates or updates a single named waypoint (used for Death). */
	public Waypoint upsertNamed(String name, double x, double y, double z, String dimension, int color) {
		Waypoint existing = findByNameIgnoreCase(name);
		if (existing != null) {
			existing.setName(name);
			existing.setPos(x, y, z);
			existing.setDimension(dimension);
			existing.setColor(color);
			scheduleSave();
			return existing;
		}
		Waypoint created = new Waypoint(name, x, y, z, dimension, color);
		add(created);
		return created;
	}

	public Waypoint recordDeath(double x, double y, double z, String dimension) {
		return upsertNamed(
			WaypointCoords.DEATH_NAME,
			x,
			y,
			z,
			dimension,
			WaypointCoords.DEATH_COLOR
		);
	}

	public int nextColor() {
		int color = PALETTE[colorIndex % PALETTE.length];
		colorIndex++;
		return color;
	}

	public String currentDimensionId() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return "minecraft:overworld";
		}
		Identifier id = client.level.dimension().identifier();
		return id.toString();
	}

	public void scheduleSave() {
		saveDeadline = System.currentTimeMillis() + SAVE_DEBOUNCE_MS;
	}

	public void tick() {
		if (saveDeadline > 0 && System.currentTimeMillis() >= saveDeadline) {
			saveDeadline = 0;
			save();
		}
	}

	public void load() {
		Path path = getPath();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
			if (root == null || !root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
				return;
			}
			List<Waypoint> loaded = new ArrayList<>();
			for (JsonElement element : root.getAsJsonArray("waypoints")) {
				if (element.isJsonObject()) {
					loaded.add(Waypoint.fromJson(element.getAsJsonObject()));
				}
			}
			waypoints.clear();
			waypoints.addAll(loaded);
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to load waypoints", exception);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		JsonArray array = new JsonArray();
		for (Waypoint waypoint : waypoints) {
			array.add(waypoint.toJson());
		}
		root.add("waypoints", array);
		try {
			Path path = getPath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to save waypoints", exception);
		}
	}

	private Path getPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("virulent").resolve("waypoints.json");
	}
}
