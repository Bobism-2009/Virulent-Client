package dev.virulent.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BlockEspConfig;
import dev.virulent.client.setting.BlockEspConfigSetting;
import dev.virulent.client.setting.BlockEspConfigsSetting;
import dev.virulent.client.setting.BlockListSetting;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.KeybindSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DEBOUNCE_MS = 300;
	private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9 _.-]{1,32}");
	public static final String DEFAULT_PROFILE = "default";
	public static final String SINGLEPLAYER_KEY = "singleplayer";

	private long saveDeadline;
	private boolean loading;
	private String activeProfile = DEFAULT_PROFILE;
	private boolean autoSwitchServers = true;
	private final Map<String, String> serverProfiles = new LinkedHashMap<>();
	private String lastServerKey;

	public void scheduleSave() {
		if (loading) {
			return;
		}
		saveDeadline = System.currentTimeMillis() + SAVE_DEBOUNCE_MS;
	}

	public boolean isLoading() {
		return loading;
	}

	public void tick() {
		if (saveDeadline > 0 && System.currentTimeMillis() >= saveDeadline) {
			saveDeadline = 0;
			save();
		}
		syncServerProfile();
	}

	public String getActiveProfile() {
		return activeProfile;
	}

	public boolean isAutoSwitchServers() {
		return autoSwitchServers;
	}

	public void setAutoSwitchServers(boolean enabled) {
		autoSwitchServers = enabled;
		saveMeta();
	}

	public Map<String, String> getServerProfiles() {
		return Map.copyOf(serverProfiles);
	}

	/** Normalized key for the current world, or null when not in-game. */
	public String currentServerKey() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return null;
		}
		if (client.getSingleplayerServer() != null && client.getCurrentServer() == null) {
			return SINGLEPLAYER_KEY;
		}
		var data = client.getCurrentServer();
		if (data == null || data.ip == null || data.ip.isBlank()) {
			return null;
		}
		return normalizeServerKey(data.ip);
	}

	public String profileForServer(String serverKey) {
		if (serverKey == null) {
			return null;
		}
		return serverProfiles.get(serverKey);
	}

	public boolean bindCurrentServer(String profileName) {
		String key = currentServerKey();
		if (key == null) {
			return false;
		}
		return bindServer(key, profileName);
	}

	public boolean bindServer(String serverKey, String profileName) {
		String key = normalizeServerKey(serverKey);
		String profile = sanitizeName(profileName);
		if (key == null || key.isBlank() || profile == null) {
			return false;
		}
		if (!profileExists(profile)) {
			activeProfile = profile;
			save();
		}
		serverProfiles.put(key, profile);
		saveMeta();
		return true;
	}

	public boolean unbindCurrentServer() {
		String key = currentServerKey();
		if (key == null) {
			return false;
		}
		return unbindServer(key);
	}

	public boolean unbindServer(String serverKey) {
		String key = normalizeServerKey(serverKey);
		if (key == null || !serverProfiles.containsKey(key)) {
			return false;
		}
		serverProfiles.remove(key);
		saveMeta();
		return true;
	}

	public static String normalizeServerKey(String address) {
		if (address == null) {
			return null;
		}
		String trimmed = address.trim().toLowerCase(Locale.ROOT);
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.equals(SINGLEPLAYER_KEY)) {
			return SINGLEPLAYER_KEY;
		}
		// Drop trailing dot / spaces; keep host:port so different ports stay distinct.
		while (trimmed.endsWith(".")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed.isEmpty() ? null : trimmed;
	}

	private void syncServerProfile() {
		String key = currentServerKey();
		if (key == null) {
			lastServerKey = null;
			return;
		}
		if (key.equals(lastServerKey)) {
			return;
		}
		lastServerKey = key;
		if (!autoSwitchServers) {
			return;
		}
		String profile = serverProfiles.get(key);
		if (profile == null || profile.equalsIgnoreCase(activeProfile)) {
			return;
		}
		if (!profileExists(profile)) {
			serverProfiles.remove(key);
			saveMeta();
			return;
		}
		// Persist the profile you were on before swapping.
		save();
		if (loadProfile(profile)) {
			VirulentClient.LOGGER.info("Auto-loaded profile '{}' for {}", profile, key);
		}
	}

	public List<String> listProfiles() {
		List<String> names = new ArrayList<>();
		Path dir = getProfilesDir();
		if (!Files.isDirectory(dir)) {
			names.add(DEFAULT_PROFILE);
			return names;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
			for (Path path : stream) {
				String fileName = path.getFileName().toString();
				names.add(fileName.substring(0, fileName.length() - 5));
			}
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to list profiles", exception);
		}
		if (names.isEmpty()) {
			names.add(DEFAULT_PROFILE);
		}
		names.sort(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)));
		return names;
	}

	public boolean profileExists(String name) {
		String sanitized = sanitizeName(name);
		return sanitized != null && Files.exists(getProfilePath(sanitized));
	}

	/** Saves the current module state under {@code name} and makes it active. */
	public boolean saveProfile(String name) {
		String sanitized = sanitizeName(name);
		if (sanitized == null) {
			return false;
		}
		activeProfile = sanitized;
		saveMeta();
		save();
		return true;
	}

	/** Loads {@code name}, makes it active, and applies module settings. */
	public boolean loadProfile(String name) {
		String sanitized = sanitizeName(name);
		if (sanitized == null) {
			return false;
		}
		Path path = getProfilePath(sanitized);
		if (!Files.exists(path)) {
			return false;
		}
		activeProfile = sanitized;
		saveMeta();
		loadFrom(path);
		mirrorLegacyConfig();
		return true;
	}

	public boolean deleteProfile(String name) {
		String sanitized = sanitizeName(name);
		if (sanitized == null || sanitized.equalsIgnoreCase(DEFAULT_PROFILE)) {
			return false;
		}
		Path path = getProfilePath(sanitized);
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to delete profile {}", sanitized, exception);
			return false;
		}
		if (sanitized.equalsIgnoreCase(activeProfile)) {
			activeProfile = DEFAULT_PROFILE;
			saveMeta();
			if (Files.exists(getProfilePath(DEFAULT_PROFILE))) {
				loadFrom(getProfilePath(DEFAULT_PROFILE));
			}
			mirrorLegacyConfig();
		}
		return true;
	}

	public static String sanitizeName(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		if (!SAFE_NAME.matcher(trimmed).matches()) {
			return null;
		}
		return trimmed;
	}

	public void load() {
		ensureDirs();
		loadMeta();
		migrateLegacyIfNeeded();

		Path profilePath = getProfilePath(activeProfile);
		if (Files.exists(profilePath)) {
			loadFrom(profilePath);
		} else if (Files.exists(getLegacyConfigPath())) {
			loadFrom(getLegacyConfigPath());
			save();
		}
	}

	public void save() {
		JsonObject root = serializeModules();
		try {
			ensureDirs();
			Path profilePath = getProfilePath(activeProfile);
			Files.writeString(profilePath, GSON.toJson(root));
			Files.writeString(getLegacyConfigPath(), GSON.toJson(root));
			saveMeta();
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to save config", exception);
		}
	}

	private void loadFrom(Path path) {
		loading = true;
		try {
			String json = Files.readString(path);
			JsonObject root = GSON.fromJson(json, JsonObject.class);
			if (root == null) {
				return;
			}
			applyModules(root);
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to load config from {}", path, exception);
		} finally {
			loading = false;
		}
	}

	private void applyModules(JsonObject root) {
		for (Module module : VirulentClient.getInstance().getModuleManager().getModules()) {
			if (!root.has(module.getName())) {
				continue;
			}

			JsonObject moduleJson = root.getAsJsonObject(module.getName());
			if (moduleJson.has("enabled")) {
				module.setEnabled(moduleJson.get("enabled").getAsBoolean());
			}
			if (moduleJson.has("keyBind")) {
				module.setKeyBind(moduleJson.get("keyBind").getAsInt());
			}

			for (Setting<?> setting : module.getSettings()) {
				if (!moduleJson.has(setting.getName())) {
					continue;
				}
				applySetting(setting, moduleJson.get(setting.getName()));
			}
		}
	}

	private JsonObject serializeModules() {
		JsonObject root = new JsonObject();
		for (Module module : VirulentClient.getInstance().getModuleManager().getModules()) {
			JsonObject moduleJson = new JsonObject();
			moduleJson.addProperty("enabled", module.isEnabled());
			moduleJson.addProperty("keyBind", module.getKeyBind());
			for (Setting<?> setting : module.getSettings()) {
				moduleJson.add(setting.getName(), serializeSetting(setting));
			}
			root.add(module.getName(), moduleJson);
		}
		return root;
	}

	private void migrateLegacyIfNeeded() {
		Path legacy = getLegacyConfigPath();
		Path defaultProfile = getProfilePath(DEFAULT_PROFILE);
		if (Files.exists(legacy) && !Files.exists(defaultProfile)) {
			try {
				Files.copy(legacy, defaultProfile, StandardCopyOption.REPLACE_EXISTING);
				if (activeProfile == null || activeProfile.isBlank()) {
					activeProfile = DEFAULT_PROFILE;
				}
				saveMeta();
			} catch (IOException exception) {
				VirulentClient.LOGGER.error("Failed to migrate legacy config", exception);
			}
		}
	}

	private void mirrorLegacyConfig() {
		try {
			Path profilePath = getProfilePath(activeProfile);
			if (Files.exists(profilePath)) {
				Files.copy(profilePath, getLegacyConfigPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to mirror active profile", exception);
		}
	}

	private void loadMeta() {
		Path meta = getMetaPath();
		if (!Files.exists(meta)) {
			activeProfile = DEFAULT_PROFILE;
			autoSwitchServers = true;
			serverProfiles.clear();
			return;
		}
		try {
			JsonObject root = GSON.fromJson(Files.readString(meta), JsonObject.class);
			if (root == null) {
				activeProfile = DEFAULT_PROFILE;
				return;
			}
			if (root.has("active")) {
				String name = sanitizeName(root.get("active").getAsString());
				if (name != null) {
					activeProfile = name;
				}
			}
			autoSwitchServers = !root.has("autoSwitchServers") || root.get("autoSwitchServers").getAsBoolean();
			serverProfiles.clear();
			if (root.has("servers") && root.get("servers").isJsonObject()) {
				for (var entry : root.getAsJsonObject("servers").entrySet()) {
					String key = normalizeServerKey(entry.getKey());
					String profile = sanitizeName(entry.getValue().getAsString());
					if (key != null && profile != null) {
						serverProfiles.put(key, profile);
					}
				}
			}
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to load profile meta", exception);
			activeProfile = DEFAULT_PROFILE;
		}
	}

	private void saveMeta() {
		JsonObject root = new JsonObject();
		root.addProperty("active", activeProfile);
		root.addProperty("autoSwitchServers", autoSwitchServers);
		JsonObject servers = new JsonObject();
		for (var entry : serverProfiles.entrySet()) {
			servers.addProperty(entry.getKey(), entry.getValue());
		}
		root.add("servers", servers);
		try {
			ensureDirs();
			Files.writeString(getMetaPath(), GSON.toJson(root));
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to save profile meta", exception);
		}
	}

	private void ensureDirs() {
		try {
			Files.createDirectories(getProfilesDir());
			Files.createDirectories(getVirulentDir());
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to create config directories", exception);
		}
	}

	private Path getVirulentDir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("virulent");
	}

	private Path getProfilesDir() {
		return getVirulentDir().resolve("profiles");
	}

	private Path getProfilePath(String name) {
		return getProfilesDir().resolve(name + ".json");
	}

	private Path getMetaPath() {
		return getVirulentDir().resolve("profiles.json");
	}

	private Path getLegacyConfigPath() {
		return getVirulentDir().resolve("config.json");
	}

	private static void applySetting(Setting<?> setting, JsonElement value) {
		if (setting instanceof BooleanSetting booleanSetting) {
			booleanSetting.setValue(value.getAsBoolean());
		} else if (setting instanceof NumberSetting numberSetting) {
			numberSetting.setValue(value.getAsDouble());
		} else if (setting instanceof ModeSetting modeSetting) {
			modeSetting.setValue(value.getAsString());
		} else if (setting instanceof KeybindSetting keybindSetting) {
			keybindSetting.setValue(value.getAsInt());
		} else if (setting instanceof BlockListSetting blockListSetting && value.isJsonArray()) {
			Set<Block> blocks = new LinkedHashSet<>();
			for (JsonElement element : value.getAsJsonArray()) {
				Identifier id = Identifier.tryParse(element.getAsString());
				if (id == null) {
					continue;
				}
				BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
			}
			blockListSetting.setValue(blocks);
		} else if (setting instanceof BlockEspConfigSetting configSetting && value.isJsonObject()) {
			configSetting.setValue(BlockEspConfig.fromJson(value.getAsJsonObject()));
		} else if (setting instanceof BlockEspConfigsSetting configsSetting && value.isJsonObject()) {
			Map<Block, BlockEspConfig> map = new LinkedHashMap<>();
			for (var entry : value.getAsJsonObject().entrySet()) {
				Identifier id = Identifier.tryParse(entry.getKey());
				if (id == null || !entry.getValue().isJsonObject()) {
					continue;
				}
				BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block ->
					map.put(block, BlockEspConfig.fromJson(entry.getValue().getAsJsonObject()))
				);
			}
			configsSetting.setValue(map);
		}
	}

	private static JsonElement serializeSetting(Setting<?> setting) {
		if (setting instanceof BlockListSetting blockListSetting) {
			JsonArray array = new JsonArray();
			for (Block block : blockListSetting.getValue()) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(block);
				if (id != null) {
					array.add(id.toString());
				}
			}
			return array;
		}
		if (setting instanceof BlockEspConfigSetting configSetting) {
			return configSetting.getValue().toJson();
		}
		if (setting instanceof BlockEspConfigsSetting configsSetting) {
			JsonObject object = new JsonObject();
			for (var entry : configsSetting.getValue().entrySet()) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(entry.getKey());
				if (id != null) {
					object.add(id.toString(), entry.getValue().toJson());
				}
			}
			return object;
		}
		Object value = setting.getValue();
		if (value instanceof Boolean bool) {
			return GSON.toJsonTree(bool);
		}
		if (value instanceof Double number) {
			return GSON.toJsonTree(number);
		}
		if (value instanceof Integer number) {
			return GSON.toJsonTree(number);
		}
		return GSON.toJsonTree(value.toString());
	}
}
