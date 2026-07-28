package dev.virulent.client.gui.clickgui;

import dev.virulent.client.module.Category;

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

		// Meteor: staggered floating columns
		int x = 40;
		int y = 40;
		for (int i = 0; i < categories.length; i++) {
			panels.put(categories[i], new Panel(x + (i % 3) * 120, y + (i / 3) * 28, false));
		}
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
