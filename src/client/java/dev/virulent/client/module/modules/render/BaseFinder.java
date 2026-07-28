package dev.virulent.client.module.modules.render;

import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.Render2DUtil;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.util.WorldToScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BaseFinder extends Module {
	private static final int SCAN_INTERVAL_TICKS = 8;
	private static final int RESCAN_MOVE_BLOCKS = 4;

	private final ModeSetting filterMode = addSetting(new ModeSetting("Filter", "Smart", "All", "Smart", "Strict"));
	private final NumberSetting range = addSetting(new NumberSetting("Range", 64.0, 16.0, 128.0, 8.0));
	private final NumberSetting maxHighlights = addSetting(new NumberSetting("Max Highlights", 128.0, 16.0, 512.0, 16.0));
	private final BooleanSetting chatAlerts = addSetting(new BooleanSetting("Chat Alerts", true));
	private final NumberSetting minBlocks = addSetting(new NumberSetting("Min Blocks", 3.0, 1.0, 32.0, 1.0));
	private final NumberSetting minScore = addSetting(new NumberSetting("Min Score", 28.0, 10.0, 120.0, 2.0));
	private final NumberSetting clusterRadius = addSetting(new NumberSetting("Cluster Radius", 28.0, 8.0, 64.0, 4.0));
	private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", false));
	private final NumberSetting thickness = addSetting(new NumberSetting("Thickness", 1.0, 0.5, 5.0, 0.5));

	private final BooleanSetting chests = addSetting(new BooleanSetting("Chests", true));
	private final BooleanSetting enderChests = addSetting(new BooleanSetting("Ender Chests", true));
	private final BooleanSetting shulkers = addSetting(new BooleanSetting("Shulkers", true));
	private final BooleanSetting barrels = addSetting(new BooleanSetting("Barrels", true));
	private final BooleanSetting hoppers = addSetting(new BooleanSetting("Hoppers", true));
	private final BooleanSetting furnaces = addSetting(new BooleanSetting("Furnaces", true));
	private final BooleanSetting spawners = addSetting(new BooleanSetting("Spawners", true));
	private final BooleanSetting beacons = addSetting(new BooleanSetting("Beacons", true));
	private final BooleanSetting brewing = addSetting(new BooleanSetting("Brewing Stands", true));
	private final BooleanSetting enchanting = addSetting(new BooleanSetting("Enchant Tables", true));
	private final BooleanSetting anvils = addSetting(new BooleanSetting("Anvils", false));
	private final BooleanSetting dispensers = addSetting(new BooleanSetting("Dispensers", false));
	private final BooleanSetting heads = addSetting(new BooleanSetting("Heads", true));
	private final BooleanSetting beds = addSetting(new BooleanSetting("Beds", false));
	private final BooleanSetting vaults = addSetting(new BooleanSetting("Vaults", true));
	private final BooleanSetting pots = addSetting(new BooleanSetting("Decorated Pots", false));
	private final BooleanSetting lecterns = addSetting(new BooleanSetting("Lecterns", false));
	private final BooleanSetting respawnAnchors = addSetting(new BooleanSetting("Respawn Anchors", true));

	private final Map<Block, TargetInfo> targets = new HashMap<>();
	private final List<Highlight> cachedHighlights = new ArrayList<>();
	private final Set<Long> reportedBases = new HashSet<>();
	private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

	private int scanCooldown;
	private int lastScanX = Integer.MIN_VALUE;
	private int lastScanY = Integer.MIN_VALUE;
	private int lastScanZ = Integer.MIN_VALUE;
	private int lastScanRange = -1;
	private int lastMaxHighlights = -1;
	private int lastMinBlocks = -1;
	private int lastMinScore = -1;
	private int lastClusterRadius = -1;
	private String lastFilterMode = "";
	private int lastTargetFingerprint = Integer.MIN_VALUE;

	public BaseFinder() {
		super("BaseFinder", "Scores storage/utility clusters to find player bases and cut structure noise.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render3DEvent.class, this::onRender3D);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	@Override
	public void onTick() {
		if (mc().level == null || mc().player == null) {
			return;
		}

		rebuildTargetsIfNeeded();

		BlockPos center = mc().player.blockPosition();
		int scanRange = range.getValue().intValue();
		int highlightLimit = maxHighlights.getValue().intValue();
		int blocksFloor = minBlocks.getValue().intValue();
		int scoreFloor = minScore.getValue().intValue();
		int radius = clusterRadius.getValue().intValue();
		String mode = filterMode.getValue();
		boolean movedFar =
			Math.abs(center.getX() - lastScanX) >= RESCAN_MOVE_BLOCKS
				|| Math.abs(center.getY() - lastScanY) >= RESCAN_MOVE_BLOCKS
				|| Math.abs(center.getZ() - lastScanZ) >= RESCAN_MOVE_BLOCKS;
		boolean settingsChanged =
			scanRange != lastScanRange
				|| highlightLimit != lastMaxHighlights
				|| blocksFloor != lastMinBlocks
				|| scoreFloor != lastMinScore
				|| radius != lastClusterRadius
				|| !mode.equals(lastFilterMode);

		if (scanCooldown > 0) {
			scanCooldown--;
		}
		if (scanCooldown > 0 && !movedFar && !settingsChanged) {
			return;
		}

		scanHighlights(center, scanRange, highlightLimit);
		scanCooldown = SCAN_INTERVAL_TICKS;
		lastScanX = center.getX();
		lastScanY = center.getY();
		lastScanZ = center.getZ();
		lastScanRange = scanRange;
		lastMaxHighlights = highlightLimit;
		lastMinBlocks = blocksFloor;
		lastMinScore = scoreFloor;
		lastClusterRadius = radius;
		lastFilterMode = mode;
	}

	private void onRender3D(Render3DEvent event) {
		if (mc().level == null || mc().player == null || cachedHighlights.isEmpty()) {
			return;
		}

		RenderUtil.beginLines(event.getContext());
		for (Highlight highlight : cachedHighlights) {
			RenderUtil.addBox(new AABB(highlight.pos()).deflate(0.05), highlight.color());
		}
		RenderUtil.endLines();
	}

	private void onRender2D(Render2DEvent event) {
		if (!tracers.getValue() || mc().level == null || mc().player == null || mc().options.hideGui || cachedHighlights.isEmpty()) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		float cursorX = mc().getWindow().getGuiScaledWidth() * 0.5f;
		float cursorY = mc().getWindow().getGuiScaledHeight() * 0.5f;
		float lineThickness = thickness.getValue().floatValue();
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);

		for (Highlight highlight : cachedHighlights) {
			Vec3 target = Vec3.atCenterOf(highlight.pos());
			float[] screen = WorldToScreen.project(target, tickDelta);
			if (screen == null) {
				continue;
			}
			Render2DUtil.drawLine(context, cursorX, cursorY, screen[0], screen[1], highlight.color(), lineThickness);
		}
	}

	private void scanHighlights(BlockPos center, int scanRange, int highlightLimit) {
		Level level = mc().level;
		double playerX = mc().player.getX();
		double playerY = mc().player.getY();
		double playerZ = mc().player.getZ();
		double horizontalRangeSq = scanRange * (double) scanRange;
		int worldMinY = level.getMinY();
		int worldMaxY = worldMinY + level.getHeight() - 1;
		int minY = Math.max(worldMinY, center.getY() - scanRange);
		int maxY = Math.min(worldMaxY, center.getY() + scanRange);

		List<Highlight> found = new ArrayList<>(Math.min(highlightLimit * 2, 256));

		int minChunkX = SectionPos.blockToSectionCoord(center.getX() - scanRange);
		int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + scanRange);
		int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - scanRange);
		int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + scanRange);
		int minSectionY = SectionPos.blockToSectionCoord(minY);
		int maxSectionY = SectionPos.blockToSectionCoord(maxY);

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				LevelChunkSection[] sections = chunk.getSections();
				int minSectionIndex = level.getMinSectionY();

				for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
					int sectionIndex = sectionY - minSectionIndex;
					if (sectionIndex < 0 || sectionIndex >= sections.length) {
						continue;
					}

					LevelChunkSection section = sections[sectionIndex];
					if (section == null || section.hasOnlyAir()) {
						continue;
					}

					int sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
					int localMinY = Math.max(0, minY - sectionMinY);
					int localMaxY = Math.min(15, maxY - sectionMinY);
					int blockMinX = Math.max(chunkX << 4, center.getX() - scanRange);
					int blockMaxX = Math.min((chunkX << 4) + 15, center.getX() + scanRange);
					int blockMinZ = Math.max(chunkZ << 4, center.getZ() - scanRange);
					int blockMaxZ = Math.min((chunkZ << 4) + 15, center.getZ() + scanRange);

					for (int x = blockMinX; x <= blockMaxX; x++) {
						double dx = x + 0.5 - playerX;
						double dxSq = dx * dx;
						for (int z = blockMinZ; z <= blockMaxZ; z++) {
							double dz = z + 0.5 - playerZ;
							if (dxSq + dz * dz > horizontalRangeSq) {
								continue;
							}

							int localX = x & 15;
							int localZ = z & 15;
							for (int localY = localMinY; localY <= localMaxY; localY++) {
								BlockState state = section.getBlockState(localX, localY, localZ);
								if (state.isAir()) {
									continue;
								}

								TargetInfo info = targets.get(state.getBlock());
								if (info == null) {
									continue;
								}

								int y = sectionMinY + localY;
								mutablePos.set(x, y, z);
								double dy = y + 0.5 - playerY;
								found.add(new Highlight(mutablePos.immutable(), info.color(), info.signal(), dxSq + dy * dy + dz * dz));
							}
						}
					}
				}
			}
		}

		List<Cluster> clusters = buildClusters(found);
		boolean filterBases = !"All".equals(filterMode.getValue());
		List<Highlight> renderPool = found;
		if (filterBases) {
			renderPool = new ArrayList<>();
			for (Cluster cluster : clusters) {
				if (cluster.accepted()) {
					renderPool.addAll(cluster.highlights());
				}
			}
		}

		renderPool.sort(Comparator.comparingDouble(Highlight::distanceSq));
		cachedHighlights.clear();
		int limit = Math.min(highlightLimit, renderPool.size());
		for (int i = 0; i < limit; i++) {
			cachedHighlights.add(renderPool.get(i));
		}

		if (chatAlerts.getValue()) {
			alertBases(clusters);
		}
	}

	private List<Cluster> buildClusters(List<Highlight> highlights) {
		List<Cluster> clusters = new ArrayList<>();
		if (highlights.isEmpty()) {
			return clusters;
		}

		int radius = clusterRadius.getValue().intValue();
		int radiusSq = radius * radius;
		boolean[] used = new boolean[highlights.size()];

		for (int i = 0; i < highlights.size(); i++) {
			if (used[i]) {
				continue;
			}

			List<Highlight> members = new ArrayList<>();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(i);
			used[i] = true;

			while (!queue.isEmpty()) {
				int index = queue.removeFirst();
				Highlight current = highlights.get(index);
				members.add(current);
				BlockPos pos = current.pos();

				for (int j = 0; j < highlights.size(); j++) {
					if (used[j]) {
						continue;
					}
					BlockPos other = highlights.get(j).pos();
					int dx = pos.getX() - other.getX();
					int dy = pos.getY() - other.getY();
					int dz = pos.getZ() - other.getZ();
					if (dx * dx + dy * dy + dz * dz <= radiusSq) {
						used[j] = true;
						queue.add(j);
					}
				}
			}

			clusters.add(evaluateCluster(members));
		}

		return clusters;
	}

	private Cluster evaluateCluster(List<Highlight> members) {
		EnumMap<Signal, Integer> counts = new EnumMap<>(Signal.class);
		for (Signal signal : Signal.values()) {
			counts.put(signal, 0);
		}
		for (Highlight highlight : members) {
			counts.merge(highlight.signal(), 1, Integer::sum);
		}

		int rawScore = 0;
		EnumSet<Signal> present = EnumSet.noneOf(Signal.class);
		for (Map.Entry<Signal, Integer> entry : counts.entrySet()) {
			int count = entry.getValue();
			if (count <= 0) {
				continue;
			}
			Signal signal = entry.getKey();
			present.add(signal);
			rawScore += signal.weight * count;
			if (count > 1) {
				// Diminishing returns so a mineshaft of chests doesn't dominate.
				rawScore += signal.weight * Math.min(count - 1, 6) / 2;
			}
		}

		int diversity = present.size();
		boolean hasStrong = present.stream().anyMatch(Signal::strong);
		boolean hasStorage = present.contains(Signal.CHEST)
			|| present.contains(Signal.BARREL)
			|| present.contains(Signal.SHULKER)
			|| present.contains(Signal.ENDER_CHEST)
			|| present.contains(Signal.HOPPER);
		boolean hasUtility = present.contains(Signal.FURNACE)
			|| present.contains(Signal.BREWING)
			|| present.contains(Signal.ENCHANTING)
			|| present.contains(Signal.ANVIL)
			|| present.contains(Signal.BEACON)
			|| present.contains(Signal.RESPAWN_ANCHOR)
			|| present.contains(Signal.DISPENSER);

		int score = rawScore;
		if (diversity >= 3) {
			score += 10;
		} else if (diversity >= 2) {
			score += 5;
		}
		if (hasStorage && hasUtility) {
			score += 12;
		}
		if (hasStrong) {
			score += 14;
		}

		// Compact stashes score higher than sparse loot spreads.
		BlockPos middle = clusterCenter(members);
		int spread = clusterSpread(members, middle);
		if (spread <= 10) {
			score += 8;
		} else if (spread <= 18) {
			score += 3;
		} else if (spread >= 40) {
			score -= 8;
		}

		String rejectReason = null;
		if (isTrialChamberPattern(present, counts)) {
			score -= 40;
			rejectReason = "trial chamber";
		} else if (isDungeonPattern(present, counts, members.size())) {
			score -= 22;
			rejectReason = "dungeon";
		} else if (isVillagePattern(present, counts, middle)) {
			score -= 26;
			rejectReason = "village";
		} else if (isChestOnlyLoot(present, counts, members.size(), spread)) {
			score -= 18;
			rejectReason = "loot chests";
		}

		score = Math.max(0, score);

		int blocksFloor = minBlocks.getValue().intValue();
		int scoreFloor = effectiveMinScore();
		boolean accepted;
		if ("All".equals(filterMode.getValue())) {
			accepted = members.size() >= blocksFloor;
		} else {
			accepted = members.size() >= blocksFloor
				&& score >= scoreFloor
				&& (hasStrong || diversity >= 2 || (hasStorage && hasUtility));
			if (rejectReason != null && score < scoreFloor + 10 && !hasStrong) {
				accepted = false;
			}
		}

		Confidence confidence = confidenceFor(score, hasStrong, diversity);
		return new Cluster(members, middle, score, diversity, confidence, accepted, present, rejectReason);
	}

	private int effectiveMinScore() {
		int base = minScore.getValue().intValue();
		if ("Strict".equals(filterMode.getValue())) {
			return base + 16;
		}
		return base;
	}

	private static boolean isTrialChamberPattern(Set<Signal> present, Map<Signal, Integer> counts) {
		int trialish = counts.getOrDefault(Signal.VAULT, 0) + counts.getOrDefault(Signal.TRIAL_SPAWNER, 0);
		if (trialish == 0) {
			return false;
		}
		EnumSet<Signal> withoutTrial = EnumSet.copyOf(present);
		withoutTrial.remove(Signal.VAULT);
		withoutTrial.remove(Signal.TRIAL_SPAWNER);
		return withoutTrial.isEmpty() || (withoutTrial.size() == 1 && withoutTrial.contains(Signal.POT));
	}

	private static boolean isDungeonPattern(Set<Signal> present, Map<Signal, Integer> counts, int size) {
		int spawners = counts.getOrDefault(Signal.SPAWNER, 0);
		if (spawners != 1) {
			return false;
		}
		int chests = counts.getOrDefault(Signal.CHEST, 0);
		if (chests == 0 || chests > 2) {
			return false;
		}
		EnumSet<Signal> others = EnumSet.copyOf(present);
		others.remove(Signal.SPAWNER);
		others.remove(Signal.CHEST);
		return others.isEmpty() && size <= 4;
	}

	private static boolean isVillagePattern(Set<Signal> present, Map<Signal, Integer> counts, BlockPos middle) {
		if (middle.getY() < 50) {
			return false;
		}
		EnumSet<Signal> villageSignals = EnumSet.of(
			Signal.CHEST, Signal.BARREL, Signal.FURNACE, Signal.BED, Signal.LECTERN, Signal.ANVIL, Signal.POT
		);
		for (Signal signal : present) {
			if (!villageSignals.contains(signal)) {
				return false;
			}
		}
		boolean hasSoft = present.contains(Signal.BED) || present.contains(Signal.LECTERN) || present.contains(Signal.FURNACE);
		int storage = counts.getOrDefault(Signal.CHEST, 0) + counts.getOrDefault(Signal.BARREL, 0);
		return hasSoft && storage <= 4 && !present.contains(Signal.HOPPER);
	}

	private static boolean isChestOnlyLoot(Set<Signal> present, Map<Signal, Integer> counts, int size, int spread) {
		EnumSet<Signal> loot = EnumSet.of(Signal.CHEST, Signal.BARREL, Signal.POT);
		for (Signal signal : present) {
			if (!loot.contains(signal)) {
				return false;
			}
		}
		int storage = counts.getOrDefault(Signal.CHEST, 0) + counts.getOrDefault(Signal.BARREL, 0);
		return storage <= 5 && size <= 6 && spread >= 16;
	}

	private static Confidence confidenceFor(int score, boolean hasStrong, int diversity) {
		if (hasStrong && score >= 70 || score >= 90) {
			return Confidence.CONFIRMED;
		}
		if (score >= 55 || (hasStrong && diversity >= 2)) {
			return Confidence.HIGH;
		}
		if (score >= 35) {
			return Confidence.MEDIUM;
		}
		return Confidence.LOW;
	}

	private void alertBases(List<Cluster> clusters) {
		if (mc().player == null) {
			return;
		}

		List<Cluster> ordered = new ArrayList<>();
		for (Cluster cluster : clusters) {
			if (cluster.accepted()) {
				ordered.add(cluster);
			}
		}
		ordered.sort(Comparator.comparingInt(Cluster::score).reversed());

		for (Cluster cluster : ordered) {
			long key = baseKey(cluster.center());
			if (!reportedBases.add(key)) {
				continue;
			}

			String summary = summarizeSignals(cluster);
			mc().player.sendSystemMessage(Component.literal(
				"[Virulent] Base " + cluster.confidence().label
					+ " near " + cluster.center().getX() + ", " + cluster.center().getY() + ", " + cluster.center().getZ()
					+ " (" + cluster.highlights().size() + " blocks, score " + cluster.score()
					+ (summary.isEmpty() ? "" : ", " + summary) + ")"
			));
		}
	}

	private static String summarizeSignals(Cluster cluster) {
		List<String> parts = new ArrayList<>();
		EnumMap<Signal, Integer> counts = new EnumMap<>(Signal.class);
		for (Highlight highlight : cluster.highlights()) {
			counts.merge(highlight.signal(), 1, Integer::sum);
		}
		counts.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getKey().weight * b.getValue(), a.getKey().weight * a.getValue()))
			.limit(4)
			.forEach(entry -> parts.add(entry.getValue() + "x " + entry.getKey().label));
		return String.join(", ", parts);
	}

	private static BlockPos clusterCenter(List<Highlight> cluster) {
		long sumX = 0;
		long sumY = 0;
		long sumZ = 0;
		for (Highlight highlight : cluster) {
			BlockPos pos = highlight.pos();
			sumX += pos.getX();
			sumY += pos.getY();
			sumZ += pos.getZ();
		}
		int n = cluster.size();
		return new BlockPos(
			(int) Math.round(sumX / (double) n),
			(int) Math.round(sumY / (double) n),
			(int) Math.round(sumZ / (double) n)
		);
	}

	private static int clusterSpread(List<Highlight> cluster, BlockPos middle) {
		int max = 0;
		for (Highlight highlight : cluster) {
			BlockPos pos = highlight.pos();
			int dx = pos.getX() - middle.getX();
			int dy = pos.getY() - middle.getY();
			int dz = pos.getZ() - middle.getZ();
			int distSq = dx * dx + dy * dy + dz * dz;
			if (distSq > max) {
				max = distSq;
			}
		}
		return (int) Math.round(Math.sqrt(max));
	}

	/** Quantize to a coarse grid so the same base isn't re-alerted as the center drifts. */
	private static long baseKey(BlockPos middle) {
		int qx = Math.floorDiv(middle.getX(), 16);
		int qy = Math.floorDiv(middle.getY(), 16);
		int qz = Math.floorDiv(middle.getZ(), 16);
		return BlockPos.asLong(qx, qy, qz);
	}

	private void rebuildTargetsIfNeeded() {
		int fingerprint = targetFingerprint();
		if (fingerprint == lastTargetFingerprint) {
			return;
		}

		lastTargetFingerprint = fingerprint;
		targets.clear();

		if (chests.getValue()) {
			addBlock(Blocks.CHEST, 0xFFFFAA00, Signal.CHEST);
			addBlock(Blocks.TRAPPED_CHEST, 0xFFFF8800, Signal.CHEST);
			addTagged(BlockTags.COPPER_CHESTS, 0xFFFF9944, Signal.CHEST);
		}
		if (enderChests.getValue()) {
			addBlock(Blocks.ENDER_CHEST, 0xFFAA44FF, Signal.ENDER_CHEST);
		}
		if (shulkers.getValue()) {
			addTagged(BlockTags.SHULKER_BOXES, 0xFFE14CFF, Signal.SHULKER);
		}
		if (barrels.getValue()) {
			addBlock(Blocks.BARREL, 0xFFC48A3A, Signal.BARREL);
		}
		if (hoppers.getValue()) {
			addBlock(Blocks.HOPPER, 0xFF888888, Signal.HOPPER);
		}
		if (furnaces.getValue()) {
			addBlock(Blocks.FURNACE, 0xFFFF6622, Signal.FURNACE);
			addBlock(Blocks.BLAST_FURNACE, 0xFFFF4422, Signal.FURNACE);
			addBlock(Blocks.SMOKER, 0xFFFF8844, Signal.FURNACE);
		}
		if (spawners.getValue()) {
			addBlock(Blocks.SPAWNER, 0xFFCC44FF, Signal.SPAWNER);
			addBlock(Blocks.TRIAL_SPAWNER, 0xFFFF44AA, Signal.TRIAL_SPAWNER);
		}
		if (beacons.getValue()) {
			addBlock(Blocks.BEACON, 0xFF44FFFF, Signal.BEACON);
		}
		if (brewing.getValue()) {
			addBlock(Blocks.BREWING_STAND, 0xFF66FF66, Signal.BREWING);
		}
		if (enchanting.getValue()) {
			addBlock(Blocks.ENCHANTING_TABLE, 0xFF4466FF, Signal.ENCHANTING);
		}
		if (anvils.getValue()) {
			addTagged(BlockTags.ANVIL, 0xFFCCCCCC, Signal.ANVIL);
		}
		if (dispensers.getValue()) {
			addBlock(Blocks.DISPENSER, 0xFF666666, Signal.DISPENSER);
			addBlock(Blocks.DROPPER, 0xFF777777, Signal.DISPENSER);
		}
		if (heads.getValue()) {
			addBlock(Blocks.PLAYER_HEAD, 0xFFFFFFFF, Signal.PLAYER_HEAD);
			addBlock(Blocks.PLAYER_WALL_HEAD, 0xFFFFFFFF, Signal.PLAYER_HEAD);
			addBlock(Blocks.SKELETON_SKULL, 0xFFEEEEEE, Signal.SKULL);
			addBlock(Blocks.SKELETON_WALL_SKULL, 0xFFEEEEEE, Signal.SKULL);
			addBlock(Blocks.WITHER_SKELETON_SKULL, 0xFF222222, Signal.SKULL);
			addBlock(Blocks.WITHER_SKELETON_WALL_SKULL, 0xFF222222, Signal.SKULL);
			addBlock(Blocks.ZOMBIE_HEAD, 0xFF55AA55, Signal.SKULL);
			addBlock(Blocks.ZOMBIE_WALL_HEAD, 0xFF55AA55, Signal.SKULL);
			addBlock(Blocks.CREEPER_HEAD, 0xFF33AA33, Signal.SKULL);
			addBlock(Blocks.CREEPER_WALL_HEAD, 0xFF33AA33, Signal.SKULL);
			addBlock(Blocks.DRAGON_HEAD, 0xFFAA44AA, Signal.SKULL);
			addBlock(Blocks.DRAGON_WALL_HEAD, 0xFFAA44AA, Signal.SKULL);
			addBlock(Blocks.PIGLIN_HEAD, 0xFFFFAA88, Signal.SKULL);
			addBlock(Blocks.PIGLIN_WALL_HEAD, 0xFFFFAA88, Signal.SKULL);
		}
		if (beds.getValue()) {
			addTagged(BlockTags.BEDS, 0xFFFF6699, Signal.BED);
		}
		if (vaults.getValue()) {
			addBlock(Blocks.VAULT, 0xFFFFD700, Signal.VAULT);
		}
		if (pots.getValue()) {
			addBlock(Blocks.DECORATED_POT, 0xFFCC7744, Signal.POT);
		}
		if (lecterns.getValue()) {
			addBlock(Blocks.LECTERN, 0xFFAA7744, Signal.LECTERN);
		}
		if (respawnAnchors.getValue()) {
			addBlock(Blocks.RESPAWN_ANCHOR, 0xFFAA22FF, Signal.RESPAWN_ANCHOR);
		}

		scanCooldown = 0;
	}

	private void addBlock(Block block, int color, Signal signal) {
		targets.put(block, new TargetInfo(color, signal));
	}

	private void addTagged(TagKey<Block> tag, int color, Signal signal) {
		if (mc().level == null) {
			return;
		}
		var optional = mc().level.registryAccess().lookupOrThrow(Registries.BLOCK).get(tag);
		optional.ifPresent(set -> set.forEach(holder -> targets.put(holder.value(), new TargetInfo(color, signal))));
	}

	private int targetFingerprint() {
		int value = 0;
		value = (value << 1) | (chests.getValue() ? 1 : 0);
		value = (value << 1) | (enderChests.getValue() ? 1 : 0);
		value = (value << 1) | (shulkers.getValue() ? 1 : 0);
		value = (value << 1) | (barrels.getValue() ? 1 : 0);
		value = (value << 1) | (hoppers.getValue() ? 1 : 0);
		value = (value << 1) | (furnaces.getValue() ? 1 : 0);
		value = (value << 1) | (spawners.getValue() ? 1 : 0);
		value = (value << 1) | (beacons.getValue() ? 1 : 0);
		value = (value << 1) | (brewing.getValue() ? 1 : 0);
		value = (value << 1) | (enchanting.getValue() ? 1 : 0);
		value = (value << 1) | (anvils.getValue() ? 1 : 0);
		value = (value << 1) | (dispensers.getValue() ? 1 : 0);
		value = (value << 1) | (heads.getValue() ? 1 : 0);
		value = (value << 1) | (beds.getValue() ? 1 : 0);
		value = (value << 1) | (vaults.getValue() ? 1 : 0);
		value = (value << 1) | (pots.getValue() ? 1 : 0);
		value = (value << 1) | (lecterns.getValue() ? 1 : 0);
		value = (value << 1) | (respawnAnchors.getValue() ? 1 : 0);
		return value;
	}

	@Override
	protected void onEnable() {
		lastTargetFingerprint = Integer.MIN_VALUE;
		scanCooldown = 0;
		cachedHighlights.clear();
		reportedBases.clear();
		rebuildTargetsIfNeeded();
	}

	@Override
	protected void onDisable() {
		cachedHighlights.clear();
		targets.clear();
		reportedBases.clear();
		lastTargetFingerprint = Integer.MIN_VALUE;
	}

	private enum Signal {
		CHEST(5, false, "chest"),
		BARREL(5, false, "barrel"),
		HOPPER(8, false, "hopper"),
		SHULKER(26, true, "shulker"),
		ENDER_CHEST(22, true, "ender chest"),
		FURNACE(4, false, "furnace"),
		SPAWNER(14, false, "spawner"),
		TRIAL_SPAWNER(4, false, "trial spawner"),
		BEACON(36, true, "beacon"),
		BREWING(14, true, "brewing"),
		ENCHANTING(14, true, "enchant"),
		ANVIL(5, false, "anvil"),
		DISPENSER(4, false, "dispenser"),
		PLAYER_HEAD(30, true, "player head"),
		SKULL(6, false, "skull"),
		BED(3, false, "bed"),
		VAULT(6, false, "vault"),
		POT(2, false, "pot"),
		LECTERN(2, false, "lectern"),
		RESPAWN_ANCHOR(16, true, "respawn anchor");

		private final int weight;
		private final boolean strong;
		private final String label;

		Signal(int weight, boolean strong, String label) {
			this.weight = weight;
			this.strong = strong;
			this.label = label;
		}

		boolean strong() {
			return strong;
		}
	}

	private enum Confidence {
		LOW("Low"),
		MEDIUM("Medium"),
		HIGH("High"),
		CONFIRMED("Confirmed");

		private final String label;

		Confidence(String label) {
			this.label = label;
		}
	}

	private record TargetInfo(int color, Signal signal) {
	}

	private record Highlight(BlockPos pos, int color, Signal signal, double distanceSq) {
	}

	private record Cluster(
		List<Highlight> highlights,
		BlockPos center,
		int score,
		int diversity,
		Confidence confidence,
		boolean accepted,
		Set<Signal> signals,
		String rejectReason
	) {
	}
}
