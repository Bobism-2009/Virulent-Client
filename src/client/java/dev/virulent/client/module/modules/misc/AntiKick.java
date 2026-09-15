package dev.virulent.client.module.modules.misc;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps the server from kicking us.
 *
 * <p><b>Fly kick.</b> A vanilla server does not simulate our flight, it counts it.
 * Every move packet sets {@code clientIsFloating} when the claimed Y is no more than
 * 0.03125 below the previous claim and we said we were airborne; the play handler then
 * increments {@code aboveGroundTickCount} once per tick while that flag is set and
 * disconnects with "Flying is not enabled on this server" past 80 ticks. The counter is
 * cleared the moment one packet reports a real descent, so the bypass is to make sure
 * such a packet goes out well before the 80-tick deadline.
 *
 * <p>We do that by dipping the position vanilla is about to report rather than by sending
 * an extra packet of our own: {@code sendPosition} is wrapped, Y is lowered by
 * {@link #DIP} for the duration of the call, and the real position is put straight back
 * afterwards. The client never actually moves, the server sees one honest-looking
 * fall tick, and the next regular packet climbs back. Sending our own extra packet
 * instead would not work reliably - it would land in the same server tick as vanilla's,
 * and the later packet's flag is the one the tick loop reads.
 *
 * <p><b>Idle kick.</b> Vanilla's own keep-alive stops the read timeout, but AFK plugins
 * watch for position and rotation that never change. After the configured idle period we
 * send a single rotation packet nudged half a degree off the real yaw, alternating sides
 * so the server's view of us never drifts further than that from the truth.
 */
public final class AntiKick extends Module {
	/**
	 * How far below the last reported Y we claim to be when resetting the float
	 * counter. Vanilla only accepts a descent steeper than 0.03125 as "not floating";
	 * 0.0625 clears that with room to spare and is far inside the 0.25 block gap that
	 * would earn a "moved wrongly" rubber-band.
	 */
	private static final double DIP = 0.0625;
	/** Vanilla's floating threshold: a claim at or above this delta still counts as floating. */
	private static final double FLOAT_THRESHOLD = -0.03125;
	/** Yaw offset of an idle nudge. Large enough to register as a rotation, invisible in game. */
	private static final float IDLE_NUDGE_DEGREES = 0.5f;

	private static AntiKick instance;

	private final BooleanSetting flyKick = addSetting(new BooleanSetting("Fly Kick", true));
	private final NumberSetting flyDelay = addSetting(new NumberSetting("Fly Delay", 40.0, 5.0, 70.0, 1.0));
	private final BooleanSetting idleKick = addSetting(new BooleanSetting("Idle Kick", true));
	private final NumberSetting idleDelay = addSetting(new NumberSetting("Idle Delay", 30.0, 5.0, 300.0, 5.0));

	/** Y of the last packet the server saw, i.e. what it compares the next claim against. */
	private double lastSentY;
	private boolean hasLastSentY;
	private int floatingTicks;

	private boolean dipped;
	private double restoreX;
	private double restoreY;
	private double restoreZ;

	private long idleSinceMs;
	private double idleX;
	private double idleY;
	private double idleZ;
	private float idleYaw;
	private float idlePitch;
	private boolean hasIdleSample;
	private boolean nudgeHigh;

	public AntiKick() {
		super("AntiKick", "Blocks flight and idle kicks by fixing up what we report to the server.",
			Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/**
	 * Called at the head of {@code LocalPlayer.sendPosition}, before vanilla reads our
	 * position to build the move packet.
	 */
	public static void beforeSendPosition() {
		if (isActive()) {
			instance.dipForFlyKick();
		}
	}

	/** Called when {@code LocalPlayer.sendPosition} returns; undoes {@link #beforeSendPosition()}. */
	public static void afterSendPosition() {
		if (instance != null) {
			instance.undoDip();
		}
	}

	@Override
	protected void onEnable() {
		resetFlyState();
		hasIdleSample = false;
		idleSinceMs = System.currentTimeMillis();
	}

	@Override
	protected void onDisable() {
		// The dip is applied and undone inside one sendPosition call, so nothing can be
		// left dipped here; the counters are cleared so a re-enable starts from scratch.
		resetFlyState();
		hasIdleSample = false;
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		if (player == null || mc().getConnection() == null) {
			resetFlyState();
			hasIdleSample = false;
			return;
		}

		if (idleKick.getValue()) {
			tickIdle(player);
		} else {
			hasIdleSample = false;
		}
	}

	private void dipForFlyKick() {
		LocalPlayer player = mc().player;
		ClientLevel level = mc().level;
		if (!flyKick.getValue() || player == null || level == null) {
			resetFlyState();
			return;
		}

		double y = player.getY();
		if (!hasLastSentY) {
			lastSentY = y;
			hasLastSentY = true;
			floatingTicks = 0;
			return;
		}

		if (!isFloating(player, y)) {
			lastSentY = y;
			floatingTicks = 0;
			return;
		}

		floatingTicks++;
		if (floatingTicks < flyDelay.getValue().intValue()) {
			lastSentY = y;
			return;
		}

		// Claiming we are lower than we are also moves us server-side, so only do it
		// through open air. A block in the way would stop the server short of the claim
		// and cost us a rubber-band - and with a block that close vanilla is not counting
		// float ticks anyway, so there is nothing to reset.
		AABB dipBox = player.getBoundingBox().move(0.0, -DIP, 0.0);
		if (!level.noCollision(player, dipBox)) {
			lastSentY = y;
			return;
		}

		restoreX = player.getX();
		restoreY = y;
		restoreZ = player.getZ();
		double dippedY = lastSentY - DIP;
		player.setPosRaw(restoreX, dippedY, restoreZ);
		dipped = true;
		lastSentY = dippedY;
		floatingTicks = 0;
	}

	private void undoDip() {
		if (!dipped) {
			return;
		}
		dipped = false;
		LocalPlayer player = mc().player;
		if (player != null) {
			player.setPosRaw(restoreX, restoreY, restoreZ);
		}
	}

	/**
	 * Mirrors the conditions under which a vanilla server counts a float tick against us.
	 * Anything it would not count is not worth a dip.
	 */
	private boolean isFloating(LocalPlayer player, double y) {
		if (player.onGround() || player.isPassenger() || player.isSleeping() || player.isDeadOrDying()) {
			return false;
		}
		// Server-side flight permission, elytra, levitation and spin attacks are all
		// explicit exemptions in vanilla's check.
		if (player.isCreative() || player.isSpectator() || player.isFallFlying() || player.isAutoSpinAttack()
			|| player.hasEffect(MobEffects.LEVITATION)) {
			return false;
		}
		return y - lastSentY >= FLOAT_THRESHOLD;
	}

	private void tickIdle(LocalPlayer player) {
		long now = System.currentTimeMillis();
		if (!hasIdleSample) {
			sampleIdle(player, now);
			return;
		}

		if (hasMoved(player)) {
			sampleIdle(player, now);
			return;
		}

		if (now - idleSinceMs < (long) (idleDelay.getValue() * 1000.0)) {
			return;
		}

		if (!player.isAlive()) {
			// Nothing to keep alive while dead, and the respawn will move us anyway.
			idleSinceMs = now;
			return;
		}

		float nudged = player.getYRot() + (nudgeHigh ? IDLE_NUDGE_DEGREES : -IDLE_NUDGE_DEGREES);
		nudgeHigh = !nudgeHigh;
		player.connection.send(new ServerboundMovePlayerPacket.Rot(
			nudged, player.getXRot(), player.onGround(), player.horizontalCollision
		));
		sampleIdle(player, now);
	}

	private boolean hasMoved(LocalPlayer player) {
		return Math.abs(player.getX() - idleX) > 1.0E-4
			|| Math.abs(player.getY() - idleY) > 1.0E-4
			|| Math.abs(player.getZ() - idleZ) > 1.0E-4
			|| player.getYRot() != idleYaw
			|| player.getXRot() != idlePitch;
	}

	private void sampleIdle(LocalPlayer player, long now) {
		idleX = player.getX();
		idleY = player.getY();
		idleZ = player.getZ();
		idleYaw = player.getYRot();
		idlePitch = player.getXRot();
		idleSinceMs = now;
		hasIdleSample = true;
	}

	private void resetFlyState() {
		hasLastSentY = false;
		floatingTicks = 0;
		dipped = false;
	}
}
