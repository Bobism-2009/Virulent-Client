package dev.virulent.client.gui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared ClickGUI paint helpers for borders, toggles, and sliders.
 */
final class GuiPaint {
	static final int OVERLAY = 0x66000000;
	static final int WINDOW_BG = 0xF012121A;
	static final int WINDOW_INNER = 0xFF0E0E16;
	static final int PANEL_BG = 0xFF16161F;
	static final int PANEL_HOVER = 0xFF1C1C28;
	static final int SIDEBAR_BG = 0xFF0A0A12;
	static final int BORDER = 0xFF2A2A38;
	static final int BORDER_SOFT = 0xFF222230;
	static final int TEXT = 0xFFE8E8F0;
	static final int TEXT_DIM = 0xFF8A8A9A;
	static final int TEXT_MUTED = 0xFF5C5C6C;
	static final int TRACK = 0xFF2A2A38;
	static final int DANGER = 0xFFE07070;
	static final int DANGER_BG = 0xFF241818;
	static final int DANGER_HOVER = 0xFF301E1E;

	private GuiPaint() {
	}

	static void fill(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
		context.fill(x1, y1, x2, y2, color);
	}

	static void border(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
		fill(context, x, y, x + w, y + 1, color);
		fill(context, x, y + h - 1, x + w, y + h, color);
		fill(context, x, y, x + 1, y + h, color);
		fill(context, x + w - 1, y, x + w, y + h, color);
	}

	static void inset(GuiGraphicsExtractor context, int x, int y, int w, int h, int bg, int border) {
		fill(context, x, y, x + w, y + h, bg);
		border(context, x, y, w, h, border);
	}

	static void accentStrip(GuiGraphicsExtractor context, int x, int y, int h, int accent) {
		fill(context, x, y, x + 2, y + h, accent | 0xFF000000);
	}

	static void topAccent(GuiGraphicsExtractor context, int x, int y, int w, int accent) {
		fill(context, x, y, x + w, y + 1, accent | 0xFF000000);
		fill(context, x, y + 1, x + w, y + 2, withAlpha(accent, 0x55));
	}

	static void toggle(GuiGraphicsExtractor context, int x, int y, boolean on, int accent) {
		int trackW = 16;
		int trackH = 8;
		int trackColor = on ? withAlpha(accent, 0xAA) : 0xFF303040;
		fill(context, x, y, x + trackW, y + trackH, trackColor);
		border(context, x, y, trackW, trackH, on ? accent : BORDER);
		int knobX = on ? x + trackW - 7 : x + 1;
		fill(context, knobX, y + 1, knobX + 6, y + trackH - 1, on ? 0xFFF0FFF0 : 0xFFB0B0C0);
	}

	static void slider(GuiGraphicsExtractor context, int x, int y, int w, double percent, int accent) {
		int trackY = y + 1;
		fill(context, x, trackY, x + w, trackY + 3, TRACK);
		int fillW = Math.max(0, Math.min(w, (int) Math.round(w * percent)));
		if (fillW > 0) {
			fill(context, x, trackY, x + fillW, trackY + 3, accent | 0xFF000000);
		}
		int knobX = x + Math.max(0, Math.min(w - 4, fillW - 2));
		fill(context, knobX, y, knobX + 4, y + 5, 0xFFF0F0F8);
		border(context, knobX, y, 4, 5, accent | 0xFF000000);
	}

	static void chip(GuiGraphicsExtractor context, String text, int x, int y, int accent, boolean active) {
		var font = Minecraft.getInstance().font;
		int tw = font.width(text);
		int pad = 3;
		int bg = active ? withAlpha(accent, 0x55) : 0xFF222230;
		fill(context, x, y, x + tw + pad * 2, y + 9, bg);
		if (active) {
			border(context, x, y, tw + pad * 2, 9, withAlpha(accent, 0xAA));
		}
		context.text(font, text, x + pad, y + 1, active ? accent : TEXT_MUTED);
	}

	static void chipRight(GuiGraphicsExtractor context, String text, int right, int y, int accent, boolean active) {
		var font = Minecraft.getInstance().font;
		int tw = font.width(text);
		chip(context, text, right - tw - 6, y, accent, active);
	}

	static int blend(int colorA, int colorB, float ratio) {
		int aA = (colorA >> 24) & 0xFF;
		int rA = (colorA >> 16) & 0xFF;
		int gA = (colorA >> 8) & 0xFF;
		int bA = colorA & 0xFF;
		int aB = (colorB >> 24) & 0xFF;
		int rB = (colorB >> 16) & 0xFF;
		int gB = (colorB >> 8) & 0xFF;
		int bB = colorB & 0xFF;
		int a = (int) (aA + (aB - aA) * ratio);
		int r = (int) (rA + (rB - rA) * ratio);
		int g = (int) (gA + (gB - gA) * ratio);
		int b = (int) (bA + (bB - bA) * ratio);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
