package dev.virulent.client.module.modules.render;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.Render2DUtil;
import dev.virulent.client.util.WorldToScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class Tracers extends Module {
	private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", true));
	private final ModeSetting friendsMode = addSetting(new ModeSetting("Friends", "Highlight", "Normal", "Highlight", "Hide"));
	private final NumberSetting range = addSetting(new NumberSetting("Range", 64.0, 8.0, 128.0, 8.0));
	private final NumberSetting thickness = addSetting(new NumberSetting("Thickness", 1.0, 0.5, 5.0, 0.5));

	public Tracers() {
		super("Tracers", "Draws lines to nearby entities.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	private void onRender2D(Render2DEvent event) {
		if (mc().level == null || mc().player == null || mc().options.hideGui) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		int screenW = mc().getWindow().getGuiScaledWidth();
		int screenH = mc().getWindow().getGuiScaledHeight();
		float cursorX = screenW * 0.5f;
		float cursorY = screenH * 0.5f;

		double rangeValue = range.getValue();
		AABB searchBox = mc().player.getBoundingBox().inflate(rangeValue);
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);

		for (Entity entity : mc().level.getEntities(mc().player, searchBox)) {
			if (entity == mc().player || !(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			if (living.isSpectator()) {
				continue;
			}

			boolean player = entity instanceof Player;
			if (living.isInvisible() && !player) {
				continue;
			}
			if (playersOnly.getValue() && !player) {
				continue;
			}
			boolean friend = friends().isFriend(entity);
			if ("Hide".equals(friendsMode.getValue()) && friend) {
				continue;
			}
			if (mc().player.distanceTo(entity) > rangeValue) {
				continue;
			}

			Vec3 pos = entity.getPosition(tickDelta);
			Vec3 target = pos.add(0.0, living.getBbHeight() * 0.5, 0.0);
			float[] screen = WorldToScreen.project(target, tickDelta);
			if (screen == null) {
				continue;
			}

			int color;
			if (friend && "Highlight".equals(friendsMode.getValue())) {
				color = FriendsManager.FRIEND_COLOR;
			} else if (player) {
				color = living.isInvisible() ? 0xFFFFAA00 : 0xFF39FF14;
			} else {
				color = 0xFFB026FF;
			}
			Render2DUtil.drawLine(context, cursorX, cursorY, screen[0], screen[1], color, thickness.getValue().floatValue());
		}
	}

	private FriendsManager friends() {
		return VirulentClient.getInstance().getFriendsManager();
	}
}
