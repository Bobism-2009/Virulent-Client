package dev.virulent.client.util;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * World overlays via the Gizmos system.
 * Per-frame gizmos with {@code setAlwaysOnTop()} draw through walls after depth clear.
 */
public final class RenderUtil {
	private static final float LINE_WIDTH = 2.5f;
	private static boolean active;

	private RenderUtil() {
	}

	public static void beginLines(LevelRenderContext context) {
		active = true;
	}

	public static void endLines() {
		active = false;
	}

	public static void beginFilledBoxes(LevelRenderContext context) {
		beginLines(context);
	}

	public static void endFilledBoxes() {
		endLines();
	}

	public static void addFilledBox(AABB worldBox, int color) {
		if (!active) {
			return;
		}

		int fill = ensureAlpha(color, 0x55);
		int stroke = ensureAlpha(color, 0xFF);
		Gizmos.cuboid(worldBox, GizmoStyle.strokeAndFill(stroke, LINE_WIDTH, fill)).setAlwaysOnTop();
	}

	public static void addBox(AABB worldBox, int color) {
		if (!active) {
			return;
		}

		Gizmos.cuboid(worldBox, GizmoStyle.stroke(ensureAlpha(color, 0xFF), LINE_WIDTH)).setAlwaysOnTop();
	}

	public static void addLine(Vec3 worldStart, Vec3 worldEnd, int color) {
		if (!active) {
			return;
		}

		Gizmos.line(worldStart, worldEnd, ensureAlpha(color, 0xFF), LINE_WIDTH).setAlwaysOnTop();
	}

	private static int ensureAlpha(int color, int defaultAlpha) {
		int alpha = (color >>> 24) & 0xFF;
		if (alpha == 0) {
			return (defaultAlpha << 24) | (color & 0x00FFFFFF);
		}
		return color;
	}
}
