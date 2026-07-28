package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.config.GuiSettings;
import dev.virulent.client.input.ClientKeybinds;
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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public final class ClickGuiScreen extends Screen {
	private static final int HEADER_HEIGHT = 18;
	private static final int SIDEBAR_WIDTH = 84;
	private static final int SEARCH_HEIGHT = 16;
	private static final int FOOTER_HEIGHT = 18;
	private static final int FOOTER_SLIDER_WIDTH = 32;
	private static final int FOOTER_COLOR_SIZE = 8;
	private static final int FOOTER_ITEM_GAP = 5;
	private static final int CATEGORY_TAB_HEIGHT = 16;
	private static final int MODULE_HEIGHT = 15;
	private static final int SETTING_HEIGHT = 14;
	private static final int KEYBIND_HEIGHT = 13;
	private static final int RESET_HEIGHT = 13;
	private static final int DESC_LINE_HEIGHT = 9;
	private static final int RESIZE_HANDLE = 4;

	private final GuiSettings guiSettings;
	private final MultiPanelClickGui multiPanel;
	private final GuiMotion motion = new GuiMotion();

	private int windowX;
	private int windowY;
	private int scrollOffset;
	private boolean listCollapsed;

	private Module bindingModule;
	private KeybindSetting bindingSetting;
	private NumberSetting draggingSetting;
	private boolean draggingWindow;
	private boolean draggingWidth;
	private boolean draggingHeight;
	private int footerSliderX;
	/** Fixed slider origin for the active width drag (right-aligned slider moves as width changes). */
	private int widthDragAnchorX;
	private double dragOffsetX;
	private double dragOffsetY;
	private String searchQuery = "";
	private boolean searchFocused;
	private boolean suppressNextSearchChar;

	public ClickGuiScreen(GuiSettings guiSettings) {
		super(Component.literal("Virulent Client"));
		this.guiSettings = guiSettings;
		this.multiPanel = new MultiPanelClickGui(guiSettings, guiSettings::scheduleSave, this, motion);
	}

	GuiMotion motion() {
		return motion;
	}

	public boolean isBinding() {
		return bindingModule != null || bindingSetting != null || multiPanel.isBinding();
	}

	private boolean isMultiPanel() {
		GuiLayoutStyle style = guiSettings.getLayoutStyle();
		return style == GuiLayoutStyle.METEOR || style == GuiLayoutStyle.WURST;
	}

	public void toggle() {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof ClickGuiScreen) {
			if (motion.isClosing()) {
				finishClose();
				return;
			}
			motion.beginClose();
		} else if (client.screen == null) {
			windowX = guiSettings.getWindowX();
			windowY = guiSettings.getWindowY();
			scrollOffset = guiSettings.getScrollOffset();
			motion.beginOpen();
			client.setScreen(this);
		}
	}

	private void finishClose() {
		Minecraft client = Minecraft.getInstance();
		guiSettings.setWindowPosition(windowX, windowY);
		guiSettings.setScrollOffset(scrollOffset);
		bindingModule = null;
		bindingSetting = null;
		draggingSetting = null;
		draggingWindow = false;
		searchFocused = false;
		suppressNextSearchChar = false;
		draggingWidth = false;
		widthDragAnchorX = 0;
		motion.clearExpand();
		multiPanel.onClose();
		VirulentClient.getInstance().getConfigManager().save();
		guiSettings.save();
		if (client.screen instanceof ClickGuiScreen) {
			client.setScreen(null);
		}
	}

	private int windowWidth() {
		return guiSettings.getWindowWidth();
	}

	private int windowHeight() {
		int saved = guiSettings.getWindowHeight();
		if (saved > 0) {
			return saved;
		}
		return Math.max(220, (int) (height * 0.68));
	}

	private int contentX() {
		return windowX + SIDEBAR_WIDTH;
	}

	private int contentWidth() {
		return windowWidth() - SIDEBAR_WIDTH;
	}

	private int listTop() {
		return windowY + HEADER_HEIGHT + SEARCH_HEIGHT + 3;
	}

	private int listHeight() {
		if (listCollapsed) {
			return 0;
		}
		return windowHeight() - HEADER_HEIGHT - SEARCH_HEIGHT - FOOTER_HEIGHT - 3;
	}

	private int windowBottom() {
		return windowY + windowHeight();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		motion.beginFrame();
		int slide = motion.openSlidePx(14);
		int hoverY = mouseY - slide;

		if (isMultiPanel()) {
			multiPanel.render(context, mouseX, hoverY, width, height, slide);
			motion.endFrame();
			if (motion.isFullyClosed()) {
				finishClose();
			}
			return;
		}

		int accent = guiSettings.getAccentColor();
		int ww = windowWidth();
		int wh = windowHeight();

		context.fill(0, 0, width, height, GuiPaint.withAlpha(0x000000, motion.openAlpha(0x66)));

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate(0.0f, slide);

		GuiPaint.fill(context, windowX, windowY, windowX + ww, windowBottom(),
			GuiPaint.withAlpha(GuiPaint.WINDOW_BG, motion.openAlpha(0xF0)));
		GuiPaint.border(context, windowX, windowY, ww, wh, GuiPaint.withAlpha(GuiPaint.BORDER, motion.openAlpha(0xFF)));
		GuiPaint.topAccent(context, windowX, windowY, ww, accent);
		GuiPaint.fill(context, contentX(), windowY + HEADER_HEIGHT, windowX + ww - 1, windowBottom() - 1,
			GuiPaint.withAlpha(GuiPaint.WINDOW_INNER, motion.openAlpha(0xFF)));

		renderHeader(context, mouseX, hoverY, accent, ww);
		renderSidebar(context, mouseX, hoverY, accent);

		if (!listCollapsed) {
			renderSearchBar(context, mouseX, hoverY, accent);
			int contentHeight = computeContentHeight();
			int visibleHeight = listHeight();
			scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, contentHeight - visibleHeight)));

			context.enableScissor(contentX(), listTop() + slide, windowX + ww, listTop() + visibleHeight + slide);
			renderModuleList(context, mouseX, hoverY, accent);
			context.disableScissor();
			renderScrollbar(context, contentHeight, visibleHeight, accent);
			renderFooter(context, mouseX, hoverY, accent);

			int handleY = windowBottom() - RESIZE_HANDLE;
			boolean resizeHovered = isHovered(mouseX, hoverY, windowX, handleY, ww, RESIZE_HANDLE);
			GuiPaint.fill(context, windowX + 1, handleY, windowX + ww - 1, windowBottom() - 1,
				resizeHovered ? GuiPaint.blend(accent, GuiPaint.BORDER, 0.55f) : GuiPaint.BORDER_SOFT);
		}

		if (bindingModule != null || bindingSetting != null) {
			String target = bindingModule != null ? bindingModule.getName() : bindingSetting.getName();
			String text = "Press key to bind " + target + "  |  DEL clear  |  ESC cancel";
			int textWidth = Minecraft.getInstance().font.width(text);
			int boxX = width / 2 - textWidth / 2 - 10;
			GuiPaint.inset(context, boxX, height - 28 - slide, textWidth + 20, 18, 0xF014141C, accent);
			context.centeredText(Minecraft.getInstance().font, text, width / 2, height - 23 - slide, accent);
		}

		pose.popMatrix();
		motion.endFrame();

		if (motion.isFullyClosed()) {
			finishClose();
		}
	}

	private void renderHeader(GuiGraphicsExtractor context, int mouseX, int mouseY, int accent, int ww) {
		GuiPaint.fill(context, windowX + 1, windowY + 2, windowX + ww - 1, windowY + HEADER_HEIGHT,
			guiSettings.getHeaderColor() | 0xFF000000);
		if (isHovered(mouseX, mouseY, windowX, windowY, ww, HEADER_HEIGHT) && !draggingWindow) {
			GuiPaint.fill(context, windowX + 1, windowY + 2, windowX + ww - 1, windowY + HEADER_HEIGHT, 0x14FFFFFF);
		}
		GuiPaint.fill(context, windowX + 1, windowY + HEADER_HEIGHT - 1, windowX + ww - 1, windowY + HEADER_HEIGHT,
			GuiPaint.withAlpha(accent, 0x66));

		context.text(Minecraft.getInstance().font, VirulentClient.NAME, windowX + 8, windowY + 5, accent);
		String version = "v" + VirulentClient.VERSION;
		int versionWidth = Minecraft.getInstance().font.width(version);
		context.text(Minecraft.getInstance().font, version, windowX + ww - versionWidth - 24, windowY + 5, GuiPaint.TEXT_MUTED);

		int collapseX = windowX + ww - 16;
		boolean collapseHovered = isHovered(mouseX, mouseY, collapseX, windowY + 4, 11, 11);
		GuiPaint.inset(context, collapseX, windowY + 4, 11, 11,
			collapseHovered ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG, GuiPaint.BORDER);
		context.centeredText(Minecraft.getInstance().font, listCollapsed ? "+" : "-", collapseX + 5, windowY + 6, accent);
	}

	private void renderSidebar(GuiGraphicsExtractor context, int mouseX, int mouseY, int accent) {
		int sidebarTop = windowY + HEADER_HEIGHT;
		int sidebarBottom = listCollapsed ? windowY + HEADER_HEIGHT : windowBottom() - FOOTER_HEIGHT - RESIZE_HANDLE;
		GuiPaint.fill(context, windowX + 1, sidebarTop, windowX + SIDEBAR_WIDTH, sidebarBottom, GuiPaint.SIDEBAR_BG);
		GuiPaint.fill(context, windowX + SIDEBAR_WIDTH - 1, sidebarTop, windowX + SIDEBAR_WIDTH, sidebarBottom, GuiPaint.BORDER);

		int tabY = sidebarTop + 5;
		for (Category category : Category.values()) {
			boolean selected = guiSettings.getSelectedCategory() == category;
			boolean rawHovered = isHovered(mouseX, mouseY, windowX + 4, tabY, SIDEBAR_WIDTH - 8, CATEGORY_TAB_HEIGHT);
			float hover = motion.hover(category, rawHovered && !selected);
			int idle = 0xFF101018;
			int bg = selected
				? GuiPaint.blend(accent, GuiPaint.PANEL_BG, 0.78f)
				: GuiPaint.blend(idle, GuiPaint.PANEL_HOVER, hover);
			GuiPaint.fill(context, windowX + 4, tabY, windowX + SIDEBAR_WIDTH - 4, tabY + CATEGORY_TAB_HEIGHT, bg);
			if (selected) {
				GuiPaint.accentStrip(context, windowX + 4, tabY, CATEGORY_TAB_HEIGHT, accent);
			}

			String label = shortCategoryName(category);
			context.text(Minecraft.getInstance().font, label, windowX + 10, tabY + 4,
				selected ? accent : GuiPaint.TEXT_DIM);

			int enabled = enabledCount(category);
			if (enabled > 0) {
				String count = String.valueOf(enabled);
				int countWidth = Minecraft.getInstance().font.width(count);
				context.text(Minecraft.getInstance().font, count,
					windowX + SIDEBAR_WIDTH - countWidth - 8, tabY + 4, accent);
			}

			tabY += CATEGORY_TAB_HEIGHT + 3;
		}
	}

	private static String shortCategoryName(Category category) {
		return switch (category) {
			case COMBAT -> "Combat";
			case MOVEMENT -> "Move";
			case RENDER -> "Render";
			case PLAYER -> "Player";
			case MISC -> "Misc";
			case PERFORMANCE -> "Perf";
		};
	}

	private int enabledCount(Category category) {
		int count = 0;
		for (Module module : VirulentClient.getInstance().getModuleManager().getModulesByCategory(category)) {
			if (module.isEnabled()) {
				count++;
			}
		}
		return count;
	}

	private void renderSearchBar(GuiGraphicsExtractor context, int mouseX, int mouseY, int accent) {
		int searchY = windowY + HEADER_HEIGHT + 2;
		int sw = contentWidth() - 8;
		int sx = contentX() + 4;
		boolean hovered = isHovered(mouseX, mouseY, sx, searchY, sw, SEARCH_HEIGHT);
		GuiPaint.inset(context, sx, searchY, sw, SEARCH_HEIGHT,
			searchFocused || hovered ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG,
			searchFocused ? accent : GuiPaint.BORDER);
		String text = searchQuery.isEmpty() && !searchFocused ? "Search modules..." : searchQuery + (searchFocused ? "_" : "");
		int color = searchQuery.isEmpty() && !searchFocused ? GuiPaint.TEXT_MUTED : GuiPaint.TEXT;
		context.text(Minecraft.getInstance().font, text, sx + 6, searchY + 4, color);
	}

	private void renderModuleList(GuiGraphicsExtractor context, int mouseX, int mouseY, int accent) {
		int cw = contentWidth();
		int y = listTop() - scrollOffset;
		List<Module> modules = visibleModules();

		if (modules.isEmpty()) {
			context.text(Minecraft.getInstance().font, "No modules found", contentX() + 10, y + 6, GuiPaint.TEXT_MUTED);
			return;
		}

		for (Module module : modules) {
			boolean rawHovered = isHovered(mouseX, mouseY, contentX() + 4, y, cw - 8, MODULE_HEIGHT);
			boolean enabled = module.isEnabled();
			float hover = motion.hover(module, rawHovered && !enabled);
			int background = enabled
				? GuiPaint.blend(accent, GuiPaint.WINDOW_INNER, 0.82f)
				: GuiPaint.blend(GuiPaint.PANEL_BG, GuiPaint.PANEL_HOVER, hover);
			GuiPaint.fill(context, contentX() + 4, y, contentX() + cw - 4, y + MODULE_HEIGHT, background);
			if (enabled) {
				GuiPaint.accentStrip(context, contentX() + 4, y, MODULE_HEIGHT, accent);
			}

			int nameX = contentX() + 10;
			if (isGlobalSearch()) {
				String tag = shortCategoryName(module.getCategory()) + " ";
				context.text(Minecraft.getInstance().font, tag, nameX, y + 4, GuiPaint.TEXT_MUTED);
				nameX += Minecraft.getInstance().font.width(tag);
			}

			context.text(Minecraft.getInstance().font, module.getName(), nameX, y + 4,
				enabled ? accent : GuiPaint.TEXT);

			int chipRight = contentX() + cw - 8;
			GuiPaint.chipRight(context, enabled ? "ON" : "OFF", chipRight, y + 3, accent, enabled);

			if (guiSettings.showKeybinds() && module.getKeyBind() != GLFW.GLFW_KEY_UNKNOWN) {
				String bind = KeybindUtil.getName(module.getKeyBind());
				int bindWidth = Minecraft.getInstance().font.width(bind);
				int stateWidth = Minecraft.getInstance().font.width(enabled ? "ON" : "OFF") + 6;
				context.text(Minecraft.getInstance().font, bind, chipRight - stateWidth - bindWidth - 6, y + 4, GuiPaint.TEXT_DIM);
			}

			y += MODULE_HEIGHT + 1;
		}
	}

	private void renderScrollbar(GuiGraphicsExtractor context, int contentHeight, int visibleHeight, int accent) {
		if (contentHeight <= visibleHeight) {
			return;
		}

		int trackX = windowX + windowWidth() - 5;
		int trackY = listTop();
		int trackH = visibleHeight;
		GuiPaint.fill(context, trackX, trackY, trackX + 3, trackY + trackH, GuiPaint.TRACK);

		double ratio = (double) visibleHeight / contentHeight;
		int thumbH = Math.max(14, (int) (trackH * ratio));
		int thumbY = trackY + (int) ((trackH - thumbH) * ((double) scrollOffset / (contentHeight - visibleHeight)));
		GuiPaint.fill(context, trackX, thumbY, trackX + 3, thumbY + thumbH, accent | 0xFF000000);
	}

	private record FooterLayout(
		int colorX,
		int colorY,
		int sliderX,
		int sliderY,
		int themeX,
		int themeWidth,
		int sortX,
		int sortWidth,
		int descX,
		int descWidth,
		int keysX,
		int keysWidth,
		int profilesX,
		int profilesWidth,
		int hudX,
		int hudWidth,
		int controlsLeft
	) {}

	private FooterLayout footerLayout(int footerY) {
		var font = Minecraft.getInstance().font;
		int minLeft = windowX + 5;
		int right = windowX + windowWidth() - 5;
		int colorY = footerY + 4;
		int gap = FOOTER_ITEM_GAP;

		String keysLabel = guiSettings.showKeybinds() ? "Keys" : "keys";
		int keysWidth = font.width(keysLabel);
		String descLabel = guiSettings.showDescriptions() ? "Desc" : "desc";
		int descWidth = font.width(descLabel);
		String sortLabel = guiSettings.getHudSort().getLabel();
		int sortWidth = font.width(sortLabel);
		String themeLabel = guiSettings.getLayoutStyle().getLabel();
		int themeWidth = font.width(themeLabel);
		String hudLabel = guiSettings.isHudVisible() ? "Hud" : "hud";
		int hudWidth = font.width(hudLabel);
		String profilesLabel = "Cfg";
		int profilesWidth = font.width(profilesLabel);

		int total = hudWidth + gap + FOOTER_COLOR_SIZE + gap + FOOTER_SLIDER_WIDTH + gap
			+ themeWidth + gap + sortWidth + gap + descWidth + gap + keysWidth + gap + profilesWidth;
		boolean showSort = true;
		if (right - minLeft < total) {
			showSort = false;
			total -= sortWidth + gap;
		}

		// Right-align when there is room; otherwise pin to the left padding so Hud never leaves the window.
		int x = Math.max(minLeft, right - total);

		int hudX = x;
		x += hudWidth + gap;
		int colorX = x;
		x += FOOTER_COLOR_SIZE + gap;
		int sliderX = x;
		x += FOOTER_SLIDER_WIDTH + gap;
		int themeX = x;
		x += themeWidth + gap;
		int sortX = x;
		if (showSort) {
			x += sortWidth + gap;
		} else {
			sortWidth = 0;
		}
		int descX = x;
		x += descWidth + gap;
		int keysX = x;
		x += keysWidth + gap;
		int profilesX = x;

		return new FooterLayout(
			colorX,
			colorY,
			sliderX,
			footerY + 6,
			themeX,
			themeWidth,
			sortX,
			sortWidth,
			descX,
			descWidth,
			keysX,
			keysWidth,
			profilesX,
			profilesWidth,
			hudX,
			hudWidth,
			hudX
		);
	}

	private void renderFooter(GuiGraphicsExtractor context, int mouseX, int mouseY, int accent) {
		int footerY = listTop() + listHeight();
		int ww = windowWidth();
		GuiPaint.fill(context, windowX + 1, footerY, windowX + ww - 1, footerY + FOOTER_HEIGHT, GuiPaint.SIDEBAR_BG);
		GuiPaint.fill(context, windowX + 1, footerY, windowX + ww - 1, footerY + 1, GuiPaint.withAlpha(accent, 0x44));

		FooterLayout layout = footerLayout(footerY);
		footerSliderX = layout.sliderX();
		var font = Minecraft.getInstance().font;

		String hint = "L:on  R:cfg  M:bind";
		int hintX = contentX() + 6;
		if (hintX + font.width(hint) < layout.controlsLeft() - 4) {
			context.text(font, hint, hintX, footerY + 5, GuiPaint.TEXT_MUTED);
		}

		GuiPaint.inset(context, layout.colorX(), layout.colorY(), FOOTER_COLOR_SIZE, FOOTER_COLOR_SIZE,
			accent | 0xFF000000, GuiPaint.BORDER);

		int sliderDrawX = draggingWidth ? widthDragAnchorX : layout.sliderX();
		double widthPercent = (windowWidth() - 200) / 200.0;
		GuiPaint.slider(context, sliderDrawX, layout.sliderY() - 1, FOOTER_SLIDER_WIDTH, widthPercent, accent);

		context.text(font, guiSettings.getLayoutStyle().getLabel(), layout.themeX(), footerY + 5, accent);
		if (layout.sortWidth() > 0) {
			context.text(font, guiSettings.getHudSort().getLabel(), layout.sortX(), footerY + 5, accent);
		}
		context.text(
			font,
			guiSettings.isHudVisible() ? "Hud" : "hud",
			layout.hudX(),
			footerY + 5,
			guiSettings.isHudVisible() ? accent : GuiPaint.TEXT_MUTED
		);
		context.text(
			font,
			guiSettings.showDescriptions() ? "Desc" : "desc",
			layout.descX(),
			footerY + 5,
			guiSettings.showDescriptions() ? accent : GuiPaint.TEXT_MUTED
		);
		context.text(
			font,
			guiSettings.showKeybinds() ? "Keys" : "keys",
			layout.keysX(),
			footerY + 5,
			guiSettings.showKeybinds() ? accent : GuiPaint.TEXT_MUTED
		);
		context.text(font, "Cfg", layout.profilesX(), footerY + 5, accent);
	}

	private void renderSetting(GuiGraphicsExtractor context, Setting<?> setting, int sx, int sy, int sw, int mouseX, int mouseY, int accent) {
		boolean hovered = isHovered(mouseX, mouseY, sx, sy, sw, SETTING_HEIGHT);
		GuiPaint.fill(context, sx, sy, sx + sw, sy + SETTING_HEIGHT, hovered ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG);

		if (setting instanceof BooleanSetting booleanSetting) {
			context.text(Minecraft.getInstance().font, setting.getName(), sx + 5, sy + 3, GuiPaint.TEXT);
			GuiPaint.toggle(context, sx + sw - 22, sy + 3, booleanSetting.getValue(), accent);
			return;
		}

		if (setting instanceof NumberSetting numberSetting) {
			context.text(Minecraft.getInstance().font, setting.getName(), sx + 5, sy + 3, GuiPaint.TEXT);
			int sliderX = sx + sw / 2;
			int sliderW = sw / 2 - 8;
			double range = numberSetting.getMax() - numberSetting.getMin();
			double percent = range <= 0 ? 0 : (numberSetting.getValue() - numberSetting.getMin()) / range;
			GuiPaint.slider(context, sliderX, sy + 5, sliderW, percent, accent);
			String valueText = String.format("%.1f", numberSetting.getValue());
			context.text(Minecraft.getInstance().font, valueText,
				sliderX + sliderW - Minecraft.getInstance().font.width(valueText), sy + 1, accent);
			return;
		}

		if (setting instanceof ModeSetting modeSetting) {
			context.text(Minecraft.getInstance().font, setting.getName(), sx + 5, sy + 3, GuiPaint.TEXT);
			String value = modeSetting.getValue();
			GuiPaint.chipRight(context, value, sx + sw - 4, sy + 2, accent, true);
			return;
		}

		if (setting instanceof KeybindSetting keybindSetting) {
			String value = bindingSetting == keybindSetting ? "..." : KeybindUtil.getName(keybindSetting.getValue());
			context.text(Minecraft.getInstance().font, setting.getName(), sx + 5, sy + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, value, sx + sw - 4, sy + 2, accent, true);
			return;
		}

		if (setting instanceof BlockListSetting blockListSetting) {
			context.text(Minecraft.getInstance().font, setting.getName(), sx + 5, sy + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, "Select (" + blockListSetting.size() + ")", sx + sw - 4, sy + 2, accent, true);
			return;
		}

		if (setting instanceof ActionSetting actionSetting) {
			context.text(Minecraft.getInstance().font, setting.getName(), sx + 5, sy + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, actionSetting.getLabel(), sx + sw - 4, sy + 2, accent, true);
			return;
		}

		if (setting instanceof BlockEspConfigSetting || setting instanceof BlockEspConfigsSetting) {
			context.text(Minecraft.getInstance().font, setting.getName(), sx + 5, sy + 3, GuiPaint.TEXT);
			GuiPaint.chipRight(context, "Edit", sx + sw - 4, sy + 2, accent, true);
		}
	}

	private int computeContentHeight() {
		int h = 0;
		for (Module ignored : visibleModules()) {
			h += MODULE_HEIGHT + 1;
		}
		return Math.max(h, 20);
	}

	private List<Module> visibleModules() {
		var manager = VirulentClient.getInstance().getModuleManager();
		if (isGlobalSearch()) {
			return manager.getModules().stream()
				.filter(this::matchesSearch)
				.toList();
		}
		return manager.getModulesByCategory(guiSettings.getSelectedCategory()).stream()
			.filter(this::matchesSearch)
			.toList();
	}

	private boolean isGlobalSearch() {
		return !searchQuery.isEmpty();
	}

	private Module expandTargetModule() {
		Object target = motion.expandTarget();
		return target instanceof Module module ? module : null;
	}

	private int animSlide() {
		return motion.openSlidePx(14);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y() - animSlide();
		int button = event.button();

		if (motion.isClosing()) {
			return true;
		}

		if (isMultiPanel()) {
			return multiPanel.mouseClicked(mouseX, mouseY, button, width, height) || super.mouseClicked(event, doubleClick);
		}

		if (button != 0) {
			return handleContentClick(mouseX, mouseY, button) || super.mouseClicked(event, doubleClick);
		}

		int ww = windowWidth();

		if (isHovered(mouseX, mouseY, windowX + ww - 16, windowY + 4, 11, 11)) {
			listCollapsed = !listCollapsed;
			return true;
		}

		if (isHovered(mouseX, mouseY, windowX, windowY, ww, HEADER_HEIGHT)) {
			draggingWindow = true;
			dragOffsetX = mouseX - windowX;
			dragOffsetY = mouseY - windowY;
			return true;
		}

		if (handleSidebarClick(mouseX, mouseY)) {
			return true;
		}

		int searchY = windowY + HEADER_HEIGHT + 2;
		if (isHovered(mouseX, mouseY, contentX() + 4, searchY, contentWidth() - 8, SEARCH_HEIGHT)) {
			searchFocused = true;
			bindingModule = null;
			bindingSetting = null;
			return true;
		}

		searchFocused = false;

		if (handleFooterClick(mouseX, mouseY)) {
			return true;
		}

		if (!listCollapsed && isHovered(mouseX, mouseY, windowX, windowBottom() - RESIZE_HANDLE, ww, RESIZE_HANDLE)) {
			draggingHeight = true;
			return true;
		}

		if (handleContentClick(mouseX, mouseY, button)) {
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	private boolean handleSidebarClick(double mouseX, double mouseY) {
		int tabY = windowY + HEADER_HEIGHT + 5;
		for (Category category : Category.values()) {
			if (isHovered(mouseX, mouseY, windowX + 4, tabY, SIDEBAR_WIDTH - 8, CATEGORY_TAB_HEIGHT)) {
				if (guiSettings.getSelectedCategory() != category) {
					guiSettings.setSelectedCategory(category);
					scrollOffset = 0;
					motion.clearExpand();
					searchQuery = "";
				}
				return true;
			}
			tabY += CATEGORY_TAB_HEIGHT + 3;
		}
		return false;
	}

	private boolean handleFooterClick(double mouseX, double mouseY) {
		if (listCollapsed) {
			return false;
		}

		int footerY = listTop() + listHeight();
		int ww = windowWidth();
		if (!isHovered(mouseX, mouseY, windowX, footerY, ww, FOOTER_HEIGHT)) {
			return false;
		}

		FooterLayout layout = footerLayout(footerY);

		if (isHovered(mouseX, mouseY, layout.colorX(), layout.colorY(), FOOTER_COLOR_SIZE, FOOTER_COLOR_SIZE)) {
			guiSettings.cycleAccentPreset();
			return true;
		}

		if (isHovered(mouseX, mouseY, layout.sliderX(), footerY + 2, FOOTER_SLIDER_WIDTH, FOOTER_HEIGHT - 4)) {
			draggingWidth = true;
			widthDragAnchorX = layout.sliderX();
			updateWidth(mouseX, widthDragAnchorX);
			return true;
		}

		if (isHovered(mouseX, mouseY, layout.themeX(), footerY, layout.themeWidth(), FOOTER_HEIGHT)) {
			guiSettings.cycleLayoutStyle();
			return true;
		}

		if (layout.sortWidth() > 0
			&& isHovered(mouseX, mouseY, layout.sortX(), footerY, layout.sortWidth(), FOOTER_HEIGHT)) {
			guiSettings.cycleHudSort();
			return true;
		}

		if (isHovered(mouseX, mouseY, layout.hudX(), footerY, layout.hudWidth(), FOOTER_HEIGHT)) {
			guiSettings.toggleHudVisible();
			return true;
		}

		if (isHovered(mouseX, mouseY, layout.descX(), footerY, layout.descWidth(), FOOTER_HEIGHT)) {
			guiSettings.toggleDescriptions();
			return true;
		}

		if (isHovered(mouseX, mouseY, layout.keysX(), footerY, layout.keysWidth(), FOOTER_HEIGHT)) {
			guiSettings.toggleKeybinds();
			return true;
		}

		if (isHovered(mouseX, mouseY, layout.profilesX(), footerY, layout.profilesWidth(), FOOTER_HEIGHT)) {
			Minecraft.getInstance().setScreen(new ProfilesScreen(this));
			return true;
		}

		return false;
	}

	private boolean handleContentClick(double mouseX, double mouseY, int button) {
		if (listCollapsed || !isHovered(mouseX, mouseY, contentX(), listTop(), contentWidth(), listHeight())) {
			return false;
		}

		int cw = contentWidth();
		int y = listTop() - scrollOffset;

		for (Module module : visibleModules()) {
			if (isHovered(mouseX, mouseY, contentX() + 4, y, cw - 8, MODULE_HEIGHT)) {
				if (button == 0) {
					module.toggle();
				} else if (button == 1) {
					if (isGlobalSearch()) {
						guiSettings.setSelectedCategory(module.getCategory());
					}
					Minecraft.getInstance().setScreen(new ModuleSettingsScreen(this, module));
				} else if (button == 2 && guiSettings.showKeybinds()) {
					bindingModule = module;
				}
				return true;
			}
			y += MODULE_HEIGHT + 1;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (isMultiPanel()) {
			multiPanel.mouseReleased();
		}
		if (draggingWindow) {
			guiSettings.setWindowPosition(windowX, windowY);
		}
		if (draggingHeight) {
			guiSettings.setWindowHeight(windowHeight());
		}
		draggingWindow = false;
		draggingSetting = null;
		draggingWidth = false;
		draggingHeight = false;
		widthDragAnchorX = 0;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		double mouseX = event.x();
		double mouseY = event.y() - animSlide();
		if (isMultiPanel() && multiPanel.mouseDragged(mouseX, mouseY, width, height)) {
			return true;
		}
		if (draggingWindow) {
			windowX = clamp((int) (mouseX - dragOffsetX), 0, width - windowWidth());
			windowY = clamp((int) (mouseY - dragOffsetY), 0, height - HEADER_HEIGHT);
			return true;
		}
		if (draggingWidth) {
			updateWidth(mouseX, widthDragAnchorX);
			return true;
		}
		if (draggingHeight) {
			int newHeight = clamp((int) (mouseY - windowY), 160, height - windowY);
			guiSettings.setWindowHeight(newHeight);
			return true;
		}
		if (draggingSetting != null) {
			updateDraggedNumber(mouseX);
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	private void updateWidth(double mouseX, int sliderX) {
		double percent = Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / (double) FOOTER_SLIDER_WIDTH));
		int newWidth = (int) Math.round(200.0 + percent * 200.0);
		guiSettings.setWindowWidth(newWidth);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!listCollapsed && isHovered(mouseX, mouseY - animSlide(), contentX(), listTop(), contentWidth(), listHeight())) {
			scrollOffset = (int) Math.max(0, scrollOffset - verticalAmount * 14);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key();
		if (multiPanel.isBinding()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				multiPanel.clearBinding();
			} else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				multiPanel.clearModuleBind();
			} else if (keyCode != ClientKeybinds.clickGuiKey()) {
				multiPanel.setModuleBind(keyCode);
			}
			return true;
		}

		if (bindingModule != null || bindingSetting != null) {
			searchFocused = false;
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				bindingModule = null;
				bindingSetting = null;
			} else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				if (bindingModule != null) {
					bindingModule.setKeyBind(GLFW.GLFW_KEY_UNKNOWN);
				} else if (bindingSetting != null) {
					bindingSetting.setValue(GLFW.GLFW_KEY_UNKNOWN);
				}
				bindingModule = null;
				bindingSetting = null;
			} else if (keyCode != ClientKeybinds.clickGuiKey()) {
				if (bindingModule != null) {
					bindingModule.setKeyBind(keyCode);
				} else if (bindingSetting != null) {
					bindingSetting.setValue(keyCode);
				}
				bindingModule = null;
				bindingSetting = null;
				// keyPressed clears bind state before charTyped; don't let that char hit search.
				suppressNextSearchChar = true;
			}
			return true;
		}

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (searchFocused) {
				searchFocused = false;
				return true;
			}
			toggle();
			return true;
		}

		if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
			searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (suppressNextSearchChar) {
			suppressNextSearchChar = false;
			return true;
		}
		if (bindingModule != null || bindingSetting != null || multiPanel.isBinding()) {
			return true;
		}
		if (searchFocused && event.isAllowedChatCharacter()) {
			searchQuery += event.codepointAsString();
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private boolean matchesSearch(Module module) {
		if (searchQuery.isEmpty()) {
			return true;
		}
		String query = searchQuery.toLowerCase(Locale.ROOT);
		return module.getName().toLowerCase(Locale.ROOT).contains(query)
			|| module.getDescription().toLowerCase(Locale.ROOT).contains(query);
	}

	private boolean handleSettingClick(Setting<?> setting, double mouseX, double mouseY, int button, int sx, int sy, int sw) {
		if (!isHovered(mouseX, mouseY, sx, sy, sw, SETTING_HEIGHT)) {
			return false;
		}

		if (setting instanceof BooleanSetting booleanSetting && button == 0) {
			booleanSetting.toggle();
			return true;
		}

		if (setting instanceof NumberSetting numberSetting) {
			if (button == 0) {
				int sliderX = sx + sw / 2;
				int sliderW = sw / 2 - 8;
				draggingSetting = numberSetting;
				updateNumberFromMouse(numberSetting, mouseX, sliderX, sliderW);
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
				cycleModeBackward(modeSetting);
			}
			return true;
		}

		if (setting instanceof KeybindSetting keybindSetting && button == 0) {
			bindingSetting = keybindSetting;
			return true;
		}

		if (setting instanceof ActionSetting && button == 0) {
			Module expanded = expandTargetModule();
			if (expanded != null && "SeedCracker".equals(expanded.getName())) {
				Minecraft.getInstance().setScreen(new SeedCrackerScreen(this));
			} else if (expanded != null && "Friends".equals(expanded.getName())) {
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

		if (setting instanceof BlockEspConfigsSetting && button == 0 && expandTargetModule() instanceof BlockEsp blockEsp) {
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

	private void updateDraggedNumber(double mouseX) {
		if (draggingSetting == null) {
			return;
		}
		int panelX = contentX() + 6;
		int panelW = contentWidth() - 12;
		int sliderX = panelX + panelW / 2;
		int sliderW = panelW / 2 - 8;
		updateNumberFromMouse(draggingSetting, mouseX, sliderX, sliderW);
	}

	private static void updateNumberFromMouse(NumberSetting setting, double mouseX, int sliderX, int sliderW) {
		double percent = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
		double value = setting.getMin() + percent * (setting.getMax() - setting.getMin());
		double steps = Math.round((value - setting.getMin()) / setting.getIncrement());
		setting.setValue(setting.getMin() + steps * setting.getIncrement());
	}

	private static void cycleModeBackward(ModeSetting modeSetting) {
		int index = modeSetting.getModes().indexOf(modeSetting.getValue());
		int previous = (index - 1 + modeSetting.getModes().size()) % modeSetting.getModes().size();
		modeSetting.setValue(modeSetting.getModes().get(previous));
	}

	private void drawWrappedText(GuiGraphicsExtractor context, String text, int x, int y, int maxWidth, int color) {
		String line = text;
		if (Minecraft.getInstance().font.width(line) > maxWidth) {
			while (line.length() > 3 && Minecraft.getInstance().font.width(line + "...") > maxWidth) {
				line = line.substring(0, line.length() - 1);
			}
			line += "...";
		}
		context.text(Minecraft.getInstance().font, line, x, y, color);
	}

	private static boolean isHovered(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

}
