package dev.virulent.client.module.modules.render;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.KeyEvent;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.ActionSetting;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.KeybindSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.Render2DUtil;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.util.WorldToScreen;
import dev.virulent.client.waypoint.Waypoint;
import dev.virulent.client.waypoint.WaypointCoords;
import dev.virulent.client.waypoint.WaypointManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public final class Waypoints extends Module {
	private final ActionSetting manage = addSetting(new ActionSetting("Manage", "Open"));
	private final BooleanSetting beams = addSetting(new BooleanSetting("Beams", true));
	private final BooleanSetting boxes = addSetting(new BooleanSetting("Boxes", true));
	private final BooleanSetting labels = addSetting(new BooleanSetting("Labels", true));
	private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));
	private final BooleanSetting offscreen = addSetting(new BooleanSetting("Offscreen", true));
	private final BooleanSetting distanceHud = addSetting(new BooleanSetting("Distance HUD", true));
	private final BooleanSetting deathWaypoint = addSetting(new BooleanSetting("Death Waypoint", true));
	private final BooleanSetting crossDim = addSetting(new BooleanSetting("Cross Dim", true));
	private final NumberSetting maxDistance = addSetting(new NumberSetting("Max Distance", 10000.0, 64.0, 50000.0, 64.0));
	private final NumberSetting beamHeight = addSetting(new NumberSetting("Beam Height", 128.0, 16.0, 512.0, 8.0));
	private final NumberSetting thickness = addSetting(new NumberSetting("Thickness", 1.5, 0.5, 5.0, 0.5));
	private final NumberSetting labelScale = addSetting(new NumberSetting("Label Scale", 1.0, 0.7, 2.0, 0.1));
	private final KeybindSetting addHereKey = addSetting(new KeybindSetting("Add Here", GLFW.GLFW_KEY_UNKNOWN));
	private final KeybindSetting addLookKey = addSetting(new KeybindSetting("Add Look", GLFW.GLFW_KEY_UNKNOWN));

	/** null = unknown (just joined / left world) */
	private Boolean wasAlive;

	public Waypoints() {
		super("Waypoints", "Save coordinates and show them in the world.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render3DEvent.class, this::onRender3D);
		subscribe(Render2DEvent.class, this::onRender2D);
		subscribe(KeyEvent.class, this::onKey);
	}

	public ActionSetting getManageSetting() {
		return manage;
	}

	private WaypointManager manager() {
		return VirulentClient.getInstance().getWaypointManager();
	}

	private void onKey(KeyEvent event) {
		if (!event.isPressed() || mc().screen != null || mc().player == null || mc().level == null) {
			return;
		}
		if (addHereKey.getValue() != GLFW.GLFW_KEY_UNKNOWN && event.getKey() == addHereKey.getValue()) {
			addAtPlayer();
		} else if (addLookKey.getValue() != GLFW.GLFW_KEY_UNKNOWN && event.getKey() == addLookKey.getValue()) {
			addAtLook();
		}
	}

	public void addAtPlayer() {
		if (mc().player == null || mc().level == null) {
			return;
		}
		WaypointManager manager = manager();
		String name = nextName("WP");
		manager.add(new Waypoint(
			name,
			mc().player.getX(),
			mc().player.getY(),
			mc().player.getZ(),
			manager.currentDimensionId(),
			manager.nextColor()
		));
		message("§aWaypoint §f" + name + " §asaved at your position.");
	}

	public void addAtLook() {
		if (mc().player == null || mc().level == null) {
			return;
		}
		Vec3 eye = mc().player.getEyePosition();
		Vec3 end = eye.add(mc().player.getViewVector(1.0f).scale(256.0));
		BlockHitResult hit = mc().level.clip(new ClipContext(
			eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc().player
		));
		double x;
		double y;
		double z;
		if (hit.getType() == HitResult.Type.BLOCK) {
			x = hit.getBlockPos().getX() + 0.5;
			y = hit.getBlockPos().getY() + 1.0;
			z = hit.getBlockPos().getZ() + 0.5;
		} else {
			x = end.x;
			y = end.y;
			z = end.z;
		}
		WaypointManager manager = manager();
		String name = nextName("Look");
		manager.add(new Waypoint(name, x, y, z, manager.currentDimensionId(), manager.nextColor()));
		message("§aWaypoint §f" + name + " §asaved at look position.");
	}

	private String nextName(String prefix) {
		return prefix + " " + (manager().size() + 1);
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null) {
			wasAlive = null;
			return;
		}
		if (!deathWaypoint.getValue()) {
			wasAlive = !mc().player.isDeadOrDying();
			return;
		}

		boolean alive = !mc().player.isDeadOrDying();
		if (wasAlive != null && wasAlive && !alive) {
			Waypoint death = manager().recordDeath(
				mc().player.getX(),
				mc().player.getY(),
				mc().player.getZ(),
				manager().currentDimensionId()
			);
			message("§cDeath waypoint §f" + (int) Math.round(death.getX())
				+ " " + (int) Math.round(death.getY())
				+ " " + (int) Math.round(death.getZ()));
		}
		wasAlive = alive;
	}

	private void onRender3D(Render3DEvent event) {
		if (mc().level == null || mc().player == null) {
			return;
		}

		String dimension = manager().currentDimensionId();
		double maxDist = maxDistance.getValue();
		double maxDistSq = maxDist * maxDist;
		Vec3 playerPos = mc().player.position();
		boolean drawBeams = beams.getValue();
		boolean drawBoxes = boxes.getValue();
		double baseHeight = beamHeight.getValue();
		boolean showCross = crossDim.getValue();

		RenderUtil.beginLines(event.getContext());
		for (Waypoint waypoint : manager().getWaypoints()) {
			Vec3 pos = resolveRenderPos(waypoint, dimension, showCross);
			if (pos == null) {
				continue;
			}
			double dist = playerPos.distanceTo(pos);
			if (dist * dist > maxDistSq) {
				continue;
			}

			boolean linked = !waypoint.getDimension().equals(dimension);
			int color = linked ? withAlpha(waypoint.getColor(), 0x88) : waypoint.getColor();
			double size = Math.min(8.0, 0.35 + dist * 0.004);
			double height = Math.min(512.0, baseHeight + dist * 0.35);

			if (drawBoxes) {
				RenderUtil.addBox(new AABB(
					pos.x - size, pos.y, pos.z - size,
					pos.x + size, pos.y + size * 2.0, pos.z + size
				), color);
			}
			if (drawBeams) {
				Vec3 top = pos.add(0.0, height, 0.0);
				RenderUtil.addLine(pos, top, color);
				double pad = Math.min(1.5, size * 0.35);
				RenderUtil.addLine(pos.add(pad, 0.0, 0.0), top.add(pad, 0.0, 0.0), color);
				RenderUtil.addLine(pos.add(-pad, 0.0, 0.0), top.add(-pad, 0.0, 0.0), color);
				RenderUtil.addLine(pos.add(0.0, 0.0, pad), top.add(0.0, 0.0, pad), color);
				RenderUtil.addLine(pos.add(0.0, 0.0, -pad), top.add(0.0, 0.0, -pad), color);
			}
		}
		RenderUtil.endLines();
	}

	private void onRender2D(Render2DEvent event) {
		if (mc().level == null || mc().player == null || mc().options.hideGui) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		String dimension = manager().currentDimensionId();
		double maxDist = maxDistance.getValue();
		double maxDistSq = maxDist * maxDist;
		Vec3 playerPos = mc().player.position();
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float cursorX = mc().getWindow().getGuiScaledWidth() * 0.5f;
		float cursorY = mc().getWindow().getGuiScaledHeight() * 0.5f;
		float lineThickness = thickness.getValue().floatValue();
		float scale = labelScale.getValue().floatValue();
		boolean showCross = crossDim.getValue();
		var font = mc().font;

		int hudY = 28;
		for (Waypoint waypoint : manager().getWaypoints()) {
			Vec3 pos = resolveRenderPos(waypoint, dimension, showCross);
			if (pos == null) {
				continue;
			}
			boolean linked = !waypoint.getDimension().equals(dimension);
			Vec3 labelPos = pos.add(0.0, 0.5, 0.0);
			double distSq = playerPos.distanceToSqr(labelPos);
			if (distSq > maxDistSq) {
				continue;
			}
			int dist = (int) Math.round(Math.sqrt(distSq));
			int color = linked ? withAlpha(waypoint.getColor(), 0xAA) : waypoint.getColor();
			String labelName = linked
				? waypoint.getName() + " (" + WaypointCoords.shortDim(waypoint.getDimension()) + ")"
				: waypoint.getName();

			float[] projected = WorldToScreen.projectClamped(labelPos, tickDelta, 12.0f);
			if (projected != null) {
				boolean onScreen = projected[2] >= 0.5f;
				float x = projected[0];
				float y = projected[1];

				if (tracers.getValue()) {
					Render2DUtil.drawLine(context, cursorX, cursorY, x, y, color, lineThickness);
				}

				if (labels.getValue() && (onScreen || offscreen.getValue())) {
					drawScreenLabel(context, font, labelName, dist, x, y, color, scale, onScreen);
				}
			}

			if (distanceHud.getValue()) {
				String line = labelName + "  " + dist + "m";
				context.text(font, line, 4, hudY, color);
				hudY += 10;
			}
		}
	}

	private static Vec3 resolveRenderPos(Waypoint waypoint, String viewerDimension, boolean crossDim) {
		if (waypoint.getDimension().equals(viewerDimension)) {
			return new Vec3(waypoint.getX(), waypoint.getY(), waypoint.getZ());
		}
		if (!crossDim) {
			return null;
		}
		Optional<double[]> linked = WaypointCoords.crossDimPos(waypoint, viewerDimension);
		if (linked.isEmpty()) {
			return null;
		}
		double[] xyz = linked.get();
		return new Vec3(xyz[0], xyz[1], xyz[2]);
	}

	private static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	private static void drawScreenLabel(
		GuiGraphicsExtractor context,
		net.minecraft.client.gui.Font font,
		String name,
		int dist,
		float x,
		float y,
		int color,
		float scale,
		boolean onScreen
	) {
		String text = name + " [" + dist + "m]";
		int textW = font.width(text);
		int boxW = Math.round((textW + 8) * scale);
		int boxH = Math.round(12 * scale);
		int boxX = Math.round(x - boxW * 0.5f);
		int boxY = Math.round(y - boxH - (onScreen ? 6 : 2));

		// Marker diamond / arrow tip.
		int mx = Math.round(x);
		int my = Math.round(y);
		context.fill(mx - 2, my - 2, mx + 3, my + 3, color);
		context.fill(mx - 1, my - 4, mx + 2, my + 5, color | 0xFF000000);
		context.fill(mx - 4, my - 1, mx + 5, my + 2, color | 0xFF000000);

		context.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xAA000000);
		context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC101018);
		context.fill(boxX, boxY, boxX + 2, boxY + boxH, color);

		// Approximate scale by drawing normally; scale>1 just adds padding for readability.
		int textX = boxX + 5;
		int textY = boxY + Math.max(2, (boxH - 8) / 2);
		context.text(font, text, textX, textY, color);
	}

	private void message(String text) {
		if (mc().player != null) {
			mc().player.sendSystemMessage(Component.literal(text));
		}
	}
}
