package dev.virulent.client.module.modules.combat;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.module.ModuleManager;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of Meteor Client Criticals — packet / NCP / jump crits plus optional mace smash spoof.
 */
public final class Criticals extends Module {
	private static Criticals instance;

	private final ModeSetting mode = addSetting(new ModeSetting(
		"Mode", "Packet", "None", "Packet", "UpdatedNCP", "OldNCP", "Jump", "MiniJump"
	));
	private final BooleanSetting onlyKillAura = addSetting(new BooleanSetting("Only KillAura", false));
	private final BooleanSetting smashAttack = addSetting(new BooleanSetting("Smash Attack", true));
	private final NumberSetting extraHeight = addSetting(new NumberSetting("Additional Height", 0.0, 0.0, 100.0, 0.5));

	private ServerboundAttackPacket attackPacket;
	private ServerboundSwingPacket swingPacket;
	private boolean sendPackets;
	private int sendTimer;
	private double lastY;
	private boolean waitingForPeak;
	private boolean sending;

	public Criticals() {
		super("Criticals", "Performs critical attacks when you hit your target.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	@Override
	protected void onEnable() {
		attackPacket = null;
		swingPacket = null;
		sendPackets = false;
		sendTimer = 0;
		lastY = 0;
		waitingForPeak = false;
		sending = false;
	}

	@Override
	protected void onDisable() {
		onEnable();
	}

	/**
	 * Called from {@link dev.virulent.client.mixin.ConnectionMixin} for outgoing attack / swing packets.
	 */
	public static void onOutgoing(Packet<?> packet, CallbackInfo ci) {
		if (!isActive() || instance.sending) {
			return;
		}
		instance.handleOutgoing(packet, ci);
	}

	private void handleOutgoing(Packet<?> packet, CallbackInfo ci) {
		if (mc().player == null || mc().level == null || mc().getConnection() == null) {
			return;
		}

		if (packet instanceof ServerboundAttackPacket attack) {
			handleAttack(attack, ci);
		} else if (packet instanceof ServerboundSwingPacket swing
			&& !"Packet".equals(mode.getValue())
			&& !"None".equals(mode.getValue())) {
			if (skipCrit()) {
				return;
			}
			if (sendPackets && swingPacket == null) {
				swingPacket = swing;
				ci.cancel();
			}
		}
	}

	private void handleAttack(ServerboundAttackPacket attack, CallbackInfo ci) {
		// Dedicated MaceKill owns mace spoofing when it's on.
		if (smashAttack.getValue()
			&& mc().player.getMainHandItem().is(Items.MACE)
			&& !MaceKill.isActive()) {
			if (mc().player.isFallFlying()) {
				return;
			}
			sendPacket(0);
			sendPacket(1.501 + extraHeight.getValue());
			sendPacket(0);
			return;
		}

		if (skipCrit()) {
			return;
		}

		Entity entity = mc().level.getEntity(attack.entityId());
		if (!(entity instanceof LivingEntity)) {
			return;
		}

		if (onlyKillAura.getValue()) {
			KillAura killAura = getKillAura();
			if (killAura == null || entity != killAura.getTarget()) {
				return;
			}
		}

		switch (mode.getValue()) {
			case "Packet" -> {
				sendPacket(0.0625);
				sendPacket(0);
			}
			case "UpdatedNCP" -> {
				sendPacket(0.0000008);
				sendPacket(0);
			}
			case "OldNCP" -> {
				sendPacket(0.11);
				sendPacket(0.1100013579);
				sendPacket(0.0000013579);
			}
			case "Jump", "MiniJump" -> {
				if (!sendPackets) {
					sendPackets = true;
					attackPacket = attack;

					if ("Jump".equals(mode.getValue())) {
						mc().player.jumpFromGround();
						waitingForPeak = true;
						lastY = mc().player.getY();
					} else {
						Vec3 motion = mc().player.getDeltaMovement();
						mc().player.setDeltaMovement(motion.x, 0.25, motion.z);
						sendTimer = 4;
					}
					ci.cancel();
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void onTick() {
		if (!sendPackets || mc().player == null || mc().getConnection() == null) {
			return;
		}

		if ("Jump".equals(mode.getValue()) && waitingForPeak) {
			double currentY = mc().player.getY();
			if (currentY <= lastY) {
				waitingForPeak = false;
				sendTimer = 0;
			}
			lastY = currentY;
			return;
		}

		if (sendTimer <= 0) {
			if (attackPacket == null || swingPacket == null) {
				sendPackets = false;
				return;
			}

			sending = true;
			try {
				mc().getConnection().send(attackPacket);
				mc().getConnection().send(swingPacket);
			} finally {
				sending = false;
			}

			attackPacket = null;
			swingPacket = null;
			sendPackets = false;
		} else {
			sendTimer--;
		}
	}

	private void sendPacket(double height) {
		double x = mc().player.getX();
		double y = mc().player.getY();
		double z = mc().player.getZ();

		sending = true;
		try {
			mc().player.connection.send(new ServerboundMovePlayerPacket.Pos(
				x, y + height, z, false, false
			));
		} finally {
			sending = false;
		}
	}

	private boolean skipCrit() {
		if (isInCobweb() && ("Jump".equals(mode.getValue()) || "MiniJump".equals(mode.getValue()))) {
			return true;
		}
		return !mc().player.onGround()
			|| mc().player.isInWater()
			|| mc().player.isInLava()
			|| mc().player.onClimbable();
	}

	private boolean isInCobweb() {
		if (mc().level == null || mc().player == null) {
			return false;
		}
		return mc().level.getBlockStatesIfLoaded(mc().player.getBoundingBox())
			.anyMatch(state -> state.is(Blocks.COBWEB));
	}

	private static KillAura getKillAura() {
		ModuleManager modules = VirulentClient.getInstance().getModuleManager();
		Module module = modules.getModule("KillAura");
		return module instanceof KillAura killAura ? killAura : null;
	}
}
