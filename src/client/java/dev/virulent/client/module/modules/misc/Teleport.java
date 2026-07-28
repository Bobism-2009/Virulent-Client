package dev.virulent.client.module.modules.misc;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.KeyEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.KeybindSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class Teleport extends Module {
	private static final double PACKET_STEP = 8.0;

	private static Teleport instance;

	private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Click", "Click", "Player"));
	private final NumberSetting range = addSetting(new NumberSetting("Range", 64.0, 8.0, 256.0, 4.0));
	private final BooleanSetting leftClick = addSetting(new BooleanSetting("Left Click", true));
	private final BooleanSetting rightClick = addSetting(new BooleanSetting("Right Click", true));
	private final ModeSetting target = addSetting(new ModeSetting("Target", "None", "None"));
	private final KeybindSetting teleportKey = addSetting(new KeybindSetting("Teleport Key", GLFW.GLFW_KEY_UNKNOWN));
	private final NumberSetting yOffset = addSetting(new NumberSetting("Y Offset", 0.0, -2.0, 3.0, 0.5));

	private int refreshTimer;

	public Teleport() {
		super("Teleport", "Click to TP where you look, or teleport to online players.", Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
		subscribe(KeyEvent.class, this::onKey);
	}

	public static boolean handleAttackClick() {
		return instance != null && instance.isEnabled() && instance.onClickTp(true);
	}

	public static boolean handleUseClick() {
		return instance != null && instance.isEnabled() && instance.onClickTp(false);
	}

	@Override
	public void onTick() {
		if (!"Player".equals(mode.getValue())) {
			return;
		}
		if (refreshTimer-- > 0) {
			return;
		}
		refreshTimer = 20;
		refreshTargets();
	}

	private boolean onClickTp(boolean attack) {
		if (!"Click".equals(mode.getValue())) {
			return false;
		}
		if (mc().screen != null || mc().player == null || mc().level == null) {
			return false;
		}
		if (attack ? !leftClick.getValue() : !rightClick.getValue()) {
			return false;
		}
		return teleportToLook();
	}

	private void refreshTargets() {
		if (mc().getConnection() == null || mc().player == null) {
			target.replaceModes(List.of());
			return;
		}

		String self = mc().player.getGameProfile().name();
		List<String> names = new ArrayList<>();
		for (PlayerInfo info : mc().getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (!name.equalsIgnoreCase(self)) {
				names.add(name);
			}
		}
		names.sort(Comparator.naturalOrder());
		target.replaceModes(names);
	}

	private void onKey(KeyEvent event) {
		if (!event.isPressed() || teleportKey.getValue() == GLFW.GLFW_KEY_UNKNOWN) {
			return;
		}
		if (mc().screen != null) {
			return;
		}
		if (event.getKey() != teleportKey.getValue()) {
			return;
		}
		if ("Click".equals(mode.getValue())) {
			teleportToLook();
		} else {
			teleportToTarget();
		}
	}

	private boolean teleportToLook() {
		if (mc().player == null || mc().level == null || mc().player.connection == null) {
			return false;
		}

		double maxRange = range.getValue();
		Vec3 eye = mc().player.getEyePosition();
		Vec3 look = mc().player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(maxRange));

		BlockHitResult hit = mc().level.clip(new ClipContext(
			eye,
			end,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			mc().player
		));

		double x;
		double y;
		double z;
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos stand = standPos(hit);
			x = stand.getX() + 0.5;
			y = stand.getY() + yOffset.getValue();
			z = stand.getZ() + 0.5;
		} else {
			x = end.x;
			y = end.y + yOffset.getValue();
			z = end.z;
		}

		sendTeleportPath(x, y, z);
		return true;
	}

	private static BlockPos standPos(BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Direction face = hit.getDirection();
		if (face == Direction.UP) {
			return pos.above();
		}
		return pos.relative(face);
	}

	private void teleportToTarget() {
		if (mc().player == null || mc().level == null || mc().player.connection == null) {
			return;
		}

		String targetName = target.getValue();
		if (targetName.equals("None")) {
			message("§cNo players online.");
			return;
		}

		Player targetPlayer = findLoadedPlayer(targetName);
		if (targetPlayer == null) {
			message("§c" + targetName + " is too far away to teleport to.");
			return;
		}

		double x = targetPlayer.getX();
		double y = targetPlayer.getY() + yOffset.getValue();
		double z = targetPlayer.getZ();
		sendTeleportPath(x, y, z);
		message("§aTeleported to §f" + targetName);
	}

	private void sendTeleportPath(double x, double y, double z) {
		var player = mc().player;
		Vec3 start = player.position();
		Vec3 end = new Vec3(x, y, z);
		double distance = start.distanceTo(end);
		int steps = Math.max(1, (int) Math.ceil(distance / PACKET_STEP));

		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			double px = start.x + (end.x - start.x) * t;
			double py = start.y + (end.y - start.y) * t;
			double pz = start.z + (end.z - start.z) * t;
			boolean last = i == steps;

			player.connection.send(new ServerboundMovePlayerPacket.Pos(
				px, py, pz, last, player.horizontalCollision
			));
		}

		player.setPos(x, y, z);
		player.xo = x;
		player.yo = y;
		player.zo = z;
		player.xOld = x;
		player.yOld = y;
		player.zOld = z;
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0.0f;
		player.setOnGround(true);

		player.connection.send(new ServerboundMovePlayerPacket.PosRot(
			x, y, z, player.getYRot(), player.getXRot(), true, player.horizontalCollision
		));
	}

	private Player findLoadedPlayer(String name) {
		if (mc().getConnection() != null) {
			for (PlayerInfo info : mc().getConnection().getOnlinePlayers()) {
				if (!info.getProfile().name().equalsIgnoreCase(name)) {
					continue;
				}
				UUID id = info.getProfile().id();
				Player byId = mc().level.getPlayerByUUID(id);
				if (byId != null && byId != mc().player) {
					return byId;
				}
			}
		}

		for (Player player : mc().level.players()) {
			if (player == mc().player) {
				continue;
			}
			if (player.getGameProfile().name().equalsIgnoreCase(name)) {
				return player;
			}
		}
		return null;
	}

	private void message(String text) {
		if (mc().player != null) {
			mc().player.sendSystemMessage(Component.literal(text));
		} else {
			VirulentClient.LOGGER.info(text);
		}
	}
}
