package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import net.minecraft.client.Minecraft;

import java.util.EnumMap;
import java.util.Map;

/** Per-category floating panel positions for Meteor/Wurst layouts. */
public final class CategoryPanelState {
	private final Map<Category, Panel> panels = new EnumMap<>(Category.class);

	public CategoryPanelState() {
		resetDefaults(GuiLayoutStyle.METEOR);
	}

	public Panel get(Category category) {
		return panels.computeIfAbsent(category, ignored -> new Panel(20, 20, false));
	}

	public void set(Category category, int x, int y, boolean collapsed) {
		panels.put(category, new Panel(x, y, collapsed));
	}

	public void resetDefaults(GuiLayoutStyle style) {
		panels.clear();
		Category[] categories = Category.values();
		if (style == GuiLayoutStyle.WURST) {
			int x = 24;
			int y = 28;
			for (Category category : categories) {
				panels.put(category, new Panel(x, y, false));
				x += 108;
			}
			return;
		}

		// Meteor: pack windows in a row with 4px gaps, wrapping when needed.
		int x = 4;
		int y = 40;
		int rowH = 40;
		int screenW = 854;
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() != null) {
			screenW = client.getWindow().getGuiScaledWidth();
		}
		for (Category category : categories) {
			int w = estimateMeteorWidth(category);
			if (x + w > screenW - 4 && x > 4) {
				x = 4;
				y += rowH;
			}
			panels.put(category, new Panel(x, y, false));
			x += w + 4;
		}
	}

	private static int estimateMeteorWidth(Category category) {
		Minecraft client = Minecraft.getInstance();
		if (client.font == null) {
			return Math.round(80 * 0.72f);
		}
		int width = Math.max(80, client.font.width(category.getDisplayName()) + 22);
		if (VirulentClient.getInstance() == null || VirulentClient.getInstance().getModuleManager() == null) {
			return Math.round(width * 0.72f);
		}
		for (Module module : VirulentClient.getInstance().getModuleManager().getModulesByCategory(category)) {
			width = Math.max(width, client.font.width(module.getName()) + 12);
		}
		return Math.round(width * 0.72f);
	}

	public record Panel(int x, int y, boolean collapsed) {
		public Panel withPosition(int x, int y) {
			return new Panel(x, y, collapsed);
		}

		public Panel toggled() {
			return new Panel(x, y, !collapsed);
		}
	}
}
