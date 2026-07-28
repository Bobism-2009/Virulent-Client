package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.config.GuiSettings;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Centered Manage-style settings panel for a single module.
 */
public final class ModuleSettingsScreen extends Screen {
	private static final int PAD = 8;
	private static final int ROW_H = 15;
	private static final int KEYBIND_H = 14;
	private static final int RESET_H = 14;
	private static final int DESC_H = 10;

	private final Screen parent;
	private final Module module;
	private final GuiMotion motion = new GuiMotion();

	private int scroll;
	private KeybindSetting bindingSetting;
	private boolean bindingModuleKey;
	private NumberSetting draggingSetting;

	public ModuleSettingsScreen(Screen parent, Module module) {
		super(Component.literal(module.getName() + " Settings"));
		this.parent = parent;
		this.module = module;
		motion.beginOpen();
	}

	private GuiSettings gui() {
		return VirulentClient.getInstance().getGuiSettings();
	}

	private int accent() {
		return gui().getAccentColor();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		motion.beginFrame();
		int slide = motion.openSlidePx(18);
		int overlayAlpha = motion.openAlpha(0x88);

		context.fill(0, 0, width, height, GuiPaint.withAlpha(0x000000, overlayAlpha));

		int panelW = Math.min(380, width - 40);
		int panelH = Math.min(340, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2 + slide;
		int accent = accent();

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate(0.0f, 0.0f);

		GuiPaint.inset(context, panelX, panelY, panelW, panelH,
			GuiPaint.withAlpha(GuiPaint.WINDOW_BG, Math.max(0xE0, motion.openAlpha(0xF0))),
			GuiPaint.withAlpha(GuiPaint.BORDER, motion.openAlpha(0xFF)));
		GuiPaint.topAccent(context, panelX, panelY, panelW, accent);

		var font = Minecraft.getInstance().font;
		context.text(font, module.getName(), panelX + PAD, panelY + 6, accent);
		context.text(font, module.getCategory().getDisplayName() + "  ·  Settings",
			panelX + PAD, panelY + 17, GuiPaint.TEXT_MUTED);

		boolean enabled = module.isEnabled();
		String state = enabled ? "ON" : "OFF";
		int stateW = font.width(state) + 14;
		int stateX = panelX + panelW - PAD - stateW - 52;
		boolean stateHover = mouseX >= stateX && mouseX < stateX + stateW
			&& mouseY >= panelY + 6 && mouseY < panelY + 22;
		GuiPaint.inset(context, stateX, panelY + 6, stateW, 16,
			stateHover ? GuiPaint.PANEL_HOVER : (enabled ? GuiPaint.blend(accent, GuiPaint.PANEL_BG, 0.7f) : GuiPaint.SIDEBAR_BG),
			enabled ? accent : GuiPaint.BORDER);
		context.centeredText(font, state, stateX + stateW / 2, panelY + 10,
			enabled ? accent : GuiPaint.TEXT_DIM);

		drawButton(context, panelX + panelW - PAD - 48, panelY + 6, 48, "Done", mouseX, mouseY,
			0xFF284028, 0xFF88FF88);

		int bodyTop = panelY + 28;
		int bodyBottom = panelY + panelH - 10;
		int bodyH = Math.max(0, bodyBottom - bodyTop);
		int contentH = contentHeight();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - bodyH)));

		GuiPaint.inset(context, panelX + PAD, bodyTop, panelW - PAD * 2, bodyH,
			GuiPaint.WINDOW_INNER, GuiPaint.BORDER_SOFT);

		context.enableScissor(panelX + PAD, bodyTop, panelX + panelW - PAD, bodyBottom);
		int y = bodyTop + 4 - scroll;
		int rowX = panelX + PAD + 4;
		int rowW = panelW - PAD * 2 - 8;

		if (gui().showDescriptions()) {
			drawWrapped(context, module.getDescription(), rowX, y, rowW, GuiPaint.TEXT_DIM);
			y += DESC_H * 2 + 2;
		}

		boolean bindHover = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + KEYBIND_H;
		GuiPaint.fill(context, rowX, y, rowX + rowW, y + KEYBIND_H,
			bindHover ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG);
		String bindText = bindingModuleKey
			? "Bind: ..."
			: "Bind: " + KeybindUtil.getName(module.getKeyBind());
		context.text(font, bindText, rowX + 5, y + 3, accent);
		y += KEYBIND_H + 2;

		for (Setting<?> setting : module.getSettings()) {
			renderSetting(context, setting, rowX, y, rowW, mouseX, mouseY, accent);
			y += ROW_H + 1;
		}

		y += 3;
		boolean resetHover = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + RESET_H;
		GuiPaint.fill(context, rowX, y, rowX + rowW, y + RESET_H,
			resetHover ? GuiPaint.DANGER_HOVER : GuiPaint.DANGER_BG);
		context.centeredText(font, "Reset Defaults", rowX + rowW / 2, y + 3,
			resetHover ? 0xFFFF9999 : GuiPaint.DANGER);
		context.disableScissor();

		if (contentH > bodyH) {
			int trackX = panelX + panelW - PAD - 4;
			GuiPaint.fill(context, trackX, bodyTop + 2, trackX + 2, bodyBottom - 2, GuiPaint.TRACK);
			double ratio = (double) bodyH / contentH;
			int thumbH = Math.max(12, (int) (bodyH * ratio));
			int thumbY = bodyTop + 2 + (int) ((bodyH - 4 - thumbH) * ((double) scroll / (contentH - bodyH)));
			GuiPaint.fill(context, trackX, thumbY, trackX + 2, thumbY + thumbH, accent | 0xFF000000);
		}

		if (bindingModuleKey || bindingSetting != null) {
			String target = bindingModuleKey ? module.getName() : bindingSetting.getName();
			String text = "Press key to bind " + target + "  |  DEL clear  |  ESC cancel";
			int textW = font.width(text);
			int boxX = width / 2 - textW / 2 - 10;
			GuiPaint.inset(context, boxX, height - 28, textW + 20, 18, 0xF014141C, accent);
			context.centeredText(font, text, width / 2, height - 23, accent);
		}

		pose.popMatrix();
		motion.endFrame();

		if (motion.isFullyClosed()) {
			Minecraft.getInstance().setScreen(parent);
		}
	}

	private int contentHeight() {
		int h = 4;
		if (gui().showDescriptions()) {
			h += DESC_H * 2 + 2;
		}
		h += KEYBIND_H + 2;
		h += module.getSettings().size() * (ROW_H + 1);
		h += 3 + RESET_H + 6;
		return h;
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
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;
		GuiPaint.fill(context, x, y, x + w, y + ROW_H, hover ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG);
		var font = Minecraft.getInstance().font;

		if (setting instanceof BooleanSetting booleanSetting) {
			context.text(font, setting.getName(), x + 5, y + 3, GuiPaint.TEXT);
			GuiPaint.toggle(context, x + w - 22, y + 3, booleanSetting.getValue(), accent);
			return;
		}
		if (setting instanceof NumberSetting numberSetting) {
			context.text(font, setting.getName(), x + 5, y + 3, GuiPaint.TEXT);
			int sliderX = x + w / 2;
			int sliderW = w / 2 - 8;
			double range = numberSetting.getMax() - numberSetting.getMin();
			double percent = range <= 0 ? 0 : (numberSetting.getValue() - numberSetting.getMin()) / range;
			GuiPaint.slider(context, sliderX, y + 5, sliderW, percent, accent);
			String valueText = String.format("%.1f", numberSetting.getValue());
			context.text(font, valueText, sliderX + sliderW - font.width(valueText), y + 1, accent);
			return;
		}
		if (setting instanceof ModeSetting modeSetting) {
			context.text(font, setting.getName(), x + 5, y + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, modeSetting.getValue(), x + w - 4, y + 2, accent, true);
			return;
		}
		if (setting instanceof KeybindSetting keybindSetting) {
			String value = bindingSetting == keybindSetting ? "..." : KeybindUtil.getName(keybindSetting.getValue());
			context.text(font, setting.getName(), x + 5, y + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, value, x + w - 4, y + 2, accent, true);
			return;
		}
		if (setting instanceof BlockListSetting blockListSetting) {
			context.text(font, setting.getName(), x + 5, y + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, "Select (" + blockListSetting.size() + ")", x + w - 4, y + 2, accent, true);
			return;
		}
		if (setting instanceof ActionSetting actionSetting) {
			context.text(font, setting.getName(), x + 5, y + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, actionSetting.getLabel(), x + w - 4, y + 2, accent, true);
			return;
		}
		if (setting instanceof BlockEspConfigSetting || setting instanceof BlockEspConfigsSetting) {
			context.text(font, setting.getName(), x + 5, y + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, "Edit", x + w - 4, y + 2, accent, true);
		}
	}

	private void drawButton(
		GuiGraphicsExtractor context,
		int x,
		int y,
		int w,
		String label,
		int mouseX,
		int mouseY,
		int bg,
		int fg
	) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
		GuiPaint.inset(context, x, y, w, 16, hover ? GuiPaint.PANEL_HOVER : bg, GuiPaint.BORDER);
		var font = Minecraft.getInstance().font;
		context.text(font, label, x + (w - font.width(label)) / 2, y + 4, fg);
	}

	private void drawWrapped(GuiGraphicsExtractor context, String text, int x, int y, int maxWidth, int color) {
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

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (motion.isClosing()) {
			return true;
		}

		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		int panelW = Math.min(380, width - 40);
		int panelH = Math.min(340, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2 + motion.openSlidePx(18);

		if (button == 0 && mouseX >= panelX + panelW - PAD - 48 && mouseX < panelX + panelW - PAD
			&& mouseY >= panelY + 6 && mouseY < panelY + 22) {
			closeAnimated();
			return true;
		}

		String state = module.isEnabled() ? "ON" : "OFF";
		int stateW = Minecraft.getInstance().font.width(state) + 14;
		int stateX = panelX + panelW - PAD - stateW - 52;
		if (button == 0 && mouseX >= stateX && mouseX < stateX + stateW
			&& mouseY >= panelY + 6 && mouseY < panelY + 22) {
			module.toggle();
			return true;
		}

		int bodyTop = panelY + 28;
		int bodyBottom = panelY + panelH - 10;
		int y = bodyTop + 4 - scroll;
		int rowX = panelX + PAD + 4;
		int rowW = panelW - PAD * 2 - 8;

		if (gui().showDescriptions()) {
			y += DESC_H * 2 + 2;
		}

		if (button == 0 && mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + KEYBIND_H) {
			bindingModuleKey = true;
			bindingSetting = null;
			return true;
		}
		y += KEYBIND_H + 2;

		for (Setting<?> setting : module.getSettings()) {
			if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + ROW_H) {
				if (handleSettingClick(setting, mouseX, button, rowX, y, rowW)) {
					return true;
				}
			}
			y += ROW_H + 1;
		}

		y += 3;
		if (button == 0 && mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + RESET_H) {
			module.resetToDefaults();
			return true;
		}

		if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
			if (button == 0) {
				closeAnimated();
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	private boolean handleSettingClick(Setting<?> setting, double mouseX, int button, int x, int y, int w) {
		if (setting instanceof BooleanSetting booleanSetting && button == 0) {
			booleanSetting.toggle();
			return true;
		}
		if (setting instanceof NumberSetting numberSetting) {
			if (button == 0) {
				draggingSetting = numberSetting;
				updateNumber(numberSetting, mouseX, x + w / 2, w / 2 - 8);
				return true;
			}
			if (button == 1) {
				double next = numberSetting.getValue() - numberSetting.getIncrement();
				if (next < numberSetting.getMin()) {
					next = numberSetting.getMax();
				}
				numberSetting.setValue(next);
				return true;
			}
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
			bindingModuleKey = false;
			return true;
		}
		if (setting instanceof ActionSetting && button == 0) {
			if ("SeedCracker".equals(module.getName())) {
				Minecraft.getInstance().setScreen(new SeedCrackerScreen(this));
			} else if ("Friends".equals(module.getName())) {
				Minecraft.getInstance().setScreen(new FriendsScreen(this));
			} else {
				Minecraft.getInstance().setScreen(new WaypointsScreen(this));
			}
			return true;
		}
		if (setting instanceof BlockListSetting blockListSetting && button == 0) {
			Minecraft.getInstance().setScreen(new BlockListScreen(this, blockListSetting));
			return true;
		}
		if (setting instanceof BlockEspConfigSetting configSetting && button == 0) {
			Minecraft.getInstance().setScreen(new BlockEspConfigScreen(
				this,
				configSetting.getName(),
				configSetting.getValue(),
				configSetting::setValue
			));
			return true;
		}
		if (setting instanceof BlockEspConfigsSetting && button == 0 && module instanceof BlockEsp blockEsp) {
			Minecraft.getInstance().setScreen(new BlockEspConfigsScreen(
				this,
				blockEsp.getBlocks(),
				blockEsp.getDefaultBlockConfig(),
				blockEsp.getBlockConfigs()
			));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (draggingSetting != null) {
			int panelW = Math.min(380, width - 40);
			int panelX = (width - panelW) / 2;
			int rowX = panelX + PAD + 4;
			int rowW = panelW - PAD * 2 - 8;
			updateNumber(draggingSetting, event.x(), rowX + rowW / 2, rowW / 2 - 8);
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingSetting = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical) * 14);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (bindingModuleKey || bindingSetting != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				bindingModuleKey = false;
				bindingSetting = null;
				return true;
			}
			if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
				if (bindingModuleKey) {
					module.setKeyBind(GLFW.GLFW_KEY_UNKNOWN);
				} else {
					bindingSetting.setValue(GLFW.GLFW_KEY_UNKNOWN);
				}
				bindingModuleKey = false;
				bindingSetting = null;
				return true;
			}
			if (bindingModuleKey) {
				module.setKeyBind(key);
			} else {
				bindingSetting.setValue(key);
			}
			bindingModuleKey = false;
			bindingSetting = null;
			return true;
		}

		if (key == GLFW.GLFW_KEY_ESCAPE) {
			closeAnimated();
			return true;
		}
		return super.keyPressed(event);
	}

	private void closeAnimated() {
		bindingModuleKey = false;
		bindingSetting = null;
		draggingSetting = null;
		if (!motion.beginClose()) {
			Minecraft.getInstance().setScreen(parent);
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static void updateNumber(NumberSetting setting, double mouseX, int sliderX, int sliderW) {
		double percent = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
		double value = setting.getMin() + percent * (setting.getMax() - setting.getMin());
		double steps = Math.round((value - setting.getMin()) / setting.getIncrement());
		setting.setValue(setting.getMin() + steps * setting.getIncrement());
	}
}
