package dev.virulent.client.module.modules.render;

import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.RenderUtil;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Port of Meteor Client LogoutSpots — draws a box where another player logged out.
 */
public final class LogoutSpots extends Module {
	private static final int BOX_COLOR = 0x88FF00FF;
	private static final int LINE_COLOR = 0xFFFF00FF;
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int HEALTH_GREEN = 0xFF19E119;
	private static final int HEALTH_ORANGE = 0xFFE16919;
	private static final int HEALTH_RED = 0xFFE11919;

	private final NumberSetting nameScale = addSetting(new NumberSetting("Name Scale", 0.55, 0.2, 1.5, 0.05));
	private final BooleanSetting fullHeight = addSetting(new BooleanSetting("Full Height", true));
	private final BooleanSetting filled = addSetting(new BooleanSetting("Filled", true));
	private final BooleanSetting names = addSetting(new BooleanSetting("Names", true));
	private final NumberSetting maxDistance = addSetting(new NumberSetting("Max Distance", 512.0, 32.0, 2048.0, 16.0));

	private final List<Entry> spots = new ArrayList<>();
	private final List<PlayerInfo> lastPlayerList = new ArrayList<>();
	private final List<PlayerSnapshot> lastPlayers = new ArrayList<>();

	private int timer;
	private DimensionType lastDimension;

	public LogoutSpots() {
		super("LogoutSpots", "Shows a box where another player logged out.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render3DEvent.class, this::onRender3D);
	}

	public boolean needsWorldRender() {
		return isEnabled();
	}

	@Override
	protected void onEnable() {
		spots.clear();
		lastPlayerList.clear();
		lastPlayers.clear();
		timer = 10;
		lastDimension = null;

		if (mc().getConnection() != null) {
			lastPlayerList.addAll(mc().getConnection().getOnlinePlayers());
		}
		updateLastPlayers();
		if (mc().level != null) {
			lastDimension = mc().level.dimensionType();
		}
	}

	@Override
	protected void onDisable() {
		spots.clear();
		lastPlayerList.clear();
		lastPlayers.clear();
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().getConnection() == null) {
			spots.clear();
			lastPlayerList.clear();
			lastPlayers.clear();
			lastDimension = null;
			return;
		}

		// Someone rejoined — clear their logout ghost.
		for (Player player : mc().level.players()) {
			if (player == mc().player) {
				continue;
			}
			spots.removeIf(spot -> spot.uuid.equals(player.getUUID()));
		}

		List<PlayerInfo> online = new ArrayList<>(mc().getConnection().getOnlinePlayers());
		if (online.size() != lastPlayerList.size()) {
			for (PlayerInfo entry : lastPlayerList) {
				boolean stillOnline = online.stream().anyMatch(info -> info.getProfile().id().equals(entry.getProfile().id()));
				if (stillOnline) {
					continue;
				}

				UUID leftId = entry.getProfile().id();
				for (PlayerSnapshot snapshot : lastPlayers) {
					if (snapshot.uuid.equals(leftId)) {
						add(new Entry(snapshot));
						break;
					}
				}
			}

			lastPlayerList.clear();
			lastPlayerList.addAll(online);
			updateLastPlayers();
		}

		if (timer <= 0) {
			updateLastPlayers();
			timer = 10;
		} else {
			timer--;
		}

		DimensionType dimension = mc().level.dimensionType();
		if (lastDimension != null && dimension != lastDimension) {
			spots.clear();
		}
		lastDimension = dimension;
	}

	private void updateLastPlayers() {
		lastPlayers.clear();
		if (mc().level == null || mc().player == null) {
			return;
		}
		for (Player player : mc().level.players()) {
			if (player == mc().player) {
				continue;
			}
			lastPlayers.add(new PlayerSnapshot(player));
		}
	}

	private void add(Entry entry) {
		spots.removeIf(spot -> spot.uuid.equals(entry.uuid));
		spots.add(entry);
	}

	private void onRender3D(Render3DEvent event) {
		if (mc().level == null || mc().player == null || spots.isEmpty()) {
			return;
		}

		double maxDist = maxDistance.getValue();
		double maxDistSq = maxDist * maxDist;
		Vec3 camera = mc().player.position();
		boolean drawNames = names.getValue();
		boolean drawFilled = filled.getValue();
		boolean drawFull = fullHeight.getValue();
		float scale = nameScale.getValue().floatValue();

		RenderUtil.beginFilledBoxes(event.getContext());
		for (Entry spot : spots) {
			double distSq = camera.distanceToSqr(spot.centerX, spot.y, spot.centerZ);
			if (distSq > maxDistSq) {
				continue;
			}

			AABB box = drawFull
				? new AABB(spot.x, spot.y, spot.z, spot.x + spot.xWidth, spot.y + spot.height, spot.z + spot.zWidth)
				: new AABB(spot.x, spot.y, spot.z, spot.x + spot.xWidth, spot.y + 0.05, spot.z + spot.zWidth);

			if (drawFilled) {
				RenderUtil.addFilledBox(box, BOX_COLOR);
			} else {
				RenderUtil.addBox(box, LINE_COLOR);
			}

			if (drawNames) {
				int healthColor = healthColor(spot.health, spot.maxHealth);
				Vec3 namePos = new Vec3(spot.centerX, spot.y + (drawFull ? spot.height : 0.05) + 0.45, spot.centerZ);
				Gizmos.billboardText(
					spot.name,
					namePos,
					TextGizmo.Style.forColorAndCentered(NAME_COLOR).withScale(scale)
				).setAlwaysOnTop();
				Gizmos.billboardText(
					" " + spot.health,
					namePos.add(0.0, 0.22 * scale, 0.0),
					TextGizmo.Style.forColorAndCentered(healthColor).withScale(scale * 0.9f)
				).setAlwaysOnTop();
			}
		}
		RenderUtil.endFilledBoxes();
	}

	private static int healthColor(int health, int maxHealth) {
		if (maxHealth <= 0) {
			return HEALTH_GREEN;
		}
		double pct = (double) health / (double) maxHealth;
		if (pct <= 0.333) {
			return HEALTH_RED;
		}
		if (pct <= 0.666) {
			return HEALTH_ORANGE;
		}
		return HEALTH_GREEN;
	}

	private static final class PlayerSnapshot {
		final UUID uuid;
		final String name;
		final double x;
		final double y;
		final double z;
		final double width;
		final double height;
		final int health;
		final int maxHealth;

		PlayerSnapshot(Player player) {
			uuid = player.getUUID();
			name = player.getName().getString();
			x = player.getX();
			y = player.getY();
			z = player.getZ();
			width = player.getBbWidth();
			height = player.getBbHeight();
			health = Math.round(player.getHealth() + player.getAbsorptionAmount());
			maxHealth = Math.round(player.getMaxHealth() + player.getAbsorptionAmount());
		}
	}

	private static final class Entry {
		final UUID uuid;
		final String name;
		final double x;
		final double y;
		final double z;
		final double xWidth;
		final double zWidth;
		final double height;
		final double centerX;
		final double centerZ;
		final int health;
		final int maxHealth;

		Entry(PlayerSnapshot player) {
			double halfWidth = player.width / 2.0;
			uuid = player.uuid;
			name = player.name;
			x = player.x - halfWidth;
			y = player.y;
			z = player.z - halfWidth;
			xWidth = player.width;
			zWidth = player.width;
			height = player.height;
			centerX = player.x;
			centerZ = player.z;
			health = player.health;
			maxHealth = player.maxHealth;
		}
	}
}
