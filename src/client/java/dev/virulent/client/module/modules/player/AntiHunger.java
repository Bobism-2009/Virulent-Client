package dev.virulent.client.module.modules.player;

import dev.virulent.client.mixin.ServerboundMovePlayerPacketAccessor;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of Meteor Client AntiHunger:
 * spoofs sprint-start packets and the onGround flag to reduce hunger drain.
 * Does NOT fully remove hunger consumption.
 */
public final class AntiHunger extends Module {
	private static AntiHunger instance;

	private final BooleanSetting sprint = addSetting(new BooleanSetting("Sprint", true));
	private final BooleanSetting onGround = addSetting(new BooleanSetting("On Ground", true));

	private boolean lastOnGround;
	private boolean ignorePacket;

	public AntiHunger() {
		super("AntiHunger", "Reduces (does NOT remove) hunger consumption.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	@Override
	protected void onEnable() {
		LocalPlayer player = mc().player;
		if (player != null) {
			lastOnGround = player.onGround();
		}
		ignorePacket = false;
	}

	/**
	 * Meteor: {@code SendMovementPacketsEvent.Pre} — mark the next move packet to keep
	 * real onGround when landing so fall damage still applies.
	 */
	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		if (player == null || !onGround.getValue()) {
			if (player != null) {
				lastOnGround = player.onGround();
			}
			return;
		}

		if (player.onGround() && !lastOnGround) {
			ignorePacket = true;
		}
		lastOnGround = player.onGround();
	}

	public static void onOutgoing(Packet<?> packet, CallbackInfo ci) {
		if (!isActive()) {
			return;
		}
		instance.handleOutgoing(packet, ci);
	}

	private void handleOutgoing(Packet<?> packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			return;
		}

		if (ignorePacket && packet instanceof ServerboundMovePlayerPacket) {
			ignorePacket = false;
			return;
		}

		if (player.isPassenger() || player.isInWater() || player.isUnderWater()) {
			return;
		}

		if (sprint.getValue()
			&& packet instanceof ServerboundPlayerCommandPacket command
			&& command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
			ci.cancel();
			return;
		}

		if (onGround.getValue()
			&& packet instanceof ServerboundMovePlayerPacket move
			&& player.onGround()
			&& player.fallDistance <= 0.0f
			&& !client.gameMode.isDestroying()) {
			((ServerboundMovePlayerPacketAccessor) (Object) move).setOnGround(false);
		}
	}
}
