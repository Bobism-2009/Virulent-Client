package dev.virulent.client.module.modules.performance;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.lwjgl.glfw.GLFW;

public final class FpsHud extends Module {
	private final ModeSetting position = addSetting(new ModeSetting(
		"Position",
		"Top Left",
		"Top Left",
		"Top Right",
		"Bottom Left",
		"Bottom Right",
		"Hotbar Left",
		"Hotbar Right"
	));
	private final BooleanSetting showPing = addSetting(new BooleanSetting("Show Ping", true));
	private final BooleanSetting showMemory = addSetting(new BooleanSetting("Show Memory", false));
	private final BooleanSetting background = addSetting(new BooleanSetting("Background", true));
	private final BooleanSetting colorCode = addSetting(new BooleanSetting("Color Code", true));
	private final NumberSetting offsetX = addSetting(new NumberSetting("Offset X", 0.0, -200.0, 200.0, 1.0));
	private final NumberSetting offsetY = addSetting(new NumberSetting("Offset Y", 0.0, -200.0, 200.0, 1.0));

	public FpsHud() {
		super("FPSHud", "Shows FPS, and optionally ping and memory.", Category.PERFORMANCE, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	private void onRender2D(Render2DEvent event) {
		if (mc().player == null || mc().options.hideGui) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		var font = mc().font;
		int screenW = mc().getWindow().getGuiScaledWidth();
		int screenH = mc().getWindow().getGuiScaledHeight();
		int accent = VirulentClient.getInstance().getGuiSettings().getAccentColor() | 0xFF000000;

		int fps = mc().getFps();
		String fpsText = fps + " FPS";
		String pingText = null;
		String memText = null;

		if (showPing.getValue()) {
			int ping = currentPing();
			if (ping >= 0) {
				pingText = ping + " ms";
			}
		}
		if (showMemory.getValue()) {
			Runtime runtime = Runtime.getRuntime();
			long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
			long maxMb = runtime.maxMemory() / (1024L * 1024L);
			memText = usedMb + "/" + maxMb + " MB";
		}

		String line = fpsText;
		if (pingText != null) {
			line += "  |  " + pingText;
		}
		if (memText != null) {
			line += "  |  " + memText;
		}

		int textW = font.width(line);
		int padX = 5;
		int padY = 3;
		int panelW = textW + padX * 2;
		int panelH = font.lineHeight + padY * 2;

		int[] origin = anchor(screenW, screenH, panelW, panelH);
		int x = origin[0] + offsetX.getValue().intValue();
		int y = origin[1] + offsetY.getValue().intValue();

		if (background.getValue()) {
			drawPanel(context, x, y, panelW, panelH, accent);
		}

		int textX = x + padX;
		int textY = y + padY;
		int fpsColor = colorCode.getValue() ? fpsColor(fps) : 0xFFE8E8F0;
		context.text(font, fpsText, textX, textY, fpsColor);
		textX += font.width(fpsText);

		if (pingText != null) {
			context.text(font, "  |  ", textX, textY, 0xFF5C5C6C);
			textX += font.width("  |  ");
			int pingColor = colorCode.getValue() ? pingColor(currentPing()) : 0xFFE8E8F0;
			context.text(font, pingText, textX, textY, pingColor);
			textX += font.width(pingText);
		}
		if (memText != null) {
			context.text(font, "  |  ", textX, textY, 0xFF5C5C6C);
			textX += font.width("  |  ");
			context.text(font, memText, textX, textY, 0xFFE8E8F0);
		}
	}

	private int currentPing() {
		if (mc().player == null || mc().getConnection() == null) {
			return -1;
		}
		PlayerInfo info = mc().getConnection().getPlayerInfo(mc().player.getUUID());
		if (info == null) {
			return -1;
		}
		return info.getLatency();
	}

	private static int fpsColor(int fps) {
		if (fps >= 60) {
			return 0xFF88FF88;
		}
		if (fps >= 30) {
			return 0xFFFFCC66;
		}
		return 0xFFFF8888;
	}

	private static int pingColor(int ping) {
		if (ping < 0) {
			return 0xFFE8E8F0;
		}
		if (ping <= 80) {
			return 0xFF88FF88;
		}
		if (ping <= 150) {
			return 0xFFFFCC66;
		}
		return 0xFFFF8888;
	}

	private int[] anchor(int screenW, int screenH, int panelW, int panelH) {
		return switch (position.getValue()) {
			case "Top Right" -> new int[] {screenW - panelW - 4, 4};
			case "Bottom Left" -> new int[] {4, screenH - panelH - 4};
			case "Bottom Right" -> new int[] {screenW - panelW - 4, screenH - panelH - 4};
			case "Hotbar Left" -> new int[] {screenW / 2 - 91 - panelW - 8, screenH - 22 - panelH};
			case "Hotbar Right" -> new int[] {screenW / 2 + 91 + 8, screenH - 22 - panelH};
			default -> new int[] {4, 4};
		};
	}

	private void drawPanel(GuiGraphicsExtractor context, int x, int y, int w, int h, int accent) {
		context.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
		context.fill(x, y, x + w, y + h, 0xD012121A);
		context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x22181828);
		context.fill(x, y, x + w, y + 1, 0xFF2A2A3A);
		context.fill(x, y + h - 1, x + w, y + h, 0xFF2A2A3A);
		context.fill(x, y, x + 1, y + h, 0xFF2A2A3A);
		context.fill(x + w - 1, y, x + w, y + h, 0xFF2A2A3A);
		context.fill(x, y, x + 2, y + h, accent);
	}
}
