package dev.virulent.client.module.modules.combat;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Spoofs a tall fall before mace attacks so smash damage applies.
 * Inspired by TrouserStreak / Meteor MaceKill settings.
 *
 * Underground Mode: instead of clipping through the ceiling, a climb route is
 * pathfound through connected air spaces (caves, shafts, tunnels) around the
 * player. The route is then flown server-side with staged move packets capped
 * at TP Speed (max 200 blocks/second, i.e. 10 blocks per tick), each waypoint
 * collision-free, so strict movement checks accept every step. The server sums
 * every downward delta into fall distance while airborne, so each ascend +
 * descend round trip along the route banks its full vertical variation as fall
 * distance; cycles repeat until the smash threshold is reached, which makes
 * this work from any depth. While a route is in flight the client's own move
 * packets are held back so they cannot fight the spoofed positions.
 */
public final class MaceKill extends Module {
	private static MaceKill instance;

	private final BooleanSetting swingArm = addSetting(new BooleanSetting("Swing Arm", true));
	private final BooleanSetting disableWhenBlocked = addSetting(new BooleanSetting("Disable When Blocked", true));
	private final NumberSetting fallHeight = addSetting(new NumberSetting("Fall Height", 30.0, 1.0, 169.0, 1.0));
	private final NumberSetting spamPackets = addSetting(new NumberSetting("Spam Packets", 3.0, 1.0, 17.0, 1.0));
	private final BooleanSetting useOffset = addSetting(new BooleanSetting("Use Offset", true));
	private final NumberSetting horizontalOffset = addSetting(new NumberSetting("Horizontal Offset", 0.05, 0.0, 0.99, 0.01));
	private final NumberSetting yOffset = addSetting(new NumberSetting("Y Offset", 0.01, 0.0, 0.99, 0.01));
	private final BooleanSetting bypassTotems = addSetting(new BooleanSetting("Bypass Totems", false));
	private final NumberSetting attacks = addSetting(new NumberSetting("Attacks", 3.0, 1.0, 3.0, 1.0));
	private final NumberSetting heightIncrease = addSetting(new NumberSetting("Height Increase", 20.0, 1.0, 100.0, 1.0));
	private final BooleanSetting undergroundMode = addSetting(new BooleanSetting("Underground Mode", true));
	private final NumberSetting tpSpeed = addSetting(new NumberSetting("TP Speed", 200.0, 20.0, 200.0, 10.0));
	private final NumberSetting searchRange = addSetting(new NumberSetting("Search Range", 16.0, 4.0, 32.0, 1.0));
	private final NumberSetting maxHops = addSetting(new NumberSetting("Max Hops", 20.0, 1.0, 50.0, 1.0));
	private final BooleanSetting chatFeedback = addSetting(new BooleanSetting("Chat Feedback", true));

	/** Smash attacks require fall distance > 1.5; keep a small margin. */
	private static final double SMASH_THRESHOLD = 1.6;
	/** Smallest climb worth flying; below this no fall can accumulate. */
	private static final double MIN_HOP = 0.1;
	/** Headroom scan resolution in blocks. */
	private static final double CLEARANCE_STEP = 0.25;
	/** Pathfinder node budget per route computation. */
	private static final int MAX_SEARCH_NODES = 4096;
	/** Longest route (in waypoints) worth flying per cycle. */
	private static final int MAX_ROUTE_KNOTS = 512;
	/** Abort a route that has been in flight longer than this (30s). */
	private static final int TIMEOUT_TICKS = 600;
	/** Abort if the real player wanders this far from the route home. */
	private static final double MAX_HOME_DRIFT = 2.0;
	/**
	 * Vanilla kicks after ~80 consecutive non-falling ticks ("Flying is not
	 * enabled"); descending resets the counter, so only the ascend leg counts.
	 * Keep each leg comfortably under that many ticks at the configured speed.
	 */
	private static final int MAX_ASCENT_TICKS = 70;

	private enum Phase {
		IDLE, ASCEND, DESCEND
	}

	private Phase phase = Phase.IDLE;
	private final List<Vec3> route = new ArrayList<>();
	private Vec3 home = Vec3.ZERO;
	private Vec3 routePos = Vec3.ZERO;
	private int nextIndex;
	private int targetId;
	private double accumulatedFall;
	private double lastAttackFall;
	private int cyclesDone;
	private int totalCycles;
	private int attackIndex;
	private int attackCount;
	private int ticksActive;
	private boolean aborting;
	private String abortReason;

	private boolean sendingAttacks;
	private boolean sendingRouteMove;
	private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
	private final Map<Vec3, Boolean> positionCache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Vec3, Boolean> eldest) {
			return size() > 256;
		}
	};

	public MaceKill() {
		super(
			"MaceKill",
			"Makes the Mace powerful when swung, even deep underground. Can also bypass totem usage.",
			Category.COMBAT,
			GLFW.GLFW_KEY_UNKNOWN
		);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/**
	 * Called from {@link dev.virulent.client.mixin.ConnectionMixin} before attack packets leave.
	 */
	public static void onAttackPacket(ServerboundAttackPacket packet, CallbackInfo ci) {
		if (!isActive() || instance.sendingAttacks) {
			return;
		}
		instance.handleAttack(packet, ci);
	}

	/**
	 * Called from {@link dev.virulent.client.mixin.ConnectionMixin} before move packets leave.
	 * While a spoof route is in flight the client's own movement packets are held
	 * back so they cannot yank the server position off the route mid-flight.
	 */
	public static void onMovePacket(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (!isActive() || instance.phase == Phase.IDLE || instance.sendingRouteMove) {
			return;
		}
		ci.cancel();
	}

	@Override
	protected void onDisable() {
		if (phase != Phase.IDLE && mc().player != null && mc().getConnection() != null) {
			sendMove(home);
		}
		resetRoute();
	}

	@Override
	public void onTick() {
		if (phase == Phase.IDLE) {
			return;
		}
		if (mc().player == null || mc().level == null || mc().getConnection() == null) {
			resetRoute();
			return;
		}

		ticksActive++;
		if (!aborting) {
			if (ticksActive > TIMEOUT_TICKS) {
				abortRoute("timed out");
			} else if (mc().player.position().distanceTo(home) > MAX_HOME_DRIFT) {
				abortRoute("player moved");
			} else {
				Entity entity = mc().level.getEntity(targetId);
				if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
					abortRoute("target lost");
				}
			}
		}

		double budget = tpSpeed.getValue() / 20.0;
		while (budget > 1.0E-4 && phase != Phase.IDLE) {
			Vec3 next = route.get(nextIndex);
			double dist = routePos.distanceTo(next);
			if (dist > budget) {
				Vec3 partial = routePos.add(next.subtract(routePos).scale(budget / dist));
				trackFall(partial);
				sendMove(partial);
				routePos = partial;
				return;
			}

			budget -= dist;
			trackFall(next);
			sendMove(next);
			routePos = next;

			if (phase == Phase.ASCEND) {
				if (nextIndex >= route.size() - 1) {
					phase = Phase.DESCEND;
					nextIndex = route.size() - 2;
				} else {
					nextIndex++;
				}
			} else {
				if (nextIndex <= 0) {
					onCycleComplete();
				} else {
					nextIndex--;
				}
			}
		}
	}

	private void trackFall(Vec3 to) {
		if (to.y < routePos.y) {
			accumulatedFall += routePos.y - to.y;
		}
	}

	private void onCycleComplete() {
		cyclesDone++;
		totalCycles++;

		if (aborting) {
			finishRoute(false);
			return;
		}

		double target = currentTargetFall();
		if (accumulatedFall < target && cyclesDone < maxHops.getValue().intValue()) {
			phase = Phase.ASCEND;
			nextIndex = 1;
			return;
		}

		if (accumulatedFall < SMASH_THRESHOLD) {
			abortReason = "could not reach smash threshold (raise Max Hops)";
			finishRoute(false);
			return;
		}

		if (!performAttack()) {
			return;
		}
		attackIndex++;
		if (attackIndex >= attackCount) {
			finishRoute(true);
			return;
		}
		accumulatedFall = 0;
		cyclesDone = 0;
		phase = Phase.ASCEND;
		nextIndex = 1;
	}

	private double currentTargetFall() {
		return fallHeight.getValue() + attackIndex * heightIncrease.getValue().intValue();
	}

	private boolean performAttack() {
		Entity entity = mc().level.getEntity(targetId);
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			abortReason = "target lost";
			finishRoute(false);
			return false;
		}

		lastAttackFall = accumulatedFall;
		sendingRouteMove = true;
		try {
			for (int i = 0; i < spamPackets.getValue().intValue(); i++) {
				mc().player.connection.send(new ServerboundMovePlayerPacket.Rot(
					mc().player.getYRot(),
					mc().player.getXRot(),
					false,
					mc().player.horizontalCollision
				));
			}
		} finally {
			sendingRouteMove = false;
		}

		sendingAttacks = true;
		try {
			if (swingArm.getValue()) {
				mc().player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
				mc().player.swing(InteractionHand.MAIN_HAND);
			}
			mc().player.connection.send(new ServerboundAttackPacket(target.getId()));
		} finally {
			sendingAttacks = false;
		}
		return true;
	}

	private void abortRoute(String reason) {
		if (aborting) {
			return;
		}
		aborting = true;
		abortReason = reason;
		// Turn around and fly the route back home before letting go.
		if (phase == Phase.ASCEND) {
			phase = Phase.DESCEND;
			nextIndex = Math.max(0, nextIndex - 1);
		}
	}

	private void finishRoute(boolean success) {
		if (success && useOffset.getValue()) {
			Vec3 offsetHome = offsetHome(home);
			sendMove(offsetHome);
			mc().player.setPos(offsetHome);
		}

		if (chatFeedback.getValue()) {
			if (success) {
				feedback("MaceKill x" + attackCount + " @ " + String.format("%.1f", lastAttackFall)
					+ " fall (" + totalCycles + " cycle" + (totalCycles == 1 ? "" : "s")
					+ ", <=" + tpSpeed.getValue().intValue() + " b/s)");
			} else if (abortReason != null) {
				feedback("MaceKill cancelled: " + abortReason);
			}
		}

		positionCache.clear();
		resetRoute();
	}

	private void resetRoute() {
		phase = Phase.IDLE;
		route.clear();
		nextIndex = 0;
		accumulatedFall = 0;
		cyclesDone = 0;
		totalCycles = 0;
		attackIndex = 0;
		attackCount = 0;
		ticksActive = 0;
		aborting = false;
		abortReason = null;
	}

	private void handleAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
		if (mc().player == null || mc().level == null || mc().getConnection() == null) {
			return;
		}
		if (phase != Phase.IDLE) {
			// A spoof route is already in flight; swallow stray attacks so a
			// no-fall hit does not waste the attack cooldown mid-route.
			ci.cancel();
			return;
		}
		if (mc().player.isPassenger() || !mc().player.getMainHandItem().is(Items.MACE)) {
			return;
		}

		Entity entity = mc().level.getEntity(packet.entityId());
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		if (disableWhenBlocked.getValue() && (target.isBlocking() || target.isInvulnerable())) {
			return;
		}

		double desiredFall = fallHeight.getValue();
		int plannedAttacks = bypassTotems.getValue() ? attacks.getValue().intValue() : 1;
		double maxNeeded = desiredFall + (plannedAttacks - 1) * heightIncrease.getValue().intValue();

		Vec3 start = mc().player.position();
		List<Vec3> newRoute = buildRoute(start, maxNeeded);
		if (newRoute == null || newRoute.size() < 2) {
			feedback("No climb route found - fully sealed in with zero headroom.");
			return;
		}

		double fallPerCycle = 0;
		for (int i = 1; i < newRoute.size(); i++) {
			fallPerCycle += Math.abs(newRoute.get(i).y - newRoute.get(i - 1).y);
		}
		if (fallPerCycle < MIN_HOP) {
			feedback("No climb route found - fully sealed in with zero headroom.");
			return;
		}
		if (fallPerCycle * maxHops.getValue().intValue() < SMASH_THRESHOLD) {
			feedback("Route too shallow to reach smash threshold (raise Max Hops).");
			return;
		}

		ci.cancel();

		route.clear();
		route.addAll(newRoute);
		home = start;
		routePos = start;
		nextIndex = 1;
		targetId = target.getId();
		accumulatedFall = 0;
		lastAttackFall = 0;
		cyclesDone = 0;
		totalCycles = 0;
		attackIndex = 0;
		attackCount = plannedAttacks;
		ticksActive = 0;
		aborting = false;
		abortReason = null;
		phase = Phase.ASCEND;

		if (chatFeedback.getValue()) {
			int estCycles = (int) Math.ceil(desiredFall / fallPerCycle);
			double routeLength = 0;
			for (int i = 1; i < route.size(); i++) {
				routeLength += route.get(i).distanceTo(route.get(i - 1));
			}
			double estSeconds = 2.0 * routeLength * estCycles / tpSpeed.getValue();
			feedback("Route: " + String.format("%.1f", fallPerCycle) + "b fall/cycle, ~" + estCycles
				+ " cycle" + (estCycles == 1 ? "" : "s") + " @ <=" + tpSpeed.getValue().intValue()
				+ " b/s (~" + String.format("%.1f", estSeconds) + "s)");
		}
	}

	/**
	 * Builds the per-cycle flight route starting and ending at {@code home}.
	 * Open sky (or Underground Mode off) uses a straight vertical column; when
	 * confined, a climb route is pathfound through nearby connected air spaces.
	 * Falls back to an in-place column inside the available headroom when the
	 * player is completely sealed in, so any depth still works via cycles.
	 */
	private List<Vec3> buildRoute(Vec3 home, double neededFall) {
		double clearance = clearanceAbove(neededFall);
		if (clearance >= neededFall || !undergroundMode.getValue()) {
			return columnRoute(home, neededFall);
		}

		List<Vec3> path = findClimbRoute(home, neededFall);
		double pathFall = 0;
		if (path != null) {
			for (int i = 1; i < path.size(); i++) {
				pathFall += Math.abs(path.get(i).y - path.get(i - 1).y);
			}
		}
		// Prefer the pathfound route only when it beats plain in-place hops.
		if (path != null && pathFall > clearance + 0.5) {
			return path;
		}
		if (clearance >= MIN_HOP) {
			return columnRoute(home, clearance);
		}
		return path;
	}

	private List<Vec3> columnRoute(Vec3 home, double height) {
		List<Vec3> column = new ArrayList<>(2);
		column.add(home);
		column.add(home.add(0, Math.min(height, ascentCap()), 0));
		return column;
	}

	/** Longest one-way leg (in blocks) that stays under the flying-kick window. */
	private double ascentCap() {
		return tpSpeed.getValue() / 20.0 * MAX_ASCENT_TICKS;
	}

	/**
	 * Best-first search through passable air blocks around the player,
	 * maximizing vertical variation (every up AND down block along the path
	 * banks one block of fall per cycle). Stops early once a single cycle
	 * covers {@code neededFall}.
	 */
	private List<Vec3> findClimbRoute(Vec3 homePos, double neededFall) {
		if (mc().level == null || mc().player == null) {
			return null;
		}

		BlockPos start = BlockPos.containing(homePos);
		if (!passable(start)) {
			start = start.above();
			if (!passable(start)) {
				return null;
			}
		}

		int radius = searchRange.getValue().intValue();
		int minY = Math.max(mc().level.getMinY(), start.getY() - (int) Math.ceil(neededFall) - 8);
		int maxY = start.getY() + (int) Math.ceil(neededFall) + 8;
		int maxKnots = Math.min(MAX_ROUTE_KNOTS, (int) ascentCap());

		PriorityQueue<ClimbNode> open = new PriorityQueue<>(
			Comparator.comparingDouble((ClimbNode n) -> -(n.variation - n.steps * 0.05))
		);
		Set<BlockPos> visited = new HashSet<>();
		ClimbNode startNode = new ClimbNode(start, null, 0, 0);
		open.add(startNode);
		visited.add(start);

		ClimbNode best = startNode;
		int explored = 0;
		while (!open.isEmpty() && explored < MAX_SEARCH_NODES) {
			ClimbNode node = open.poll();
			explored++;
			if (node.variation > best.variation) {
				best = node;
			}
			if (node.variation >= neededFall) {
				break;
			}
			if (node.steps >= maxKnots - 2) {
				continue;
			}

			for (Direction direction : Direction.values()) {
				BlockPos next = node.pos.relative(direction);
				if (!visited.add(next)) {
					continue;
				}
				if (Math.abs(next.getX() - start.getX()) > radius
					|| Math.abs(next.getZ() - start.getZ()) > radius
					|| next.getY() < minY || next.getY() > maxY) {
					continue;
				}
				if (!passable(next)) {
					continue;
				}
				open.add(new ClimbNode(next, node, node.variation + Math.abs(direction.getStepY()), node.steps + 1));
			}
		}

		if (best.variation < MIN_HOP) {
			return null;
		}

		List<BlockPos> blocks = new ArrayList<>();
		for (ClimbNode node = best; node != null; node = node.parent) {
			blocks.add(node.pos);
		}
		Collections.reverse(blocks);

		// Truncate once one cycle over the prefix already covers the target.
		List<Vec3> knots = new ArrayList<>();
		knots.add(homePos);
		double variation = 0;
		for (int i = 1; i < blocks.size(); i++) {
			BlockPos pos = blocks.get(i);
			knots.add(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
			variation += Math.abs(pos.getY() - blocks.get(i - 1).getY());
			if (variation >= neededFall) {
				break;
			}
		}
		if (knots.size() < 2) {
			return null;
		}
		return knots;
	}

	/**
	 * A player-sized bounding box must fit at the block center, with no fluids
	 * (water resets fall distance) or harmful blocks at feet or head height.
	 */
	private boolean passable(BlockPos pos) {
		if (mc().level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
			return false;
		}
		for (int dy = 0; dy <= 1; dy++) {
			mutablePos.set(pos.getX(), pos.getY() + dy, pos.getZ());
			if (!mc().level.getFluidState(mutablePos).isEmpty()) {
				return false;
			}
			BlockState state = mc().level.getBlockState(mutablePos);
			if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.COBWEB) || state.is(Blocks.SWEET_BERRY_BUSH)
				|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
				return false;
			}
		}
		double cx = pos.getX() + 0.5;
		double cz = pos.getZ() + 0.5;
		AABB box = new AABB(cx - 0.3, pos.getY(), cz - 0.3, cx + 0.3, pos.getY() + 1.8, cz + 0.3);
		return !mc().level.getBlockCollisions(mc().player, box).iterator().hasNext();
	}

	private void sendMove(Vec3 pos) {
		if (mc().getConnection() == null || mc().player == null) {
			return;
		}
		sendingRouteMove = true;
		try {
			mc().player.connection.send(new ServerboundMovePlayerPacket.PosRot(
				pos,
				mc().player.getYRot(),
				mc().player.getXRot(),
				false,
				mc().player.horizontalCollision
			));
		} finally {
			sendingRouteMove = false;
		}
	}

	private Vec3 offsetHome(Vec3 base) {
		double dx = horizontalOffset.getValue();
		double dy = yOffset.getValue();
		List<Vec3> offsets = new ArrayList<>(List.of(
			base.add(dx, dy, 0),
			base.add(-dx, dy, 0),
			base.add(0, dy, dx),
			base.add(0, dy, -dx),
			base.add(dx, dy, dx),
			base.add(-dx, dy, -dx),
			base.add(-dx, dy, dx),
			base.add(dx, dy, -dx)
		));
		Collections.shuffle(offsets);
		for (Vec3 pos : offsets) {
			if (!invalid(pos)) {
				return pos;
			}
		}
		Vec3 noHorizontal = base.add(0, dy, 0);
		if (!invalid(noHorizontal)) {
			return noHorizontal;
		}
		return base;
	}

	private boolean invalid(Vec3 pos) {
		if (mc().level == null || mc().player == null) {
			return true;
		}

		BlockPos floored = BlockPos.containing(pos);
		int chunkX = floored.getX() >> 4;
		int chunkZ = floored.getZ() >> 4;
		if (mc().level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
			return true;
		}

		Boolean cached = positionCache.get(pos);
		if (cached != null) {
			return cached;
		}

		Vec3 delta = pos.subtract(mc().player.position());
		AABB box = mc().player.getBoundingBox().move(delta);

		mutablePos.set(floored);
		for (int x = -1; x <= 1; x++) {
			mutablePos.setX(floored.getX() + x);
			for (int y = -1; y <= 1; y++) {
				mutablePos.setY(floored.getY() + y);
				for (int z = -1; z <= 1; z++) {
					mutablePos.setZ(floored.getZ() + z);
					BlockState state = mc().level.getBlockState(mutablePos);
					if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
						|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
						|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)) {
						positionCache.put(pos, true);
						return true;
					}
				}
			}
		}

		for (Entity other : mc().level.getEntities(mc().player, box)) {
			if (other.canBeCollidedWith(mc().player)) {
				positionCache.put(pos, true);
				return true;
			}
		}

		boolean collides = mc().level.getBlockCollisions(mc().player, box).iterator().hasNext();
		positionCache.put(pos, collides);
		return collides;
	}

	/**
	 * Largest collision-free vertical offset (in blocks, up to {@code maxCheck})
	 * the player's bounding box can be moved upward without clipping a block.
	 */
	private double clearanceAbove(double maxCheck) {
		if (mc().level == null || mc().player == null) {
			return 0;
		}
		AABB base = mc().player.getBoundingBox();
		double clear = 0;
		while (clear + CLEARANCE_STEP <= maxCheck) {
			AABB test = base.move(0, clear + CLEARANCE_STEP, 0);
			if (mc().level.getBlockCollisions(mc().player, test).iterator().hasNext()) {
				break;
			}
			clear += CLEARANCE_STEP;
		}
		return clear;
	}

	private void feedback(String text) {
		if (mc().player != null) {
			mc().player.sendSystemMessage(Component.literal("[Virulent] " + text));
		}
	}

	private static final class ClimbNode {
		private final BlockPos pos;
		private final ClimbNode parent;
		private final double variation;
		private final int steps;

		private ClimbNode(BlockPos pos, ClimbNode parent, double variation, int steps) {
			this.pos = pos;
			this.parent = parent;
			this.variation = variation;
			this.steps = steps;
		}
	}
}
