package dev.virulent.client.friend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.virulent.client.VirulentClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FriendsManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DEBOUNCE_MS = 300;
	public static final int FRIEND_COLOR = 0xFF55CCFF;

	/** lowercase key -> display name */
	private final Map<String, String> friends = new ConcurrentHashMap<>();
	private long saveDeadline;

	public List<String> getFriends() {
		List<String> list = new ArrayList<>(friends.values());
		list.sort(String.CASE_INSENSITIVE_ORDER);
		return Collections.unmodifiableList(list);
	}

	public int size() {
		return friends.size();
	}

	public boolean isFriend(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		return friends.containsKey(normalize(name));
	}

	public boolean isFriend(Player player) {
		return player != null && isFriend(player.getGameProfile().name());
	}

	public boolean isFriend(Entity entity) {
		return entity instanceof Player player && isFriend(player);
	}

	public boolean add(String name) {
		String cleaned = clean(name);
		if (cleaned == null) {
			return false;
		}
		String key = normalize(cleaned);
		if (friends.containsKey(key)) {
			return false;
		}
		friends.put(key, cleaned);
		scheduleSave();
		return true;
	}

	public boolean remove(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		boolean removed = friends.remove(normalize(name)) != null;
		if (removed) {
			scheduleSave();
		}
		return removed;
	}

	public boolean toggle(String name) {
		if (isFriend(name)) {
			remove(name);
			return false;
		}
		add(name);
		return true;
	}

	public void clear() {
		if (friends.isEmpty()) {
			return;
		}
		friends.clear();
		scheduleSave();
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
			if (root == null || !root.has("friends") || !root.get("friends").isJsonArray()) {
				return;
			}
			friends.clear();
			for (JsonElement element : root.getAsJsonArray("friends")) {
				if (element.isJsonPrimitive()) {
					addQuiet(element.getAsString());
				}
			}
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to load friends", exception);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		JsonArray array = new JsonArray();
		List<String> names = new ArrayList<>(friends.values());
		names.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
		for (String name : names) {
			array.add(name);
		}
		root.add("friends", array);
		try {
			Path path = getPath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to save friends", exception);
		}
	}

	private void addQuiet(String name) {
		String cleaned = clean(name);
		if (cleaned != null) {
			friends.put(normalize(cleaned), cleaned);
		}
	}

	private static String clean(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		if (trimmed.isEmpty() || trimmed.length() > 16) {
			return null;
		}
		return trimmed;
	}

	private static String normalize(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	private Path getPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("virulent").resolve("friends.json");
	}
}
