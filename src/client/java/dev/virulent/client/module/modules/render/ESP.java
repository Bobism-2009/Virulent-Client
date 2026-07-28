package dev.virulent.client.module.modules.render;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.Render2DUtil;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.util.WorldToScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class ESP extends Module {
	private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Box", "Box", "Tracer"));
	private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", true));
	private final ModeSetting friendsMode = addSetting(new ModeSetting("Friends", "Highlight", "Normal", "Highlight", "Hide"));
	private final BooleanSetting names = addSetting(new BooleanSetting("Names", true));
	private final NumberSetting nameScale = addSetting(new NumberSetting("Name Scale", 0.55, 0.2, 1.5, 0.05));
	private final NumberSetting range = addSetting(new NumberSetting("Range", 64.0, 8.0, 128.0, 8.0));
	private final NumberSetting thickness = addSetting(new NumberSetting("Thickness", 1.0, 0.5, 5.0, 0.5));

	public ESP() {
		super("ESP", "Highlights entities through walls.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render3DEvent.class, this::onRender3D);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	public boolean needsWorldRender() {
		return isEnabled() && mode.getValue().equals("Box");
	}

	private void onRender3D(Render3DEvent event) {
		if (mc().level == null || mc().player == null || !mode.getValue().equals("Box")) {
			return;
		}

		double rangeValue = range.getValue();
		AABB searchBox = mc().player.getBoundingBox().inflate(rangeValue);
		boolean showNames = names.getValue();
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);

		RenderUtil.beginLines(event.getContext());
		for (Entity entity : mc().level.getEntities(mc().player, searchBox)) {
			if (!isValidTarget(entity)) {
				continue;
			}

			LivingEntity living = (LivingEntity) entity;
			boolean player = entity instanceof Player;
			boolean friend = friends().isFriend(entity);
			int color = colorFor(player, living.isInvisible(), friend);
			Vec3 pos = entity.getPosition(tickDelta);
			AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));
			RenderUtil.addBox(box, color);

			if (showNames && player) {
				Vec3 namePos = pos.add(0.0, living.getBbHeight() + 0.45, 0.0);
				Gizmos.billboardText(
					entity.getName().getString(),
					namePos,
					TextGizmo.Style.forColorAndCentered(color).withScale(nameScale.getValue().floatValue())
				).setAlwaysOnTop();
			}
		}
		RenderUtil.endLines();
	}

	private void onRender2D(Render2DEvent event) {
		if (mc().level == null || mc().player == null || mc().options.hideGui || !mode.getValue().equals("Tracer")) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		float cursorX = mc().getWindow().getGuiScaledWidth() * 0.5f;
		float cursorY = mc().getWindow().getGuiScaledHeight() * 0.5f;
		double rangeValue = range.getValue();
		AABB searchBox = mc().player.getBoundingBox().inflate(rangeValue);
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);

		for (Entity entity : mc().level.getEntities(mc().player, searchBox)) {
			if (!isValidTarget(entity)) {
				continue;
			}

			LivingEntity living = (LivingEntity) entity;
			boolean player = entity instanceof Player;
			boolean friend = friends().isFriend(entity);
			Vec3 pos = entity.getPosition(tickDelta);
			float[] screen = WorldToScreen.project(pos.add(0.0, living.getEyeHeight() * 0.5, 0.0), tickDelta);
			if (screen == null) {
				continue;
			}

			int color = colorFor(player, living.isInvisible(), friend);
			Render2DUtil.drawLine(context, cursorX, cursorY, screen[0], screen[1], color, thickness.getValue().floatValue());
		}
	}

	private boolean isValidTarget(Entity entity) {
		if (entity == mc().player || !(entity instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		if (living.isSpectator()) {
			return false;
		}
		boolean player = entity instanceof Player;
		if (living.isInvisible() && !player) {
			return false;
		}
		if (playersOnly.getValue() && !player) {
			return false;
		}
		if ("Hide".equals(friendsMode.getValue()) && friends().isFriend(entity)) {
			return false;
		}
		return !(mc().player.distanceTo(entity) > range.getValue());
	}

	private int colorFor(boolean player, boolean invisible, boolean friend) {
		if (friend && "Highlight".equals(friendsMode.getValue())) {
			return FriendsManager.FRIEND_COLOR;
		}
		if (player) {
			return invisible ? 0xFFFFAA00 : 0xFF39FF14;
		}
		return 0xFFB026FF;
	}

	private FriendsManager friends() {
		return VirulentClient.getInstance().getFriendsManager();
	}
}
