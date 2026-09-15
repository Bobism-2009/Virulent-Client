package dev.virulent.client.util;

import java.util.Arrays;

/**
 * Shared outbound packet budget for every module that spams packets.
 *
 * <p>Paper's default packet limiter kicks any client averaging over 500
 * packets/second ("sent too many packets!"). Modules used to each reason about
 * that limit privately - MaceKill capped itself at 8 move packets per tick - so
 * two spammy modules enabled at once could blow straight through a ceiling each
 * one believed it was respecting. This is the single shared token bucket they
 * all draw from instead.
 *
 * <p>Accounting runs over a rolling one-second window of twenty 50 ms slots
 * (one slot per tick at 20 tps). Wall-clock slots rather than tick callbacks,
 * because the server limiter is wall-clock too and because it keeps the budget
 * correct while the client is not ticking. Every outbound packet is recorded
 * through {@link #onOutgoing()} in {@code ConnectionMixin}, so the window sees
 * the client's own baseline traffic as well as module traffic; only module
 * packets are gated, and genuine client traffic is never dropped.
 *
 * <p>Modules that need a run of packets to arrive together (a fall spoof is
 * meaningless without its closing packet) reserve the whole run up front with
 * {@link #open(int)} so the budget can never truncate it halfway and leave the
 * server position desynced; a run the budget cannot cover is skipped whole.
 * Packets that are the point of the whole exercise - the attack a route spent
 * seconds setting up - go through {@link #openPriority(int)}, which charges the
 * budget without ever denying, so the gated senders yield to them rather than
 * the other way round.
 *
 * <p>Within a tick modules are served in the order they run, so a module with
 * unbounded demand (MaceKill flying a sub-block column wants a move packet
 * every tick it can get) will hold the per-tick allowance while its route is in
 * flight, and Criticals / CrossbowMachineGun defer until it finishes. That is
 * the intended priority: a stalled route risks a "Flying is not enabled" kick,
 * while a skipped crit just lands as a normal hit.
 *
 * <p>{@code Connection.send} is not guaranteed to run on the render thread, so
 * all counters are guarded by a lock and the send scope is per-thread.
 */
public final class PacketBudget {
	/**
	 * Paper's default packet limiter: clients averaging over this many packets
	 * per second are kicked with "sent too many packets!".
	 */
	private static final int SERVER_LIMIT_PER_SECOND = 500;
	/**
	 * Ceiling on total measured outbound traffic. The server limiter is an
	 * average over traffic we cannot fully observe (packets sent through
	 * {@code Connection#send(Packet, PacketSendListener)} bypass our hook), so
	 * keep a fifth of the limit as slack.
	 */
	private static final int SAFE_TOTAL_PER_SECOND = SERVER_LIMIT_PER_SECOND * 4 / 5;
	/**
	 * Share of the limit all modules together may spend. MaceKill's original
	 * private cap reasoned that 8 moves/tick = 160/s is "under a third" of the
	 * default limit and leaves room for the client's normal traffic; that same
	 * budget now covers every module at once instead of MaceKill alone.
	 */
	public static final int MODULE_PACKETS_PER_SECOND = 160;

	/** Slots in the rolling window: one per tick at 20 tps. */
	private static final int SLOTS = 20;
	/** Wall-clock length of one slot, i.e. one tick at 20 tps. */
	private static final long SLOT_NANOS = 50_000_000L;
	/**
	 * Per-tick share of {@link #MODULE_PACKETS_PER_SECOND}. The rolling window
	 * is what actually guarantees the rate; this smooths bursts so one module
	 * cannot spend a whole second of budget inside a single tick.
	 */
	public static final int MODULE_PACKETS_PER_TICK = MODULE_PACKETS_PER_SECOND / SLOTS;

	private static final Object LOCK = new Object();
	private static final ThreadLocal<Scope> SCOPE = ThreadLocal.withInitial(Scope::new);

	private static final int[] TOTAL_SLOTS = new int[SLOTS];
	private static final int[] MODULE_SLOTS = new int[SLOTS];
	private static int slotIndex;
	private static long slotStart;
	private static boolean started;
	private static int totalWindow;
	private static int moduleWindow;
	/** Tokens handed out to open scopes that have not been spent yet. */
	private static int reserved;

	private PacketBudget() {
	}

	/**
	 * Called from {@link dev.virulent.client.mixin.ConnectionMixin} for every
	 * packet that has survived the module handlers and is about to leave.
	 *
	 * @return false when the packet is a module packet the budget cannot
	 *         afford, in which case the caller cancels the send
	 */
	public static boolean onOutgoing() {
		Scope scope = SCOPE.get();
		long now = System.nanoTime();
		synchronized (LOCK) {
			advance(now);
			if (scope.depth == 0) {
				// The client's own traffic. Always allowed, always counted.
				TOTAL_SLOTS[slotIndex]++;
				totalWindow++;
				return true;
			}
			int top = scope.depth - 1;
			if (scope.granted[top] > 0) {
				scope.granted[top]--;
				reserved--;
			} else if (!scope.priority[top] && available() <= 0) {
				return false;
			}
			// A priority scope that outran its reservation still sends - it is
			// undroppable by contract, and miscounting it (a helper that sends a
			// packet of its own) must not silently swallow the payload. The
			// overshoot is charged below, so gated senders yield that much more.
			TOTAL_SLOTS[slotIndex]++;
			totalWindow++;
			MODULE_SLOTS[slotIndex]++;
			moduleWindow++;
			return true;
		}
	}

	/**
	 * Opens a module send scope holding exactly {@code count} tokens, or none
	 * when the budget cannot afford the whole run. Always pair with
	 * {@link #close()} in a finally block; the scope opens either way.
	 *
	 * @return true when the full run was granted
	 */
	public static boolean open(int count) {
		Scope scope = SCOPE.get();
		boolean granted = true;
		if (count > 0) {
			long now = System.nanoTime();
			synchronized (LOCK) {
				advance(now);
				granted = available() >= count;
				if (granted) {
					reserved += count;
				}
			}
		}
		scope.push(granted ? Math.max(0, count) : 0, false);
		return granted;
	}

	/**
	 * Opens a send scope for module packets that must not be dropped - the
	 * payload a whole sequence exists to deliver, like the attack a MaceKill
	 * route spent seconds setting up. They always go out, but they are still
	 * charged to the module share, so the gated consumers back off over the
	 * following ticks to make room for them instead of ignoring them. Always
	 * pair with {@link #close()} in a finally block.
	 */
	public static void openPriority(int count) {
		Scope scope = SCOPE.get();
		if (count > 0) {
			synchronized (LOCK) {
				reserved += count;
			}
		}
		scope.push(Math.max(0, count), true);
	}

	/** Closes the innermost send scope, refunding tokens it never spent. */
	public static void close() {
		Scope scope = SCOPE.get();
		if (scope.depth == 0) {
			return;
		}
		int unspent = scope.granted[--scope.depth];
		if (unspent > 0) {
			synchronized (LOCK) {
				reserved = Math.max(0, reserved - unspent);
			}
		}
	}

	/** Module tokens spendable right now. Caller must hold {@link #LOCK}. */
	private static int available() {
		int free = Math.min(
			MODULE_PACKETS_PER_TICK - MODULE_SLOTS[slotIndex],
			MODULE_PACKETS_PER_SECOND - moduleWindow);
		free = Math.min(free, SAFE_TOTAL_PER_SECOND - totalWindow);
		return Math.max(0, free - reserved);
	}

	/** Rolls the window forward to {@code now}. Caller must hold {@link #LOCK}. */
	private static void advance(long now) {
		if (!started) {
			started = true;
			slotStart = now;
			return;
		}
		long elapsed = now - slotStart;
		if (elapsed < SLOT_NANOS) {
			return;
		}
		long slots = elapsed / SLOT_NANOS;
		if (slots >= SLOTS) {
			Arrays.fill(TOTAL_SLOTS, 0);
			Arrays.fill(MODULE_SLOTS, 0);
			totalWindow = 0;
			moduleWindow = 0;
			slotIndex = 0;
			slotStart = now;
			return;
		}
		for (int i = 0; i < slots; i++) {
			slotIndex = (slotIndex + 1) % SLOTS;
			totalWindow -= TOTAL_SLOTS[slotIndex];
			moduleWindow -= MODULE_SLOTS[slotIndex];
			TOTAL_SLOTS[slotIndex] = 0;
			MODULE_SLOTS[slotIndex] = 0;
		}
		slotStart += slots * SLOT_NANOS;
	}

	/** Per-thread stack of open send scopes and the tokens each still holds. */
	private static final class Scope {
		private int[] granted = new int[4];
		private boolean[] priority = new boolean[4];
		private int depth;

		private void push(int count, boolean prio) {
			if (depth == granted.length) {
				granted = Arrays.copyOf(granted, depth * 2);
				priority = Arrays.copyOf(priority, depth * 2);
			}
			granted[depth] = count;
			priority[depth] = prio;
			depth++;
		}
	}
}
