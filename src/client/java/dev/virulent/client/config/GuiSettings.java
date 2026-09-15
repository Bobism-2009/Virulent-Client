package dev.virulent.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.virulent.client.VirulentClient;
import dev.virulent.client.gui.clickgui.CategoryPanelState;
import dev.virulent.client.gui.clickgui.GuiLayoutStyle;
import dev.virulent.client.module.Category;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

public final class GuiSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DEBOUNCE_MS = 300;

	private GuiLayoutStyle layoutStyle;
	private int accentColor;
	private int headerColor;
	private int windowWidth;
	private int windowHeight;
	private int windowX;
	private int windowY;
	private int scrollOffset;
	private boolean showDescriptions;
	private boolean showKeybinds;
	private Category selectedCategory;
	private int hudX;
	private int hudY;
	private HudSort hudSort;
	private boolean hudVisible;
	private boolean hudBlack;
	private final CategoryPanelState categoryPanels = new CategoryPanelState();

	private long saveDeadline;
	private boolean loading;

	private final Set<Category> collapsedCategories = EnumSet.noneOf(Category.class);

	public GuiSettings() {
		resetToDefaults();
	}

	/** Single source of truth for the shipped defaults, also used when {@code gui.json} is unreadable. */
	private void resetToDefaults() {
		layoutStyle = GuiLayoutStyle.DEFAULT;
		accentColor = GuiLayoutStyle.DEFAULT.defaultAccent();
		headerColor = GuiLayoutStyle.DEFAULT.defaultHeader();
		windowWidth = 260;
		windowHeight = 0;
		windowX = 10;
		windowY = 10;
		scrollOffset = 0;
		showDescriptions = true;
		showKeybinds = true;
		selectedCategory = Category.COMBAT;
		hudX = 4;
		hudY = 4;
		hudSort = HudSort.LENGTH;
		hudVisible = true;
		hudBlack = false;
		collapsedCategories.clear();
		categoryPanels.resetDefaults(layoutStyle);
	}

	public GuiLayoutStyle getLayoutStyle() {
		return layoutStyle;
	}

	/** @deprecated use {@link #getLayoutStyle()} */
	@Deprecated
	public GuiLayoutStyle getTheme() {
		return layoutStyle;
	}

	public CategoryPanelState getCategoryPanels() {
		return categoryPanels;
	}

	public int getAccentColor() {
		return accentColor;
	}

	public int getHeaderColor() {
		return headerColor;
	}

	public int getWindowWidth() {
		return windowWidth;
	}

	public int getWindowHeight() {
		return windowHeight;
	}

	public int getWindowX() {
		return windowX;
	}

	public int getWindowY() {
		return windowY;
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	public void setWindowWidth(int windowWidth) {
		this.windowWidth = Math.max(200, Math.min(400, windowWidth));
		scheduleSave();
	}

	public Category getSelectedCategory() {
		return selectedCategory;
	}

	public void setSelectedCategory(Category category) {
		this.selectedCategory = category;
		scheduleSave();
	}

	public void setWindowHeight(int windowHeight) {
		this.windowHeight = Math.max(0, Math.min(600, windowHeight));
		scheduleSave();
	}

	public void setWindowPosition(int x, int y) {
		this.windowX = x;
		this.windowY = y;
		scheduleSave();
	}

	public void setScrollOffset(int scrollOffset) {
		this.scrollOffset = Math.max(0, scrollOffset);
		scheduleSave();
	}

	public boolean showDescriptions() {
		return showDescriptions;
	}

	public boolean showKeybinds() {
		return showKeybinds;
	}

	public boolean isCollapsed(Category category) {
		return collapsedCategories.contains(category);
	}

	public void setCollapsed(Category category, boolean collapsed) {
		if (collapsed) {
			collapsedCategories.add(category);
		} else {
			collapsedCategories.remove(category);
		}
	}

	public void cycleLayoutStyle() {
		layoutStyle = layoutStyle.next();
		accentColor = layoutStyle.defaultAccent();
		headerColor = layoutStyle.defaultHeader();
		categoryPanels.resetDefaults(layoutStyle);
		scheduleSave();
	}

	/** @deprecated use {@link #cycleLayoutStyle()} */
	@Deprecated
	public void cycleTheme() {
		cycleLayoutStyle();
	}

	public void cycleAccentPreset() {
		int[] presets = {
			0xFF39FF14,
			0xFF00D4FF,
			0xFFFF4444,
			0xFFB026FF,
			0xFFFFAA00,
			0xFFFFFFFF,
			0xFFFFCC00,
			0xFF000000
		};
		for (int i = 0; i < presets.length; i++) {
			if (presets[i] == accentColor) {
				accentColor = presets[(i + 1) % presets.length];
				scheduleSave();
				return;
			}
		}
		accentColor = presets[0];
		scheduleSave();
	}

	/** True when the accent is black / near-black (HUD should use white text). */
	public boolean isAccentBlack() {
		int rgb = accentColor & 0x00FFFFFF;
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return r + g + b < 48;
	}

	public void toggleDescriptions() {
		showDescriptions = !showDescriptions;
		scheduleSave();
	}

	public void toggleKeybinds() {
		showKeybinds = !showKeybinds;
		scheduleSave();
	}

	public int getHudX() {
		return hudX;
	}

	public int getHudY() {
		return hudY;
	}

	public void setHudPosition(int x, int y) {
		this.hudX = Math.max(0, x);
		this.hudY = Math.max(0, y);
		scheduleSave();
	}

	public HudSort getHudSort() {
		return hudSort;
	}

	public void cycleHudSort() {
		hudSort = hudSort.next();
		scheduleSave();
	}

	public boolean isHudVisible() {
		return hudVisible;
	}

	public void toggleHudVisible() {
		hudVisible = !hudVisible;
		scheduleSave();
	}

	public boolean isHudBlack() {
		return hudBlack;
	}

	public void toggleHudBlack() {
		hudBlack = !hudBlack;
		scheduleSave();
	}

	public void scheduleSave() {
		if (loading) {
			return;
		}
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

		loading = true;
		try {
			JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
			if (json == null) {
				return;
			}
			applyAppearance(json);
			applyHud(objectOrNull(json, "hud"));

			collapsedCategories.clear();
			applyCategoryPanels(json);
			applyWindow(json);
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to load GUI settings", exception);
		} catch (RuntimeException exception) {
			// A torn gui.json must not take the client down before any in-game recovery is reachable.
			VirulentClient.LOGGER.error("gui.json is corrupt, falling back to default GUI settings", exception);
			resetToDefaults();
		} finally {
			loading = false;
		}
	}

	private void applyAppearance(JsonObject json) {
		if (json.has("layout")) {
			apply(json, "layout", value -> layoutStyle = GuiLayoutStyle.fromName(value.getAsString()));
		} else {
			apply(json, "theme", value -> layoutStyle = GuiLayoutStyle.fromName(value.getAsString()));
		}
		apply(json, "accentColor", value -> accentColor = value.getAsInt());
		apply(json, "headerColor", value -> headerColor = value.getAsInt());
		apply(json, "showDescriptions", value -> showDescriptions = value.getAsBoolean());
		apply(json, "showKeybinds", value -> showKeybinds = value.getAsBoolean());
	}

	private void applyHud(JsonObject hud) {
		if (hud == null) {
			return;
		}
		apply(hud, "x", value -> hudX = value.getAsInt());
		apply(hud, "y", value -> hudY = value.getAsInt());
		apply(hud, "sort", value -> hudSort = HudSort.valueOf(value.getAsString()));
		apply(hud, "visible", value -> hudVisible = value.getAsBoolean());
		apply(hud, "black", value -> hudBlack = value.getAsBoolean());
	}

	private void applyCategoryPanels(JsonObject json) {
		JsonObject panels = objectOrNull(json, "categoryPanels");
		if (panels == null) {
			categoryPanels.resetDefaults(layoutStyle);
			return;
		}
		for (Category category : Category.values()) {
			JsonObject panel = objectOrNull(panels, category.name());
			if (panel == null) {
				continue;
			}
			categoryPanels.set(
				category,
				intOrDefault(panel, "x", 20),
				intOrDefault(panel, "y", 20),
				booleanOrDefault(panel, "collapsed", false)
			);
		}
	}

	private void applyWindow(JsonObject json) {
		JsonObject window = objectOrNull(json, "window");
		if (window == null) {
			applyLegacyPanels(objectOrNull(json, "panels"));
			return;
		}
		apply(window, "x", value -> windowX = value.getAsInt());
		apply(window, "y", value -> windowY = value.getAsInt());
		apply(window, "width", value -> setWindowWidth(value.getAsInt()));
		apply(window, "height", value -> setWindowHeight(value.getAsInt()));
		apply(window, "scroll", value -> scrollOffset = value.getAsInt());
		apply(window, "category", value -> selectedCategory = Category.valueOf(value.getAsString()));
		if (window.has("collapsed") && window.get("collapsed").isJsonArray()) {
			for (JsonElement element : window.getAsJsonArray("collapsed")) {
				try {
					collapsedCategories.add(Category.valueOf(element.getAsString()));
				} catch (RuntimeException exception) {
					logBadEntry("window.collapsed", exception);
				}
			}
		}
	}

	/** Pre-{@code window} layout, where each category carried its own position. */
	private void applyLegacyPanels(JsonObject panels) {
		if (panels == null) {
			return;
		}
		for (Category category : Category.values()) {
			JsonObject panel = objectOrNull(panels, category.name());
			if (panel == null) {
				continue;
			}
			int px = intOrDefault(panel, "x", windowX);
			int py = intOrDefault(panel, "y", windowY);
			categoryPanels.set(category, px, py, booleanOrDefault(panel, "collapsed", false));
			windowX = px;
			windowY = py;
		}
	}

	/** Applies one entry, logging and skipping it when the stored value has the wrong shape. */
	private static void apply(JsonObject owner, String key, Consumer<JsonElement> applier) {
		if (!owner.has(key) || owner.get(key).isJsonNull()) {
			return;
		}
		try {
			applier.accept(owner.get(key));
		} catch (RuntimeException exception) {
			logBadEntry(key, exception);
		}
	}

	private static JsonObject objectOrNull(JsonObject owner, String key) {
		return owner.has(key) && owner.get(key).isJsonObject() ? owner.getAsJsonObject(key) : null;
	}

	private static int intOrDefault(JsonObject owner, String key, int fallback) {
		if (!owner.has(key) || owner.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return owner.get(key).getAsInt();
		} catch (RuntimeException exception) {
			logBadEntry(key, exception);
			return fallback;
		}
	}

	private static boolean booleanOrDefault(JsonObject owner, String key, boolean fallback) {
		if (!owner.has(key) || owner.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return owner.get(key).getAsBoolean();
		} catch (RuntimeException exception) {
			logBadEntry(key, exception);
			return fallback;
		}
	}

	private static void logBadEntry(String key, RuntimeException exception) {
		VirulentClient.LOGGER.warn("Ignoring malformed GUI setting {}: {}", key, exception.toString());
	}

	public void save() {
		JsonObject json = new JsonObject();
		json.addProperty("layout", layoutStyle.name());
		json.addProperty("accentColor", accentColor);
		json.addProperty("headerColor", headerColor);
		json.addProperty("showDescriptions", showDescriptions);
		json.addProperty("showKeybinds", showKeybinds);

		JsonObject hud = new JsonObject();
		hud.addProperty("x", hudX);
		hud.addProperty("y", hudY);
		hud.addProperty("sort", hudSort.name());
		hud.addProperty("visible", hudVisible);
		hud.addProperty("black", hudBlack);
		json.add("hud", hud);

		JsonObject window = new JsonObject();
		window.addProperty("x", windowX);
		window.addProperty("y", windowY);
		window.addProperty("width", windowWidth);
		window.addProperty("height", windowHeight);
		window.addProperty("scroll", scrollOffset);
		window.addProperty("category", selectedCategory.name());
		var collapsed = new com.google.gson.JsonArray();
		for (Category category : collapsedCategories) {
			collapsed.add(category.name());
		}
		window.add("collapsed", collapsed);
		json.add("window", window);

		JsonObject panels = new JsonObject();
		for (Category category : Category.values()) {
			CategoryPanelState.Panel panel = categoryPanels.get(category);
			JsonObject entry = new JsonObject();
			entry.addProperty("x", panel.x());
			entry.addProperty("y", panel.y());
			entry.addProperty("collapsed", panel.collapsed());
			panels.add(category.name(), entry);
		}
		json.add("categoryPanels", panels);

		try {
			Path path = getPath();
			Files.createDirectories(path.getParent());
			ConfigFiles.writeAtomically(path, GSON.toJson(json));
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to save GUI settings", exception);
		}
	}

	private static Path getPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("virulent").resolve("gui.json");
	}
}
