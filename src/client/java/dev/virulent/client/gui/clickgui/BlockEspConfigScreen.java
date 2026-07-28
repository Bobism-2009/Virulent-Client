package dev.virulent.client.gui.clickgui;

import dev.virulent.client.setting.BlockEspConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class BlockEspConfigScreen extends Screen {
	private static final int PAD = 8;
	private static final int ROW_H = 16;

	private final Screen parent;
	private final String title;
	private final BlockEspConfig config;
	private final Consumer<BlockEspConfig> onSave;

	private NumberRow dragging;

	public BlockEspConfigScreen(Screen parent, String title, BlockEspConfig config, Consumer<BlockEspConfig> onSave) {
		super(Component.literal(title));
		this.parent = parent;
		this.title = title;
		this.config = config.copy();
		this.onSave = onSave;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xCC101018);

		int panelW = Math.min(280, width - 40);
		int panelH = 220;
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF012121A);
		var font = Minecraft.getInstance().font;
		context.text(font, title, panelX + PAD, panelY + 6, 0xFFE0E0E0);

		int y = panelY + 24;
		drawModeRow(context, panelX, y, panelW, "Shape Mode", config.getShapeMode().label());
		y += ROW_H + 2;

		y = drawColorGroup(context, panelX, y, panelW, "Line", config.getLineColor(), ColorChannel.LINE, mouseX);
		y = drawColorGroup(context, panelX, y, panelW, "Side", config.getSideColor(), ColorChannel.SIDE, mouseX);
		drawToggle(context, panelX, y, panelW, "Tracer", config.isTracer());
		y += ROW_H + 2;
		drawColorGroup(context, panelX, y, panelW, "Tracer", config.getTracerColor(), ColorChannel.TRACER, mouseX);

		int doneX = panelX + panelW - PAD - 40;
		int btnY = panelY + panelH - 22;
		context.fill(doneX, btnY, doneX + 40, btnY + 14, 0xFF284028);
		context.centeredText(font, "Done", doneX + 20, btnY + 3, 0xFF88FF88);
	}

	private void drawModeRow(GuiGraphicsExtractor context, int panelX, int y, int panelW, String name, String value) {
		var font = Minecraft.getInstance().font;
		context.fill(panelX + PAD, y, panelX + panelW - PAD, y + ROW_H, 0xFF161620);
		context.text(font, name, panelX + PAD + 3, y + 4, 0xFFCCCCCC);
		context.text(font, value, panelX + panelW - PAD - font.width(value) - 3, y + 4, 0xFF66FFAA);
	}

	private void drawToggle(GuiGraphicsExtractor context, int panelX, int y, int panelW, String name, boolean on) {
		var font = Minecraft.getInstance().font;
		context.fill(panelX + PAD, y, panelX + panelW - PAD, y + ROW_H, 0xFF161620);
		context.text(font, name, panelX + PAD + 3, y + 4, 0xFFCCCCCC);
		String value = on ? "ON" : "OFF";
		context.text(font, value, panelX + panelW - PAD - font.width(value) - 3, y + 4, on ? 0xFF66FFAA : 0xFF666666);
	}

	private int drawColorGroup(
		GuiGraphicsExtractor context,
		int panelX,
		int y,
		int panelW,
		String prefix,
		int color,
		ColorChannel channel,
		int mouseX
	) {
		y = drawChannel(context, panelX, y, panelW, prefix + " R", (color >> 16) & 0xFF, channel, 16, mouseX);
		y = drawChannel(context, panelX, y, panelW, prefix + " G", (color >> 8) & 0xFF, channel, 8, mouseX);
		y = drawChannel(context, panelX, y, panelW, prefix + " B", color & 0xFF, channel, 0, mouseX);
		return drawChannel(context, panelX, y, panelW, prefix + " A", (color >>> 24) & 0xFF, channel, 24, mouseX);
	}

	private int drawChannel(
		GuiGraphicsExtractor context,
		int panelX,
		int y,
		int panelW,
		String name,
		int value,
		ColorChannel channel,
		int shift,
		int mouseX
	) {
		var font = Minecraft.getInstance().font;
		context.fill(panelX + PAD, y, panelX + panelW - PAD, y + ROW_H, 0xFF161620);
		context.text(font, name, panelX + PAD + 3, y + 4, 0xFFCCCCCC);

		int sliderX = panelX + panelW / 2;
		int sliderW = panelW / 2 - PAD - 4;
		context.fill(sliderX, y + 7, sliderX + sliderW, y + 9, 0xFF303040);
		int fill = (int) (sliderW * (value / 255.0));
		context.fill(sliderX, y + 7, sliderX + fill, y + 9, 0xFF66AAFF);
		context.text(font, String.valueOf(value), sliderX + sliderW - font.width(String.valueOf(value)), y + 2, 0xFFAAAAAA);

		if (dragging != null && dragging.channel == channel && dragging.shift == shift) {
			updateChannel(channel, shift, mouseX, sliderX, sliderW);
		}
		return y + ROW_H + 1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		int panelW = Math.min(280, width - 40);
		int panelH = 220;
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;
		int doneX = panelX + panelW - PAD - 40;
		int btnY = panelY + panelH - 22;

		if (button == 0 && mouseX >= doneX && mouseX < doneX + 40 && mouseY >= btnY && mouseY < btnY + 14) {
			onSave.accept(config.copy());
			Minecraft.getInstance().setScreen(parent);
			return true;
		}

		int y = panelY + 24;
		if (button == 0 && hitRow(mouseX, mouseY, panelX, y, panelW)) {
			config.setShapeMode(config.getShapeMode().next());
			return true;
		}
		y += ROW_H + 2;

		y = handleColorGroupClick(mouseX, mouseY, button, panelX, y, panelW, ColorChannel.LINE);
		y = handleColorGroupClick(mouseX, mouseY, button, panelX, y, panelW, ColorChannel.SIDE);
		if (button == 0 && hitRow(mouseX, mouseY, panelX, y, panelW)) {
			config.setTracer(!config.isTracer());
			return true;
		}
		y += ROW_H + 2;
		handleColorGroupClick(mouseX, mouseY, button, panelX, y, panelW, ColorChannel.TRACER);
		return super.mouseClicked(event, doubleClick);
	}

	private int handleColorGroupClick(
		double mouseX,
		double mouseY,
		int button,
		int panelX,
		int y,
		int panelW,
		ColorChannel channel
	) {
		y = handleChannelClick(mouseX, mouseY, button, panelX, y, panelW, channel, 16);
		y = handleChannelClick(mouseX, mouseY, button, panelX, y, panelW, channel, 8);
		y = handleChannelClick(mouseX, mouseY, button, panelX, y, panelW, channel, 0);
		return handleChannelClick(mouseX, mouseY, button, panelX, y, panelW, channel, 24);
	}

	private int handleChannelClick(
		double mouseX,
		double mouseY,
		int button,
		int panelX,
		int y,
		int panelW,
		ColorChannel channel,
		int shift
	) {
		if (button == 0 && hitRow(mouseX, mouseY, panelX, y, panelW)) {
			dragging = new NumberRow(channel, shift);
			int sliderX = panelX + panelW / 2;
			int sliderW = panelW / 2 - PAD - 4;
			updateChannel(channel, shift, mouseX, sliderX, sliderW);
		}
		return y + ROW_H + 1;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging != null) {
			int panelW = Math.min(280, width - 40);
			int panelX = (width - panelW) / 2;
			int sliderX = panelX + panelW / 2;
			int sliderW = panelW / 2 - PAD - 4;
			updateChannel(dragging.channel, dragging.shift, event.x(), sliderX, sliderW);
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onSave.accept(config.copy());
			Minecraft.getInstance().setScreen(parent);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void updateChannel(ColorChannel channel, int shift, double mouseX, int sliderX, int sliderW) {
		double percent = Math.max(0, Math.min(1, (mouseX - sliderX) / (double) sliderW));
		int value = (int) Math.round(percent * 255.0);
		int color = switch (channel) {
			case LINE -> config.getLineColor();
			case SIDE -> config.getSideColor();
			case TRACER -> config.getTracerColor();
		};
		int mask = ~(0xFF << shift);
		int next = (color & mask) | ((value & 0xFF) << shift);
		switch (channel) {
			case LINE -> config.setLineColor(next);
			case SIDE -> config.setSideColor(next);
			case TRACER -> config.setTracerColor(next);
		}
	}

	private static boolean hitRow(double mouseX, double mouseY, int panelX, int y, int panelW) {
		return mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD && mouseY >= y && mouseY < y + ROW_H;
	}

	private enum ColorChannel {
		LINE,
		SIDE,
		TRACER
	}

	private record NumberRow(ColorChannel channel, int shift) {
	}
}
