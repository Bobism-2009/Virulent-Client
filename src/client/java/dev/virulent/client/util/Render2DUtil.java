package dev.virulent.client.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Render2DUtil {
	private Render2DUtil() {
	}

	public static void drawLine(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color) {
		drawLine(context, x1, y1, x2, y2, color, 1.0f);
	}

	public static void drawLine(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color, float thickness) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.5f) {
			return;
		}

		float t = Math.max(0.5f, thickness);
		int iLength = Math.max(1, Math.round(length));
		int halfUp = Math.max(1, Math.round(t * 0.5f));
		int halfDown = Math.max(0, Math.round(t) - halfUp);

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.rotate((float) Math.atan2(dy, dx));
		context.fill(0, -halfDown, iLength, halfUp, color);
		pose.popMatrix();
	}
}
