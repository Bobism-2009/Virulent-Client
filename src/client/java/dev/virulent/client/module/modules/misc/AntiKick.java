package dev.virulent.client.module.modules.misc;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
 * <p>Riding is the same kick with a different counter: a passenger sends
 * {@code ServerboundMoveVehiclePacket} instead, and the server floats the <i>vehicle</i>
 * against {@code aboveGroundVehicleTickCount}, which is what ends a BoatFly session.
 * There we rewrite the outgoing packet directly - the boat itself is never touched.
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
	/**
	 * Ticks of float before we dip. Vanilla kicks past 80, so 20 leaves four attempts
	 * inside the deadline: if the server happens to read a later packet in the same tick
	 * as one dip - two move packets can land in one server tick whenever the two clocks
	 * drift or the connection stutters - the next dip a second later still resets it.
	 */
	private final NumberSetting flyDelay = addSetting(new NumberSetting("Fly Delay", 20.0, 5.0, 70.0, 1.0));
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

	private Entity lastVehicle;
	private double vehicleLastSentY;
	private int vehicleFloatingTicks;

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

	/**
	 * Called with the vehicle move packet a passenger is about to send, and returns the
	 * packet that should actually go out. Rewriting the packet is enough here - unlike
	 * the player's own move packet, this one is built from the vehicle every tick
	 * regardless of whether it moved, so there is nothing to force.
	 */
	public static ServerboundMoveVehiclePacket onVehiclePacket(ServerboundMoveVehiclePacket packet) {
		if (!isActive()) {
			return packet;
		}
		return instance.dipVehicleForFlyKick(packet);
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
		// and cost us a rubber-band - and with a block that close vanilla's noBlocksAround
		// means it is not counting float ticks at all, so there is nothing to reset.
		AABB dipBox = player.getBoundingBox().move(0.0, -DIP, 0.0);
		if (!level.noCollision(player, dipBox)) {
			lastSentY = y;
			floatingTicks = 0;
			return;
		}

		restoreX = player.getX();
		restoreY = y;
		restoreZ = player.getZ();
		// Below both the real position and the last Y the server saw, so the claim is a
		// descent whichever of the two it ends up being compared against.
		double dippedY = Math.min(y, lastSentY) - DIP;
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
	 *
	 * <p>Ground is judged by {@code verticalCollisionBelow} and not by {@code onGround()}
	 * on purpose. It is what the server's own float check reads, and - the reason this
	 * whole module did nothing for anyone flying with NoFall on - {@code onGround()} is
	 * spoofed to true for the local player for the whole of {@code sendPosition}, which is
	 * exactly when we run. Asking for it here answered NoFall's lie, not the world.
	 */
	private boolean isFloating(LocalPlayer player, double y) {
		if (player.verticalCollisionBelow || player.isPassenger() || player.isSleeping()
			|| player.isDeadOrDying()) {
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

	/**
	 * The vehicle half of the fly kick. The server floats the vehicle on the same
	 * -0.03125 rule, against {@code vehicleLastGoodY} and its own counter, so the same
	 * dip clears it. The exemptions differ: gravity and vehicle type are what matter, and
	 * we deliberately do not read {@code isNoGravity()} - BoatFly sets that on the client
	 * only, so the server's boat still falls and still counts.
	 */
	private ServerboundMoveVehiclePacket dipVehicleForFlyKick(ServerboundMoveVehiclePacket packet) {
		LocalPlayer player = mc().player;
		ClientLevel level = mc().level;
		Entity vehicle = player == null ? null : player.getRootVehicle();
		if (!flyKick.getValue() || level == null || vehicle == null || vehicle == player) {
			resetVehicleState();
			return packet;
		}

		double y = packet.position().y;
		if (vehicle != lastVehicle) {
			lastVehicle = vehicle;
			vehicleLastSentY = y;
			vehicleFloatingTicks = 0;
			return packet;
		}

		if (vehicle.verticalCollisionBelow || vehicle.isFlyingVehicle() || packet.onGround()
			|| y - vehicleLastSentY < FLOAT_THRESHOLD) {
			vehicleLastSentY = y;
			vehicleFloatingTicks = 0;
			return packet;
		}

		vehicleFloatingTicks++;
		if (vehicleFloatingTicks < flyDelay.getValue().intValue()) {
			vehicleLastSentY = y;
			return packet;
		}

		AABB dipBox = vehicle.getBoundingBox().move(0.0, -DIP, 0.0);
		if (!level.noCollision(vehicle, dipBox)) {
			vehicleLastSentY = y;
			vehicleFloatingTicks = 0;
			return packet;
		}

		double dippedY = Math.min(y, vehicleLastSentY) - DIP;
		vehicleLastSentY = dippedY;
		vehicleFloatingTicks = 0;
		Vec3 position = packet.position();
		return new ServerboundMoveVehiclePacket(
			new Vec3(position.x, dippedY, position.z), packet.yRot(), packet.xRot(), packet.onGround()
		);
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
		resetVehicleState();
	}

	private void resetVehicleState() {
		lastVehicle = null;
		vehicleFloatingTicks = 0;
	}
}
