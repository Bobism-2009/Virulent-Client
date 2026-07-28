package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.config.GuiSettings;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.module.modules.render.BlockEsp;
import dev.virulent.client.setting.ActionSetting;
import dev.virulent.client.setting.BlockEspConfigSetting;
import dev.virulent.client.setting.BlockEspConfigsSetting;
import dev.virulent.client.setting.BlockListSetting;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.KeybindSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.setting.Setting;
import dev.virulent.client.util.KeybindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Meteor-style floating panels and Wurst-style column windows.
 */
final class MultiPanelClickGui {
	private static final int HEADER_H = 16;
	private static final int ROW_H = 14;
	private static final int SETTING_H = 13;
	private static final int KEYBIND_H = 13;
	private static final int RESET_H = 13;
	private static final int DESC_H = 9;
	private static final int TOOLBAR_H = 18;
	private static final int METEOR_WIDTH = 118;
	private static final int WURST_WIDTH = 104;

	private final GuiSettings settings;
	private final Runnable scheduleSave;
	private final Screen parent;
	private final GuiMotion motion;

	private Category draggingCategory;
	private double dragOffsetX;
	private double dragOffsetY;
	private Module bindingModule;
	private KeybindSetting bindingSetting;
	private NumberSetting draggingSetting;

	MultiPanelClickGui(GuiSettings settings, Runnable scheduleSave, Screen parent, GuiMotion motion) {
		this.settings = settings;
		this.scheduleSave = scheduleSave;
		this.parent = parent;
		this.motion = motion;
	}

	Module expandedModule() {
		Object visible = motion.expandVisible();
		return visible instanceof Module module ? module : null;
	}

	Module bindingModule() {
		return bindingModule;
	}

	KeybindSetting bindingSetting() {
		return bindingSetting;
	}

	boolean isBinding() {
		return bindingModule != null || bindingSetting != null;
	}

	void clearBinding() {
		bindingModule = null;
		bindingSetting = null;
	}

	void setModuleBind(int key) {
		if (bindingModule != null) {
			bindingModule.setKeyBind(key);
		} else if (bindingSetting != null) {
			bindingSetting.setValue(key);
		}
		clearBinding();
	}

	void clearModuleBind() {
		if (bindingModule != null) {
			bindingModule.setKeyBind(GLFW.GLFW_KEY_UNKNOWN);
		} else if (bindingSetting != null) {
			bindingSetting.setValue(GLFW.GLFW_KEY_UNKNOWN);
		}
		clearBinding();
	}

	void onClose() {
		draggingCategory = null;
		draggingSetting = null;
		clearBinding();
		motion.clearExpand();
		scheduleSave.run();
	}

	void render(GuiGraphicsExtractor context, int mouseX, int mouseY, int screenW, int screenH, int slide) {
		boolean wurst = settings.getLayoutStyle() == GuiLayoutStyle.WURST;
		int accent = settings.getAccentColor();
		int panelW = wurst ? WURST_WIDTH : METEOR_WIDTH;

		context.fill(0, 0, screenW, screenH, GuiPaint.withAlpha(0x000000, motion.openAlpha(0x66)));

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate(0.0f, slide);

		for (Category category : Category.values()) {
			renderPanel(context, category, mouseX, mouseY, accent, panelW, wurst, slide);
		}

		renderToolbar(context, mouseX, mouseY, screenW, screenH, accent, wurst);

		if (isBinding()) {
			String target = bindingModule != null ? bindingModule.getName() : bindingSetting.getName();
			String text = "Press key to bind " + target + "  |  DEL clear  |  ESC cancel";
			int textWidth = Minecraft.getInstance().font.width(text);
			int boxX = screenW / 2 - textWidth / 2 - 10;
			GuiPaint.inset(context, boxX, screenH - 28 - slide, textWidth + 20, 18, 0xF014141C, accent);
			context.centeredText(Minecraft.getInstance().font, text, screenW / 2, screenH - 23 - slide, accent);
		}

		pose.popMatrix();
	}

	private void renderPanel(
		GuiGraphicsExtractor context,
		Category category,
		int mouseX,
		int mouseY,
		int accent,
		int panelW,
		boolean wurst,
		int slide
	) {
		CategoryPanelState.Panel panel = settings.getCategoryPanels().get(category);
		int x = panel.x();
		int y = panel.y();
		List<Module> modules = VirulentClient.getInstance().getModuleManager().getModulesByCategory(category);
		int bodyH = panel.collapsed() ? 0 : contentHeight(modules);
		int totalH = HEADER_H + bodyH;

		int bg = wurst ? 0xF0383838 : GuiPaint.WINDOW_BG;
		int headerBg = wurst ? (settings.getHeaderColor() | 0xFF000000) : GuiPaint.SIDEBAR_BG;
		int border = wurst ? 0xFF6A6A6A : GuiPaint.BORDER;
		int alpha = motion.openAlpha(0xF0);
		GuiPaint.fill(context, x, y, x + panelW, y + totalH, GuiPaint.withAlpha(bg, alpha));
		GuiPaint.border(context, x, y, panelW, totalH, GuiPaint.withAlpha(border, alpha));
		GuiPaint.fill(context, x + 1, y + 1, x + panelW - 1, y + HEADER_H, GuiPaint.withAlpha(headerBg, alpha));
		GuiPaint.topAccent(context, x, y, panelW, accent);

		String title = category.getDisplayName();
		context.text(Minecraft.getInstance().font, title, x + 5, y + 4, accent);
		context.text(
			Minecraft.getInstance().font,
			panel.collapsed() ? "+" : "-",
			x + panelW - 11,
			y + 4,
			wurst ? 0xFFE0E0E0 : GuiPaint.TEXT_DIM
		);

		if (panel.collapsed()) {
			return;
		}

		int rowY = y + HEADER_H;
		for (Module module : modules) {
			boolean rawHovered = hovered(mouseX, mouseY, x + 1, rowY, panelW - 2, ROW_H);
			boolean enabled = module.isEnabled();
			float hoverAmt = motion.hover(module, rawHovered && !enabled);
			int rowBg = enabled
				? (wurst ? 0xFF4A4A4A : GuiPaint.blend(accent, GuiPaint.WINDOW_INNER, 0.82f))
				: GuiPaint.blend(0x00000000, wurst ? 0xFF444444 : GuiPaint.PANEL_HOVER, hoverAmt);
			if ((rowBg >>> 24) != 0) {
				GuiPaint.fill(context, x + 1, rowY, x + panelW - 1, rowY + ROW_H, rowBg);
			}

			if (wurst) {
				context.text(
					Minecraft.getInstance().font,
					enabled ? "[x]" : "[ ]",
					x + 4,
					rowY + 3,
					enabled ? accent : 0xFFAAAAAA
				);
				context.text(
					Minecraft.getInstance().font,
					module.getName(),
					x + 24,
					rowY + 3,
					enabled ? 0xFFFFFF66 : 0xFFE8E8E8
				);
			} else {
				if (enabled) {
					GuiPaint.accentStrip(context, x + 1, rowY, ROW_H, accent);
				}
				context.text(
					Minecraft.getInstance().font,
					module.getName(),
					x + 7,
					rowY + 3,
					enabled ? accent : GuiPaint.TEXT
				);
			}

			rowY += ROW_H;
		}
	}

	private int contentHeight(List<Module> modules) {
		return modules.size() * ROW_H;
	}

	private void renderSetting(
		GuiGraphicsExtractor context,
		Setting<?> setting,
		int x,
		int y,
		int w,
		int mouseX,
		int mouseY,
		int accent
	) {
		boolean hovered = hovered(mouseX, mouseY, x, y, w, SETTING_H);
		GuiPaint.fill(context, x, y, x + w, y + SETTING_H, hovered ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG);
		var font = Minecraft.getInstance().font;

		if (setting instanceof BooleanSetting booleanSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, GuiPaint.TEXT);
			GuiPaint.toggle(context, x + w - 20, y + 2, booleanSetting.getValue(), accent);
			return;
		}

		if (setting instanceof NumberSetting numberSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, GuiPaint.TEXT);
			int sliderX = x + w / 2;
			int sliderW = w / 2 - 6;
			double range = numberSetting.getMax() - numberSetting.getMin();
			double percent = range <= 0 ? 0 : (numberSetting.getValue() - numberSetting.getMin()) / range;
			GuiPaint.slider(context, sliderX, y + 4, sliderW, percent, accent);
			return;
		}

		if (setting instanceof ModeSetting modeSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, GuiPaint.TEXT);
			context.text(font, modeSetting.getValue(), x + w - font.width(modeSetting.getValue()) - 4, y + 3, accent);
			return;
		}

		if (setting instanceof KeybindSetting keybindSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, GuiPaint.TEXT);
			String value = bindingSetting == keybindSetting ? "..." : KeybindUtil.getName(keybindSetting.getValue());
			context.text(font, value, x + w - font.width(value) - 4, y + 3, accent);
			return;
		}

		if (setting instanceof BlockListSetting blockListSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, GuiPaint.TEXT);
			String value = "Select (" + blockListSetting.size() + ")";
			context.text(font, value, x + w - font.width(value) - 4, y + 3, accent);
			return;
		}

		if (setting instanceof ActionSetting actionSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, GuiPaint.TEXT);
			context.text(font, actionSetting.getLabel(), x + w - font.width(actionSetting.getLabel()) - 4, y + 3, accent);
			return;
		}

		if (setting instanceof BlockEspConfigSetting || setting instanceof BlockEspConfigsSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, GuiPaint.TEXT);
			context.text(font, "Edit", x + w - font.width("Edit") - 4, y + 3, accent);
		}
	}

	private void renderToolbar(
		GuiGraphicsExtractor context,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH,
		int accent,
		boolean wurst
	) {
		int barW = 258;
		int barX = (screenW - barW) / 2;
		int barY = screenH - TOOLBAR_H - 6;
		GuiPaint.inset(context, barX, barY, barW, TOOLBAR_H,
			wurst ? 0xF0383838 : GuiPaint.WINDOW_BG,
			wurst ? 0xFF6A6A6A : GuiPaint.BORDER);
		GuiPaint.topAccent(context, barX, barY, barW, accent);

		var font = Minecraft.getInstance().font;
		int x = barX + 8;
		GuiPaint.inset(context, x, barY + 5, 8, 8, accent | 0xFF000000, GuiPaint.BORDER);
		x += 16;

		String layout = settings.getLayoutStyle().getLabel();
		context.text(font, layout, x, barY + 5, accent);
		x += font.width(layout) + 10;

		context.text(font, settings.showDescriptions() ? "Desc" : "desc", x, barY + 5,
			settings.showDescriptions() ? accent : GuiPaint.TEXT_MUTED);
		x += font.width("Desc") + 8;

		context.text(font, settings.showKeybinds() ? "Keys" : "keys", x, barY + 5,
			settings.showKeybinds() ? accent : GuiPaint.TEXT_MUTED);
		x += font.width("Keys") + 8;

		context.text(font, settings.isHudVisible() ? "Hud" : "hud", x, barY + 5,
			settings.isHudVisible() ? accent : GuiPaint.TEXT_MUTED);
		x += font.width("Hud") + 8;

		context.text(font, "Cfg", x, barY + 5, accent);
	}

	boolean mouseClicked(double mouseX, double mouseY, int button, int screenW, int screenH) {
		if (handleToolbarClick(mouseX, mouseY, screenW, screenH, button)) {
			return true;
		}

		boolean wurst = settings.getLayoutStyle() == GuiLayoutStyle.WURST;
		int panelW = wurst ? WURST_WIDTH : METEOR_WIDTH;

		Category[] categories = Category.values();
		for (int i = categories.length - 1; i >= 0; i--) {
			if (handlePanelClick(categories[i], mouseX, mouseY, button, panelW)) {
				return true;
			}
		}
		return false;
	}

	private boolean handleToolbarClick(double mouseX, double mouseY, int screenW, int screenH, int button) {
		if (button != 0) {
			return false;
		}
		int barW = 258;
		int barX = (screenW - barW) / 2;
		int barY = screenH - TOOLBAR_H - 6;
		if (!hovered(mouseX, mouseY, barX, barY, barW, TOOLBAR_H)) {
			return false;
		}

		var font = Minecraft.getInstance().font;
		int x = barX + 8;
		if (hovered(mouseX, mouseY, x, barY + 5, 8, 8)) {
			settings.cycleAccentPreset();
			return true;
		}
		x += 16;

		String layout = settings.getLayoutStyle().getLabel();
		if (hovered(mouseX, mouseY, x, barY, font.width(layout), TOOLBAR_H)) {
			settings.cycleLayoutStyle();
			return true;
		}
		x += font.width(layout) + 10;

		if (hovered(mouseX, mouseY, x, barY, font.width("Desc"), TOOLBAR_H)) {
			settings.toggleDescriptions();
			return true;
		}
		x += font.width("Desc") + 8;

		if (hovered(mouseX, mouseY, x, barY, font.width("Keys"), TOOLBAR_H)) {
			settings.toggleKeybinds();
			return true;
		}
		x += font.width("Keys") + 8;

		if (hovered(mouseX, mouseY, x, barY, font.width("Hud"), TOOLBAR_H)) {
			settings.toggleHudVisible();
			return true;
		}
		x += font.width("Hud") + 8;

		if (hovered(mouseX, mouseY, x, barY, font.width("Cfg"), TOOLBAR_H)) {
			Minecraft.getInstance().setScreen(new ProfilesScreen(parent));
			return true;
		}
		return true;
	}

	private boolean handlePanelClick(Category category, double mouseX, double mouseY, int button, int panelW) {
		CategoryPanelState.Panel panel = settings.getCategoryPanels().get(category);
		int x = panel.x();
		int y = panel.y();
		List<Module> modules = VirulentClient.getInstance().getModuleManager().getModulesByCategory(category);
		int bodyH = panel.collapsed() ? 0 : contentHeight(modules);
		int totalH = HEADER_H + bodyH;

		if (!hovered(mouseX, mouseY, x, y, panelW, totalH)) {
			return false;
		}

		if (hovered(mouseX, mouseY, x, y, panelW, HEADER_H)) {
			if (button == 0) {
				draggingCategory = category;
				dragOffsetX = mouseX - x;
				dragOffsetY = mouseY - y;
				return true;
			}
			if (button == 1) {
				settings.getCategoryPanels().set(category, x, y, !panel.collapsed());
				scheduleSave.run();
				return true;
			}
		}

		if (panel.collapsed() || button > 2) {
			return true;
		}

		int rowY = y + HEADER_H;
		for (Module module : modules) {
			if (hovered(mouseX, mouseY, x + 1, rowY, panelW - 2, ROW_H)) {
				if (button == 0) {
					module.toggle();
				} else if (button == 1) {
					Minecraft.getInstance().setScreen(new ModuleSettingsScreen(parent, module));
				} else if (button == 2 && settings.showKeybinds()) {
					bindingModule = module;
					bindingSetting = null;
				}
				return true;
			}
			rowY += ROW_H;
		}
		return true;
	}

	private boolean handleSettingClick(Module module, Setting<?> setting, double mouseX, int button, int x, int y, int w) {
		if (setting instanceof BooleanSetting booleanSetting && button == 0) {
			booleanSetting.toggle();
			return true;
		}
		if (setting instanceof NumberSetting numberSetting && button == 0) {
			draggingSetting = numberSetting;
			updateNumber(numberSetting, mouseX, x + w / 2, w / 2 - 6);
			return true;
		}
		if (setting instanceof ModeSetting modeSetting) {
			if (button == 0) {
				modeSetting.cycle();
			} else if (button == 1) {
				int index = modeSetting.getModes().indexOf(modeSetting.getValue());
				int previous = (index - 1 + modeSetting.getModes().size()) % modeSetting.getModes().size();
				modeSetting.setValue(modeSetting.getModes().get(previous));
			}
			return true;
		}
		if (setting instanceof KeybindSetting keybindSetting && button == 0) {
			bindingSetting = keybindSetting;
			bindingModule = null;
			return true;
		}
		if (setting instanceof ActionSetting && button == 0) {
			if ("SeedCracker".equals(module.getName())) {
				Minecraft.getInstance().setScreen(new SeedCrackerScreen(parent));
			} else if ("Friends".equals(module.getName())) {
				Minecraft.getInstance().setScreen(new FriendsScreen(parent));
			} else {
				Minecraft.getInstance().setScreen(new WaypointsScreen(parent));
			}
			return true;
		}
		if (setting instanceof BlockListSetting blockListSetting && button == 0) {
			Screen parentScreen = Minecraft.getInstance().screen;
			Minecraft.getInstance().setScreen(new BlockListScreen(parentScreen, blockListSetting));
			return true;
		}
		if (setting instanceof BlockEspConfigSetting configSetting && button == 0) {
			Screen parent = Minecraft.getInstance().screen;
			Minecraft.getInstance().setScreen(new BlockEspConfigScreen(
				parent,
				configSetting.getName(),
				configSetting.getValue(),
				configSetting::setValue
			));
			return true;
		}
		if (setting instanceof BlockEspConfigsSetting && button == 0 && expandedModule() instanceof BlockEsp blockEsp) {
			Screen parent = Minecraft.getInstance().screen;
			Minecraft.getInstance().setScreen(new BlockEspConfigsScreen(
				parent,
				blockEsp.getBlocks(),
				blockEsp.getDefaultBlockConfig(),
				blockEsp.getBlockConfigs()
			));
			return true;
		}
		return false;
	}

	boolean mouseDragged(double mouseX, double mouseY, int screenW, int screenH) {
		if (draggingCategory != null) {
			int x = clamp((int) (mouseX - dragOffsetX), 0, screenW - 40);
			int y = clamp((int) (mouseY - dragOffsetY), 0, screenH - HEADER_H);
			CategoryPanelState.Panel panel = settings.getCategoryPanels().get(draggingCategory);
			settings.getCategoryPanels().set(draggingCategory, x, y, panel.collapsed());
			return true;
		}
		if (draggingSetting != null) {
			return true;
		}
		return false;
	}

	boolean mouseReleased() {
		boolean was = draggingCategory != null || draggingSetting != null;
		if (draggingCategory != null) {
			scheduleSave.run();
		}
		draggingCategory = null;
		draggingSetting = null;
		return was;
	}

	private static void updateNumber(NumberSetting setting, double mouseX, int sliderX, int sliderW) {
		double percent = Math.max(0, Math.min(1, (mouseX - sliderX) / (double) sliderW));
		double value = setting.getMin() + percent * (setting.getMax() - setting.getMin());
		double steps = Math.round((value - setting.getMin()) / setting.getIncrement());
		setting.setValue(setting.getMin() + steps * setting.getIncrement());
	}

	private static void drawTruncated(GuiGraphicsExtractor context, String text, int x, int y, int maxWidth, int color) {
		String line = text;
		var font = Minecraft.getInstance().font;
		if (font.width(line) > maxWidth) {
			while (line.length() > 3 && font.width(line + "...") > maxWidth) {
				line = line.substring(0, line.length() - 1);
			}
			line += "...";
		}
		context.text(font, line, x, y, color);
	}

	private static boolean hovered(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
