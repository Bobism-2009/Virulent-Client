package dev.virulent.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

public final class GuiSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DEBOUNCE_MS = 300;

	private GuiLayoutStyle layoutStyle = GuiLayoutStyle.DEFAULT;
	private int accentColor = GuiLayoutStyle.DEFAULT.defaultAccent();
	private int headerColor = GuiLayoutStyle.DEFAULT.defaultHeader();
	private int windowWidth = 260;
	private int windowHeight = 0;
	private int windowX = 10;
	private int windowY = 10;
	private int scrollOffset = 0;
	private boolean showDescriptions = true;
	private boolean showKeybinds = true;
	private Category selectedCategory = Category.COMBAT;
	private int hudX = 4;
	private int hudY = 4;
	private HudSort hudSort = HudSort.LENGTH;
	private boolean hudVisible = true;
	private final CategoryPanelState categoryPanels = new CategoryPanelState();

	private long saveDeadline;
	private boolean loading;

	private final Set<Category> collapsedCategories = EnumSet.noneOf(Category.class);

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
		int[] presets = {0xFF39FF14, 0xFF00D4FF, 0xFFFF4444, 0xFFB026FF, 0xFFFFAA00, 0xFFFFFFFF, 0xFFFFCC00};
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
			if (json.has("layout")) {
				layoutStyle = GuiLayoutStyle.fromName(json.get("layout").getAsString());
			} else if (json.has("theme")) {
				layoutStyle = GuiLayoutStyle.fromName(json.get("theme").getAsString());
			}
			if (json.has("accentColor")) {
				accentColor = json.get("accentColor").getAsInt();
			}
			if (json.has("headerColor")) {
				headerColor = json.get("headerColor").getAsInt();
			}
			if (json.has("showDescriptions")) {
				showDescriptions = json.get("showDescriptions").getAsBoolean();
			}
			if (json.has("showKeybinds")) {
				showKeybinds = json.get("showKeybinds").getAsBoolean();
			}
			if (json.has("hud")) {
				JsonObject hud = json.getAsJsonObject("hud");
				if (hud.has("x")) {
					hudX = hud.get("x").getAsInt();
				}
				if (hud.has("y")) {
					hudY = hud.get("y").getAsInt();
				}
				if (hud.has("sort")) {
					try {
						hudSort = HudSort.valueOf(hud.get("sort").getAsString());
					} catch (IllegalArgumentException ignored) {
					}
				}
				if (hud.has("visible")) {
					hudVisible = hud.get("visible").getAsBoolean();
				}
			}

			collapsedCategories.clear();

			if (json.has("categoryPanels")) {
				JsonObject panels = json.getAsJsonObject("categoryPanels");
				for (Category category : Category.values()) {
					if (!panels.has(category.name())) {
						continue;
					}
					JsonObject panel = panels.getAsJsonObject(category.name());
					categoryPanels.set(
						category,
						panel.has("x") ? panel.get("x").getAsInt() : 20,
						panel.has("y") ? panel.get("y").getAsInt() : 20,
						panel.has("collapsed") && panel.get("collapsed").getAsBoolean()
					);
				}
			} else {
				categoryPanels.resetDefaults(layoutStyle);
			}

			if (json.has("window")) {
				JsonObject window = json.getAsJsonObject("window");
				if (window.has("x")) {
					windowX = window.get("x").getAsInt();
				}
				if (window.has("y")) {
					windowY = window.get("y").getAsInt();
				}
				if (window.has("width")) {
					setWindowWidth(window.get("width").getAsInt());
				}
				if (window.has("height")) {
					setWindowHeight(window.get("height").getAsInt());
				}
				if (window.has("scroll")) {
					scrollOffset = window.get("scroll").getAsInt();
				}
				if (window.has("category")) {
					try {
						selectedCategory = Category.valueOf(window.get("category").getAsString());
					} catch (IllegalArgumentException ignored) {
					}
				}
				if (window.has("collapsed")) {
					for (var element : window.getAsJsonArray("collapsed")) {
						try {
							collapsedCategories.add(Category.valueOf(element.getAsString()));
						} catch (IllegalArgumentException ignored) {
						}
					}
				}
			} else if (json.has("panels")) {
				JsonObject panels = json.getAsJsonObject("panels");
				for (Category category : Category.values()) {
					if (!panels.has(category.name())) {
						continue;
					}
					JsonObject panel = panels.getAsJsonObject(category.name());
					int px = panel.get("x").getAsInt();
					int py = panel.get("y").getAsInt();
					boolean collapsed = panel.has("collapsed") && panel.get("collapsed").getAsBoolean();
					categoryPanels.set(category, px, py, collapsed);
					windowX = px;
					windowY = py;
				}
			}
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to load GUI settings", exception);
		} finally {
			loading = false;
		}
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
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException exception) {
			VirulentClient.LOGGER.error("Failed to save GUI settings", exception);
		}
	}

	private static Path getPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("virulent").resolve("gui.json");
	}
}
