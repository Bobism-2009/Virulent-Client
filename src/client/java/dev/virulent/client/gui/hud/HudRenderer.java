package dev.virulent.client.gui.hud;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.config.GuiSettings;
import dev.virulent.client.config.HudSort;
import dev.virulent.client.event.EventBus;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.module.Module;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public final class HudRenderer {
	private static final int ROW_HEIGHT = 11;
	private static final int ROW_PADDING = 2;

	private final GuiSettings guiSettings;
	private boolean draggingHud;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean wasMiddleMouseDown;

	public HudRenderer(EventBus eventBus, GuiSettings guiSettings) {
		this.guiSettings = guiSettings;
		eventBus.subscribe(Render2DEvent.class, this::onRender2D);
	}

	public void tick() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.screen != null || !guiSettings.isHudVisible()) {
			draggingHud = false;
			wasMiddleMouseDown = false;
			return;
		}

		Window window = client.getWindow();
		boolean middleMouseDown = GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
		// mouseHandler reports raw window pixels; the HUD lives in GUI-scaled space.
		double mouseX = client.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
		double mouseY = client.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();

		if (middleMouseDown) {
			List<Module> enabled = enabledModules(client);
			if (!wasMiddleMouseDown && isMouseOverHud(mouseX, mouseY, client, enabled)) {
				draggingHud = true;
				dragOffsetX = (int) mouseX - guiSettings.getHudX();
				dragOffsetY = (int) mouseY - guiSettings.getHudY();
			}

			if (draggingHud) {
				int screenWidth = window.getGuiScaledWidth();
				int screenHeight = window.getGuiScaledHeight();
				int maxWidth = maxHudWidth(client, enabled);
				int maxHeight = enabled.size() * ROW_HEIGHT;
				int x = Math.max(0, Math.min((int) mouseX - dragOffsetX, screenWidth - maxWidth));
				int y = Math.max(0, Math.min((int) mouseY - dragOffsetY, screenHeight - Math.max(ROW_HEIGHT, maxHeight)));
				guiSettings.setHudPosition(x, y);
			}
		}

		if (!middleMouseDown) {
			draggingHud = false;
		}

		wasMiddleMouseDown = middleMouseDown;
	}

	private void onRender2D(Render2DEvent event) {
		if (!guiSettings.isHudVisible()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		List<Module> enabled = enabledModules(client);
		if (enabled.isEmpty()) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		boolean black = guiSettings.isHudBlack() || guiSettings.isAccentBlack();
		int textColor = black ? 0xFFFFFFFF : (guiSettings.getAccentColor() | 0xFF000000);
		int rowBg = black ? 0xEE000000 : 0xAA101018;
		int x = guiSettings.getHudX();
		int y = guiSettings.getHudY();

		for (Module module : enabled) {
			String text = module.getName();
			int width = client.font.width(text);
			context.fill(x, y - 1, x + 4 + width, y + 9, rowBg);
			if (black) {
				context.fill(x, y - 1, x + 1, y + 9, 0xFFFFFFFF);
			}
			context.text(client.font, text, x + 2, y, textColor);
			y += ROW_HEIGHT;
		}

		if (draggingHud) {
			int maxWidth = maxHudWidth(client, enabled);
			int height = enabled.size() * ROW_HEIGHT;
			context.fill(x, y - height, x + maxWidth, y, 0x33FFFFFF);
		}
	}

	private List<Module> enabledModules(Minecraft client) {
		return VirulentClient.getInstance().getModuleManager().getModules().stream()
			.filter(Module::isEnabled)
			.sorted(sortComparator(client))
			.toList();
	}

	private Comparator<Module> sortComparator(Minecraft client) {
		return switch (guiSettings.getHudSort()) {
			case ALPHABETICAL -> Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
			case CATEGORY -> Comparator.comparing((Module module) -> module.getCategory().ordinal())
				.thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
			case LENGTH -> Comparator.comparingInt((Module module) -> -client.font.width(module.getName()));
		};
	}

	private boolean isMouseOverHud(double mouseX, double mouseY, Minecraft client, List<Module> enabled) {
		if (enabled.isEmpty()) {
			return false;
		}

		int x = guiSettings.getHudX();
		int y = guiSettings.getHudY();
		int width = maxHudWidth(client, enabled);
		int height = enabled.size() * ROW_HEIGHT;
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	private int maxHudWidth(Minecraft client, List<Module> enabled) {
		int width = 0;
		for (Module module : enabled) {
			width = Math.max(width, client.font.width(module.getName()) + 4);
		}
		return width + ROW_PADDING;
	}
}
