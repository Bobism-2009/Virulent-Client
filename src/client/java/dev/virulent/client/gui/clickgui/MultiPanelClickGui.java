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
	private static final int HEADER_H = 17;
	private static final int ROW_H = 17;
	private static final int SETTING_H = 15;
	private static final int KEYBIND_H = 15;
	private static final int RESET_H = 15;
	private static final int DESC_H = 9;
	private static final int TOOLBAR_H = 18;
	/** Unscaled Meteor layout units (drawn with {@link #METEOR_SCALE}). */
	private static final int METEOR_HEADER_H = 14;
	private static final int METEOR_ROW_H = 14;
	private static final int METEOR_MIN_WIDTH = 80;
	/** Visual scale — Meteor Client windows read much smaller than vanilla GUI widgets. */
	private static final float METEOR_SCALE = 0.72f;
	private static final int WURST_WIDTH = 104;
	/** Meteor body: rgb(20,20,20) @ ~200 alpha. */
	private static final int METEOR_BODY = 0xC8141414;
	/** Meteor active module wash: rgb(50,50,50). */
	private static final int METEOR_MODULE_ON = 0xFF323232;
	private static final int METEOR_MODULE_HOVER = 0xFF2A2A2A;
	private static final int METEOR_OUTLINE = 0xFF000000;

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
		boolean meteor = settings.getLayoutStyle() == GuiLayoutStyle.METEOR;
		int accent = settings.getAccentColor();
		GuiPaint.Palette p = GuiPaint.palette(accent, settings.isAccentBlack());

		context.fill(0, 0, screenW, screenH, GuiPaint.withAlpha(0x000000, motion.openAlpha(meteor ? 0x55 : 0x66)));

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate(0.0f, slide);

		for (Category category : Category.values()) {
			int panelW = wurst ? WURST_WIDTH : meteorPanelWidth(category);
			renderPanel(context, category, mouseX, mouseY, p, accent, panelW, wurst, meteor, slide);
		}

		renderToolbar(context, mouseX, mouseY, screenW, screenH, accent, p, wurst);

		if (isBinding()) {
			String target = bindingModule != null ? bindingModule.getName() : bindingSetting.getName();
			String text = "Press key to bind " + target + "  |  DEL clear  |  ESC cancel";
			int textWidth = Minecraft.getInstance().font.width(text);
			int boxX = screenW / 2 - textWidth / 2 - 10;
			GuiPaint.inset(context, boxX, screenH - 28 - slide, textWidth + 20, 18, 0xF014141C, p.fg());
			context.centeredText(Minecraft.getInstance().font, text, screenW / 2, screenH - 23 - slide, p.fg());
		}

		pose.popMatrix();
	}

	private int meteorPanelWidth(Category category) {
		var font = Minecraft.getInstance().font;
		int width = Math.max(METEOR_MIN_WIDTH, font.width(category.getDisplayName()) + 22);
		for (Module module : VirulentClient.getInstance().getModuleManager().getModulesByCategory(category)) {
			width = Math.max(width, font.width(module.getName()) + 12);
		}
		return width;
	}

	private int meteorScreenWidth(Category category) {
		return Math.round(meteorPanelWidth(category) * METEOR_SCALE);
	}

	private int meteorContentHeight(List<Module> modules) {
		return modules.size() * METEOR_ROW_H;
	}

	private int meteorScreenHeight(List<Module> modules, boolean collapsed) {
		int body = collapsed ? 0 : meteorContentHeight(modules);
		return Math.round((METEOR_HEADER_H + body) * METEOR_SCALE);
	}

	private void renderPanel(
		GuiGraphicsExtractor context,
		Category category,
		int mouseX,
		int mouseY,
		GuiPaint.Palette p,
		int accent,
		int panelW,
		boolean wurst,
		boolean meteor,
		int slide
	) {
		CategoryPanelState.Panel panel = settings.getCategoryPanels().get(category);
		int x = panel.x();
		int y = panel.y();
		List<Module> modules = VirulentClient.getInstance().getModuleManager().getModulesByCategory(category);
		boolean black = settings.isAccentBlack();
		int alpha = motion.openAlpha(0xF0);
		var font = Minecraft.getInstance().font;

		if (meteor) {
			int bodyH = panel.collapsed() ? 0 : meteorContentHeight(modules);
			int localH = METEOR_HEADER_H + bodyH;
			renderMeteorPanel(context, category, modules, panel, mouseX, mouseY, x, y, panelW, localH, bodyH, accent, black, alpha);
			return;
		}

		int bodyH = panel.collapsed() ? 0 : contentHeight(modules);
		int totalH = HEADER_H + bodyH;

		int bg = wurst && !black ? 0xF0383838 : p.windowBg();
		int headerBg = wurst && !black ? (settings.getHeaderColor() | 0xFF000000) : p.sidebarBg();
		int border = wurst && !black ? 0xFF6A6A6A : p.border();
		GuiPaint.fill(context, x, y, x + panelW, y + totalH, GuiPaint.withAlpha(bg, alpha));
		GuiPaint.border(context, x, y, panelW, totalH, GuiPaint.withAlpha(border, alpha));
		GuiPaint.fill(context, x + 1, y + 1, x + panelW - 1, y + HEADER_H, GuiPaint.withAlpha(headerBg, alpha));
		GuiPaint.topAccent(context, x, y, panelW, p.fg());

		String title = category.getDisplayName();
		context.text(font, title, x + 5, y + (HEADER_H - 8) / 2, p.fg());
		context.text(
			font,
			panel.collapsed() ? "+" : "-",
			x + panelW - 11,
			y + (HEADER_H - 8) / 2,
			wurst && !black ? 0xFFE0E0E0 : p.textDim()
		);

		if (panel.collapsed()) {
			return;
		}

		int rowY = y + HEADER_H;
		for (Module module : modules) {
			boolean rawHovered = hovered(mouseX, mouseY, x + 1, rowY, panelW - 2, ROW_H);
			boolean enabled = module.isEnabled();
			float hoverAmt = motion.hover(module, rawHovered && !enabled);
			int rowBg;
			if (enabled) {
				rowBg = wurst && !black ? 0xFF4A4A4A : (black ? p.panelHover() : GuiPaint.blend(p.fg(), p.windowInner(), 0.82f));
			} else {
				rowBg = GuiPaint.blend(0x00000000, wurst && !black ? 0xFF444444 : p.panelHover(), hoverAmt);
			}
			if ((rowBg >>> 24) != 0) {
				GuiPaint.fill(context, x + 1, rowY, x + panelW - 1, rowY + ROW_H, rowBg);
			}

			context.text(
				font,
				enabled ? "[x]" : "[ ]",
				x + 4,
				rowY + 3,
				enabled ? p.fg() : (black ? p.textMuted() : 0xFFAAAAAA)
			);
			context.text(
				font,
				module.getName(),
				x + 24,
				rowY + 3,
				enabled ? (black ? p.fg() : 0xFFFFFF66) : (black ? p.text() : 0xFFE8E8E8)
			);

			rowY += ROW_H;
		}
	}

	private void renderMeteorPanel(
		GuiGraphicsExtractor context,
		Category category,
		List<Module> modules,
		CategoryPanelState.Panel panel,
		int mouseX,
		int mouseY,
		int x,
		int y,
		int panelW,
		int totalH,
		int bodyH,
		int accent,
		boolean black,
		int alpha
	) {
		var font = Minecraft.getInstance().font;
		int headerColor = black ? 0xFF000000 : (accent | 0xFF000000);
		int bodyColor = black ? 0xE0000000 : METEOR_BODY;
		int onColor = black ? 0xFF1A1A1A : METEOR_MODULE_ON;
		int hoverColor = black ? 0xFF141414 : METEOR_MODULE_HOVER;
		int textWhite = 0xFFFFFFFF;
		int stripColor = black ? 0xFFFFFFFF : (accent | 0xFF000000);

		int localH = METEOR_HEADER_H + bodyH;
		float inv = 1.0f / METEOR_SCALE;
		float localMouseX = (mouseX - x) * inv;
		float localMouseY = (mouseY - y) * inv;

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(METEOR_SCALE, METEOR_SCALE);

		if (bodyH > 0) {
			GuiPaint.fill(context, 0, METEOR_HEADER_H, panelW, localH, bodyColor);
		}
		GuiPaint.border(context, 0, 0, panelW, localH, GuiPaint.withAlpha(METEOR_OUTLINE, alpha));
		GuiPaint.fill(context, 0, 0, panelW, METEOR_HEADER_H, GuiPaint.withAlpha(headerColor, alpha));

		String title = category.getDisplayName();
		context.text(font, title, (panelW - font.width(title)) / 2, (METEOR_HEADER_H - 8) / 2, textWhite);
		context.text(font, panel.collapsed() ? "+" : "-", panelW - 10, (METEOR_HEADER_H - 8) / 2, textWhite);

		if (!panel.collapsed()) {
			int rowY = METEOR_HEADER_H;
			for (Module module : modules) {
				boolean rawHovered = localMouseX >= 0 && localMouseX <= panelW
					&& localMouseY >= rowY && localMouseY <= rowY + METEOR_ROW_H;
				boolean enabled = module.isEnabled();
				float hoverAmt = motion.hover(module, rawHovered && !enabled);

				if (enabled) {
					GuiPaint.fill(context, 0, rowY, panelW, rowY + METEOR_ROW_H, onColor);
					GuiPaint.accentStrip(context, 0, rowY, METEOR_ROW_H, stripColor);
				} else if (hoverAmt > 0.01f) {
					GuiPaint.fill(context, 0, rowY, panelW, rowY + METEOR_ROW_H,
						GuiPaint.withAlpha(hoverColor, (int) (0xAA * hoverAmt)));
				}

				String name = module.getName();
				context.text(font, name, (panelW - font.width(name)) / 2, rowY + (METEOR_ROW_H - 8) / 2, textWhite);
				rowY += METEOR_ROW_H;
			}
		}

		pose.popMatrix();
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
		GuiPaint.Palette p
	) {
		boolean hovered = hovered(mouseX, mouseY, x, y, w, SETTING_H);
		GuiPaint.fill(context, x, y, x + w, y + SETTING_H, hovered ? p.panelHover() : p.panelBg());
		var font = Minecraft.getInstance().font;

		if (setting instanceof BooleanSetting booleanSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, p.text());
			GuiPaint.toggle(context, x + w - 20, y + 2, booleanSetting.getValue(), p.fg(), p.border());
			return;
		}

		if (setting instanceof NumberSetting numberSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, p.text());
			int sliderX = x + w / 2;
			int sliderW = w / 2 - 6;
			double range = numberSetting.getMax() - numberSetting.getMin();
			double percent = range <= 0 ? 0 : (numberSetting.getValue() - numberSetting.getMin()) / range;
			GuiPaint.slider(context, sliderX, y + 4, sliderW, percent, p.fg(), p.track());
			return;
		}

		if (setting instanceof ModeSetting modeSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, p.text());
			context.text(font, modeSetting.getValue(), x + w - font.width(modeSetting.getValue()) - 4, y + 3, p.fg());
			return;
		}

		if (setting instanceof KeybindSetting keybindSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, p.text());
			String value = bindingSetting == keybindSetting ? "..." : KeybindUtil.getName(keybindSetting.getValue());
			context.text(font, value, x + w - font.width(value) - 4, y + 3, p.fg());
			return;
		}

		if (setting instanceof BlockListSetting blockListSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, p.text());
			String value = "Select (" + blockListSetting.size() + ")";
			context.text(font, value, x + w - font.width(value) - 4, y + 3, p.fg());
			return;
		}

		if (setting instanceof ActionSetting actionSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, p.text());
			context.text(font, actionSetting.getLabel(), x + w - font.width(actionSetting.getLabel()) - 4, y + 3, p.fg());
			return;
		}

		if (setting instanceof BlockEspConfigSetting || setting instanceof BlockEspConfigsSetting) {
			context.text(font, setting.getName(), x + 4, y + 3, p.text());
			context.text(font, "Edit", x + w - font.width("Edit") - 4, y + 3, p.fg());
		}
	}

	private void renderToolbar(
		GuiGraphicsExtractor context,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH,
		int accent,
		GuiPaint.Palette p,
		boolean wurst
	) {
		boolean black = settings.isAccentBlack();
		int barW = 258;
		int barX = (screenW - barW) / 2;
		int barY = screenH - TOOLBAR_H - 6;
		GuiPaint.inset(context, barX, barY, barW, TOOLBAR_H,
			wurst && !black ? 0xF0383838 : p.windowBg(),
			wurst && !black ? 0xFF6A6A6A : p.border());
		GuiPaint.topAccent(context, barX, barY, barW, p.fg());

		var font = Minecraft.getInstance().font;
		int x = barX + 8;
		GuiPaint.inset(context, x, barY + 5, 8, 8, accent | 0xFF000000,
			black ? 0xFFE8E8F0 : p.border());
		x += 16;

		String layout = settings.getLayoutStyle().getLabel();
		context.text(font, layout, x, barY + 5, p.fg());
		x += font.width(layout) + 10;

		context.text(font, settings.showDescriptions() ? "Desc" : "desc", x, barY + 5,
			settings.showDescriptions() ? p.fg() : p.textMuted());
		x += font.width("Desc") + 8;

		context.text(font, settings.showKeybinds() ? "Keys" : "keys", x, barY + 5,
			settings.showKeybinds() ? p.fg() : p.textMuted());
		x += font.width("Keys") + 8;

		context.text(font, settings.isHudVisible()
				? (settings.isHudBlack() ? "HudB" : "Hud")
				: "hud",
			x, barY + 5,
			!settings.isHudVisible()
				? p.textMuted()
				: (settings.isHudBlack() ? 0xFFFFFFFF : p.fg()));
		x += font.width(settings.isHudVisible()
			? (settings.isHudBlack() ? "HudB" : "Hud")
			: "hud") + 8;

		context.text(font, "Cfg", x, barY + 5, p.fg());
	}

	boolean mouseClicked(double mouseX, double mouseY, int button, int screenW, int screenH) {
		if (handleToolbarClick(mouseX, mouseY, screenW, screenH, button)) {
			return true;
		}

		boolean wurst = settings.getLayoutStyle() == GuiLayoutStyle.WURST;
		Category[] categories = Category.values();
		for (int i = categories.length - 1; i >= 0; i--) {
			int panelW = wurst ? WURST_WIDTH : meteorScreenWidth(categories[i]);
			if (handlePanelClick(categories[i], mouseX, mouseY, button, panelW, !wurst)) {
				return true;
			}
		}
		return false;
	}

	private boolean handleToolbarClick(double mouseX, double mouseY, int screenW, int screenH, int button) {
		if (button != 0 && button != 1) {
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
		if (button == 0 && hovered(mouseX, mouseY, x, barY + 5, 8, 8)) {
			settings.cycleAccentPreset();
			return true;
		}
		x += 16;

		String layout = settings.getLayoutStyle().getLabel();
		if (button == 0 && hovered(mouseX, mouseY, x, barY, font.width(layout), TOOLBAR_H)) {
			settings.cycleLayoutStyle();
			return true;
		}
		x += font.width(layout) + 10;

		if (button == 0 && hovered(mouseX, mouseY, x, barY, font.width("Desc"), TOOLBAR_H)) {
			settings.toggleDescriptions();
			return true;
		}
		x += font.width("Desc") + 8;

		if (button == 0 && hovered(mouseX, mouseY, x, barY, font.width("Keys"), TOOLBAR_H)) {
			settings.toggleKeybinds();
			return true;
		}
		x += font.width("Keys") + 8;

		String hudLabel = settings.isHudVisible()
			? (settings.isHudBlack() ? "HudB" : "Hud")
			: "hud";
		if (hovered(mouseX, mouseY, x, barY, font.width(hudLabel), TOOLBAR_H)) {
			if (button == 1) {
				settings.toggleHudBlack();
			} else {
				settings.toggleHudVisible();
			}
			return true;
		}
		x += font.width(hudLabel) + 8;

		if (button == 0 && hovered(mouseX, mouseY, x, barY, font.width("Cfg"), TOOLBAR_H)) {
			Minecraft.getInstance().setScreen(new ProfilesScreen(parent));
			return true;
		}
		return button == 0;
	}

	private boolean handlePanelClick(Category category, double mouseX, double mouseY, int button, int panelW, boolean meteor) {
		CategoryPanelState.Panel panel = settings.getCategoryPanels().get(category);
		int x = panel.x();
		int y = panel.y();
		List<Module> modules = VirulentClient.getInstance().getModuleManager().getModulesByCategory(category);

		int headerH;
		int rowH;
		int totalH;
		if (meteor) {
			headerH = Math.round(METEOR_HEADER_H * METEOR_SCALE);
			rowH = Math.round(METEOR_ROW_H * METEOR_SCALE);
			totalH = meteorScreenHeight(modules, panel.collapsed());
		} else {
			headerH = HEADER_H;
			rowH = ROW_H;
			int bodyH = panel.collapsed() ? 0 : contentHeight(modules);
			totalH = HEADER_H + bodyH;
		}

		if (!hovered(mouseX, mouseY, x, y, panelW, totalH)) {
			return false;
		}

		if (hovered(mouseX, mouseY, x, y, panelW, headerH)) {
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

		int rowY = y + headerH;
		for (Module module : modules) {
			if (hovered(mouseX, mouseY, x, rowY, panelW, rowH)) {
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
			rowY += rowH;
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
