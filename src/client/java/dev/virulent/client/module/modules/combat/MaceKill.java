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
 * Underground Mode: fall distance is banked with an in-place vertical column
 * flown server-side with staged move packets capped at TP Speed (max 200
 * blocks/second, i.e. 10 blocks per tick) AND at a per-tick packet budget
 * that stays under server packet-rate limiters, each waypoint collision-free,
 * so strict movement checks accept every step. The server sums every downward
 * delta into fall distance while airborne, so each ascend + descend round trip
 * banks the column height; cycles repeat until the target fall is reached. A
 * column banks fall at the maximum possible rate (half of TP Speed) no matter
 * how short it is - extra cycles cost nothing - so ANY measurable headroom
 * works: open sky, a roofed base, or the ~0.2 blocks of air above the head in
 * a 2-block mining tunnel all use the column directly with zero pathfinding.
 * Only when headroom is essentially zero (under a slab or trapdoor) is a climb
 * route pathfound through connected air spaces as a last resort, smoothed into
 * line-of-sight shortcuts (split at vertical extremes so no banked fall is
 * lost). Routes abort early if the target leaves attack reach of the spoofed
 * home position. While a route is in flight the client's own move packets are
 * held back so they cannot fight the spoofed positions.
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
	/** Fine headroom scan resolution; catches sub-block gaps under ceilings. */
	private static final double FINE_CLEARANCE_STEP = 0.05;
	/** Safety gap kept below the ceiling so the head never exactly touches. */
	private static final double CLEARANCE_MARGIN = 0.02;
	/**
	 * Move packets per tick ceiling. Paper's default packet limiter kicks any
	 * client averaging over 500 packets/second ("sent too many packets!"), and
	 * sub-block columns hit this ceiling every tick, so it has to leave room
	 * for the client's normal traffic: 8 moves/tick = 160/s, under a third of
	 * the default limit.
	 */
	private static final int MAX_MOVES_PER_TICK = 8;
	/** Pathfinder node budget per route computation. */
	private static final int MAX_SEARCH_NODES = 4096;
	/** Longest route (in waypoints) worth flying per cycle. */
	private static final int MAX_ROUTE_KNOTS = 512;
	/** Abort a route that has been in flight longer than this (30s). */
	private static final int TIMEOUT_TICKS = 600;
	/** Abort if the real player wanders this far from the route home. */
	private static final double MAX_HOME_DRIFT = 2.0;
	/** Server-side entity interaction limit; attacks from farther are dropped. */
	private static final double MAX_ATTACK_REACH = 6.0;
	/** Farthest waypoint a smoothing shortcut may jump to in one step. */
	private static final int SMOOTH_WINDOW = 32;
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
	private double fallPerCycle;
	private boolean columnMode;
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
				} else if (target.position().distanceTo(home) > MAX_ATTACK_REACH) {
					// Server validates attack reach against our spoofed (home)
					// position; keep flying only while the hit can still land.
					abortRoute("target out of reach");
				}
			}
		}

		double budget = tpSpeed.getValue() / 20.0;
		int movesSent = 0;
		while (budget > 1.0E-4 && movesSent < MAX_MOVES_PER_TICK && phase != Phase.IDLE) {
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
			movesSent++;
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
		if (accumulatedFall < target && cyclesDone < cycleCap(target)) {
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

	/**
	 * Column routes bank a fixed amount per cycle at no extra cost, so they run
	 * exactly as many cycles as the target fall needs (Max Hops does not apply);
	 * pathfound routes keep the user-set Max Hops budget.
	 */
	private int cycleCap(double targetFall) {
		if (columnMode) {
			return (int) Math.ceil(targetFall / Math.max(fallPerCycle, 1.0E-4)) + 2;
		}
		return maxHops.getValue().intValue();
	}

	private boolean performAttack() {
		Entity entity = mc().level.getEntity(targetId);
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			abortReason = "target lost";
			finishRoute(false);
			return false;
		}
		if (target.position().distanceTo(home) > MAX_ATTACK_REACH) {
			abortReason = "target out of reach";
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
		fallPerCycle = 0;
		columnMode = false;
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

		double newFallPerCycle = 0;
		for (int i = 1; i < newRoute.size(); i++) {
			newFallPerCycle += Math.abs(newRoute.get(i).y - newRoute.get(i - 1).y);
		}
		boolean column = newRoute.size() == 2
			&& newRoute.get(0).x == newRoute.get(1).x
			&& newRoute.get(0).z == newRoute.get(1).z;
		if (newFallPerCycle < MIN_HOP) {
			feedback("No climb route found - fully sealed in with zero headroom.");
			return;
		}
		if (!column && newFallPerCycle * maxHops.getValue().intValue() < SMASH_THRESHOLD) {
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
		fallPerCycle = newFallPerCycle;
		columnMode = column;
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
			double estSeconds = Math.max(
				2.0 * routeLength * estCycles / tpSpeed.getValue(),
				2.0 * (route.size() - 1) * estCycles / (MAX_MOVES_PER_TICK * 20.0));
			feedback("Route: " + String.format("%.1f", fallPerCycle) + "b fall/cycle, ~" + estCycles
				+ " cycle" + (estCycles == 1 ? "" : "s") + " @ <=" + tpSpeed.getValue().intValue()
				+ " b/s (~" + String.format("%.1f", estSeconds) + "s)");
		}
	}

	/**
	 * Builds the per-cycle flight route starting and ending at {@code home}.
	 * A vertical in-place column banks fall distance at the maximum possible
	 * rate (half of TP Speed) no matter how short it is - a pathfound route can
	 * never beat it, since fall banked per cycle cannot exceed route length. So
	 * any measurable headroom uses the column directly with zero pathfinding:
	 * open sky, a roofed base, or the sub-block gap in a 2-tall tunnel. The
	 * climb-route pathfinder only remains as a last resort for spots with
	 * essentially zero headroom (under a slab or trapdoor) that still connect
	 * to nearby air.
	 */
	private List<Vec3> buildRoute(Vec3 home, double neededFall) {
		if (!undergroundMode.getValue()) {
			return columnRoute(home, neededFall);
		}
		double clearance = clearanceAbove(neededFall);
		if (clearance >= neededFall) {
			return columnRoute(home, neededFall);
		}
		double usable = clearance - CLEARANCE_MARGIN;
		if (usable >= MIN_HOP) {
			return columnRoute(home, usable);
		}
		List<Vec3> path = findClimbRoute(home, neededFall);
		return path == null ? null : smoothRoute(path);
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
	 * Replaces block-by-block staircase runs with straight line-of-sight
	 * shortcuts. The route is split at every vertical direction change so each
	 * shortcut spans a y-monotonic run only - a straight segment then has
	 * exactly the same vertical variation as the staircase it replaces, and no
	 * banked fall distance is lost. Shorter routes mean faster cycles and one
	 * move packet per tick on straight stretches instead of one per block.
	 */
	private List<Vec3> smoothRoute(List<Vec3> raw) {
		if (raw.size() < 3) {
			return raw;
		}
		List<Vec3> smoothed = new ArrayList<>(raw.size());
		smoothed.add(raw.get(0));
		int i = 0;
		while (i < raw.size() - 1) {
			// Extend the run while the y-direction does not flip.
			int runEnd = i + 1;
			int dir = (int) Math.signum(raw.get(runEnd).y - raw.get(i).y);
			while (runEnd + 1 < raw.size()) {
				int nextDir = (int) Math.signum(raw.get(runEnd + 1).y - raw.get(runEnd).y);
				if (dir != 0 && nextDir != 0 && nextDir != dir) {
					break;
				}
				if (dir == 0) {
					dir = nextDir;
				}
				runEnd++;
			}
			// Greedy farthest-visible shortcuts within the run.
			int from = i;
			while (from < runEnd) {
				int to = Math.min(runEnd, from + SMOOTH_WINDOW);
				while (to > from + 1 && !clearPath(raw.get(from), raw.get(to))) {
					to--;
				}
				smoothed.add(raw.get(to));
				from = to;
			}
			i = runEnd;
		}
		return smoothed;
	}

	/**
	 * True when a player-sized box can slide from {@code a} to {@code b} in a
	 * straight line without clipping blocks or entering fluids, sampled every
	 * quarter block.
	 */
	private boolean clearPath(Vec3 a, Vec3 b) {
		double dist = a.distanceTo(b);
		int samples = Math.max(1, (int) Math.ceil(dist / CLEARANCE_STEP));
		Vec3 step = b.subtract(a);
		for (int s = 1; s <= samples; s++) {
			Vec3 p = a.add(step.scale((double) s / samples));
			BlockPos feet = BlockPos.containing(p);
			if (!mc().level.getFluidState(feet).isEmpty()
				|| !mc().level.getFluidState(feet.above()).isEmpty()) {
				return false;
			}
			AABB box = new AABB(p.x - 0.3, p.y, p.z - 0.3, p.x + 0.3, p.y + 1.8, p.z + 0.3);
			if (mc().level.getBlockCollisions(mc().player, box).iterator().hasNext()) {
				return false;
			}
		}
		return true;
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
		while (clear + CLEARANCE_STEP <= maxCheck && clearAt(base, clear + CLEARANCE_STEP)) {
			clear += CLEARANCE_STEP;
		}
		// Refine the blocked quarter block at fine resolution so sub-block
		// headroom still registers - a 2-block tunnel leaves ~0.2 above the
		// head, which the coarse scan alone rounds down to zero.
		while (clear + FINE_CLEARANCE_STEP <= maxCheck && clearAt(base, clear + FINE_CLEARANCE_STEP)) {
			clear += FINE_CLEARANCE_STEP;
		}
		return clear;
	}

	private boolean clearAt(AABB base, double up) {
		return !mc().level.getBlockCollisions(mc().player, base.move(0, up, 0)).iterator().hasNext();
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
