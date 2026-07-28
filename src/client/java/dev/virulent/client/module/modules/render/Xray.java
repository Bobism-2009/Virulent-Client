package dev.virulent.client.module.modules.render;

import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Xray extends Module {
	private static final int SCAN_INTERVAL_TICKS = 8;
	private static final int RESCAN_MOVE_BLOCKS = 4;

	private static Xray instance;

	private final ModeSetting mode = addSetting(new ModeSetting("Mode", "ESP", "ESP", "Full"));
	private final ModeSetting expose = addSetting(new ModeSetting("Expose", "Cave", "Strict", "Cave", "Nearby", "All"));
	private final NumberSetting range = addSetting(new NumberSetting("Range", 24.0, 8.0, 48.0, 4.0));
	private final NumberSetting maxHighlights = addSetting(new NumberSetting("Max Highlights", 128.0, 16.0, 512.0, 16.0));
	private final BooleanSetting diamond = addSetting(new BooleanSetting("Diamond", true));
	private final BooleanSetting emerald = addSetting(new BooleanSetting("Emerald", true));
	private final BooleanSetting gold = addSetting(new BooleanSetting("Gold", true));
	private final BooleanSetting iron = addSetting(new BooleanSetting("Iron", true));
	private final BooleanSetting coal = addSetting(new BooleanSetting("Coal", true));
	private final BooleanSetting redstone = addSetting(new BooleanSetting("Redstone", true));
	private final BooleanSetting lapis = addSetting(new BooleanSetting("Lapis", true));
	private final BooleanSetting copper = addSetting(new BooleanSetting("Copper", true));
	private final BooleanSetting quartz = addSetting(new BooleanSetting("Quartz", true));
	private final BooleanSetting debris = addSetting(new BooleanSetting("Ancient Debris", true));
	private final BooleanSetting chests = addSetting(new BooleanSetting("Chests", true));
	private final BooleanSetting spawners = addSetting(new BooleanSetting("Spawners", false));

	private final Set<Block> targetBlocks = new HashSet<>();
	private final List<Highlight> cachedHighlights = new ArrayList<>();
	private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

	private int scanCooldown;
	private int lastScanX = Integer.MIN_VALUE;
	private int lastScanY = Integer.MIN_VALUE;
	private int lastScanZ = Integer.MIN_VALUE;
	private int lastScanRange = -1;
	private int lastMaxHighlights = -1;
	private String lastMode = "";
	private String lastExpose = "";
	private int lastTargetFingerprint = Integer.MIN_VALUE;
	private boolean wasEspMode = true;

	public Xray() {
		super("Xray", "Highlights ores through walls. Strict mode filters fake ores from anti-xray.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
		subscribe(Render3DEvent.class, this::onRender3D);
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static boolean shouldRender(BlockState state) {
		if (!isActive() || instance == null || instance.isEspMode() || state == null || state.isAir()) {
			return true;
		}
		return instance.targetBlocks.contains(state.getBlock());
	}

	private boolean isEspMode() {
		return mode.getValue().equals("ESP");
	}

	@Override
	public void onTick() {
		if (mc().level == null || mc().player == null) {
			return;
		}

		rebuildTargetsIfNeeded();

		String currentMode = mode.getValue();
		String currentExpose = expose.getValue();
		boolean esp = isEspMode();

		// Full/ESP (and expose while Full) need a chunk/texture rebuild to take effect.
		if (!currentMode.equals(lastMode) || wasEspMode != esp || (!esp && !currentExpose.equals(lastExpose))) {
			wasEspMode = esp;
			lastMode = currentMode;
			lastExpose = currentExpose;
			cachedHighlights.clear();
			scanCooldown = 0;
			reloadChunks();
		}

		if (!esp) {
			return;
		}

		BlockPos center = mc().player.blockPosition();
		int scanRange = range.getValue().intValue();
		int highlightLimit = maxHighlights.getValue().intValue();
		boolean movedFar =
			Math.abs(center.getX() - lastScanX) >= RESCAN_MOVE_BLOCKS
				|| Math.abs(center.getY() - lastScanY) >= RESCAN_MOVE_BLOCKS
				|| Math.abs(center.getZ() - lastScanZ) >= RESCAN_MOVE_BLOCKS;
		boolean settingsChanged =
			scanRange != lastScanRange
				|| highlightLimit != lastMaxHighlights
				|| !currentExpose.equals(lastExpose)
				|| !currentMode.equals(lastMode);

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
		lastExpose = currentExpose;
		lastMode = currentMode;
	}

	private void onRender3D(Render3DEvent event) {
		if (mc().level == null || mc().player == null || !isEspMode() || cachedHighlights.isEmpty()) {
			return;
		}

		RenderUtil.beginLines(event.getContext());
		for (Highlight highlight : cachedHighlights) {
			RenderUtil.addBox(new AABB(highlight.pos()).deflate(0.05), withAlpha(highlight.color(), 0xFF));
		}
		RenderUtil.endLines();
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

								Block block = state.getBlock();
								if (!targetBlocks.contains(block)) {
									continue;
								}

								int y = sectionMinY + localY;
								mutablePos.set(x, y, z);
								int color = getColorFor(state);
								if (color == 0 || !shouldDrawHighlight(mutablePos, state)) {
									continue;
								}

								double dy = y + 0.5 - playerY;
								found.add(new Highlight(mutablePos.immutable(), color, dxSq + dy * dy + dz * dz));
							}
						}
					}
				}
			}
		}

		found.sort(Comparator.comparingDouble(Highlight::distanceSq));
		cachedHighlights.clear();
		int limit = Math.min(highlightLimit, found.size());
		for (int i = 0; i < limit; i++) {
			cachedHighlights.add(found.get(i));
		}
	}

	private void rebuildTargetsIfNeeded() {
		int fingerprint = targetFingerprint();
		if (fingerprint == lastTargetFingerprint) {
			return;
		}

		lastTargetFingerprint = fingerprint;
		targetBlocks.clear();

		if (diamond.getValue()) {
			addTagged(BlockTags.DIAMOND_ORES);
		}
		if (emerald.getValue()) {
			addTagged(BlockTags.EMERALD_ORES);
		}
		if (gold.getValue()) {
			addTagged(BlockTags.GOLD_ORES);
			targetBlocks.add(Blocks.NETHER_GOLD_ORE);
		}
		if (iron.getValue()) {
			addTagged(BlockTags.IRON_ORES);
		}
		if (coal.getValue()) {
			addTagged(BlockTags.COAL_ORES);
		}
		if (redstone.getValue()) {
			addTagged(BlockTags.REDSTONE_ORES);
		}
		if (lapis.getValue()) {
			addTagged(BlockTags.LAPIS_ORES);
		}
		if (copper.getValue()) {
			addTagged(BlockTags.COPPER_ORES);
		}
		if (quartz.getValue()) {
			targetBlocks.add(Blocks.NETHER_QUARTZ_ORE);
		}
		if (debris.getValue()) {
			targetBlocks.add(Blocks.ANCIENT_DEBRIS);
		}
		if (chests.getValue()) {
			targetBlocks.add(Blocks.CHEST);
			targetBlocks.add(Blocks.TRAPPED_CHEST);
			targetBlocks.add(Blocks.ENDER_CHEST);
			targetBlocks.add(Blocks.BARREL);
		}
		if (spawners.getValue()) {
			targetBlocks.add(Blocks.SPAWNER);
		}

		scanCooldown = 0;
		if (!isEspMode()) {
			reloadChunks();
		}
	}

	private void addTagged(net.minecraft.tags.TagKey<Block> tag) {
		if (mc().level == null) {
			return;
		}
		var optional = mc().level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK).get(tag);
		optional.ifPresent(set -> set.forEach(holder -> targetBlocks.add(holder.value())));
	}

	private int targetFingerprint() {
		int value = 0;
		value = (value << 1) | (diamond.getValue() ? 1 : 0);
		value = (value << 1) | (emerald.getValue() ? 1 : 0);
		value = (value << 1) | (gold.getValue() ? 1 : 0);
		value = (value << 1) | (iron.getValue() ? 1 : 0);
		value = (value << 1) | (coal.getValue() ? 1 : 0);
		value = (value << 1) | (redstone.getValue() ? 1 : 0);
		value = (value << 1) | (lapis.getValue() ? 1 : 0);
		value = (value << 1) | (copper.getValue() ? 1 : 0);
		value = (value << 1) | (quartz.getValue() ? 1 : 0);
		value = (value << 1) | (debris.getValue() ? 1 : 0);
		value = (value << 1) | (chests.getValue() ? 1 : 0);
		value = (value << 1) | (spawners.getValue() ? 1 : 0);
		return value;
	}

	private boolean shouldDrawHighlight(BlockPos pos, BlockState state) {
		return switch (expose.getValue()) {
			case "All" -> true;
			case "Nearby" -> hasOpenNeighbor(pos, false);
			case "Cave" -> hasOpenNeighbor(pos, true);
			case "Strict" -> passesStrictFilters(pos, state);
			default -> passesStrictFilters(pos, state);
		};
	}

	private boolean passesStrictFilters(BlockPos pos, BlockState state) {
		if (!isValidHeight(state, pos.getY())) {
			return false;
		}
		return matchesHostRock(pos, state);
	}

	private boolean isValidHeight(BlockState state, int y) {
		if (state.is(BlockTags.DIAMOND_ORES)) {
			return y <= 16;
		}
		if (state.is(BlockTags.EMERALD_ORES)) {
			return y >= -16 && y <= 320;
		}
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return y >= 8 && y <= 119;
		}
		if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
			return y >= 10 && y <= 117;
		}
		return true;
	}

	private boolean matchesHostRock(BlockPos pos, BlockState state) {
		BlockState above = mc().level.getBlockState(pos.above());

		if (state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)) {
			if (above.is(Blocks.GRASS_BLOCK) || above.is(Blocks.DIRT) || above.is(Blocks.ROOTED_DIRT)
				|| above.is(Blocks.SHORT_GRASS) || above.is(Blocks.TALL_GRASS)) {
				return false;
			}
		}

		if (state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)
			|| state.is(Blocks.DEEPSLATE_GOLD_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)
			|| state.is(Blocks.DEEPSLATE_COAL_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
			|| state.is(Blocks.DEEPSLATE_REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
			BlockState below = mc().level.getBlockState(pos.below());
			return below.is(Blocks.DEEPSLATE) || below.is(Blocks.COBBLED_DEEPSLATE)
				|| below.is(Blocks.TUFF) || below.is(Blocks.POLISHED_DEEPSLATE);
		}

		return true;
	}

	private boolean hasOpenNeighbor(BlockPos pos, boolean airOnly) {
		for (Direction direction : Direction.values()) {
			BlockState neighbor = mc().level.getBlockState(pos.relative(direction));
			if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) {
				return true;
			}
			if (!airOnly && !targetBlocks.contains(neighbor.getBlock())) {
				return true;
			}
		}
		return false;
	}

	private int getColorFor(BlockState state) {
		if (state.isAir()) {
			return 0;
		}

		if (diamond.getValue() && state.is(BlockTags.DIAMOND_ORES)) {
			return 0xFF00FFFF;
		}
		if (emerald.getValue() && state.is(BlockTags.EMERALD_ORES)) {
			return 0xFF00FF66;
		}
		if (gold.getValue() && (state.is(BlockTags.GOLD_ORES) || state.is(Blocks.NETHER_GOLD_ORE))) {
			return 0xFFFFD700;
		}
		if (iron.getValue() && state.is(BlockTags.IRON_ORES)) {
			return 0xFFDDDDDD;
		}
		if (coal.getValue() && state.is(BlockTags.COAL_ORES)) {
			return 0xFF999999;
		}
		if (redstone.getValue() && state.is(BlockTags.REDSTONE_ORES)) {
			return 0xFFFF2222;
		}
		if (lapis.getValue() && state.is(BlockTags.LAPIS_ORES)) {
			return 0xFF2266FF;
		}
		if (copper.getValue() && state.is(BlockTags.COPPER_ORES)) {
			return 0xFFFF8833;
		}
		if (quartz.getValue() && state.is(Blocks.NETHER_QUARTZ_ORE)) {
			return 0xFFFFFFFF;
		}
		if (debris.getValue() && state.is(Blocks.ANCIENT_DEBRIS)) {
			return 0xFFAA4422;
		}
		if (chests.getValue() && (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)
			|| state.is(Blocks.ENDER_CHEST) || state.is(Blocks.BARREL))) {
			return 0xFFFFAA00;
		}
		if (spawners.getValue() && state.is(Blocks.SPAWNER)) {
			return 0xFFCC44FF;
		}
		return 0;
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	@Override
	protected void onEnable() {
		lastTargetFingerprint = Integer.MIN_VALUE;
		scanCooldown = 0;
		cachedHighlights.clear();
		wasEspMode = isEspMode();
		rebuildTargetsIfNeeded();
		reloadChunks();
	}

	@Override
	protected void onDisable() {
		cachedHighlights.clear();
		targetBlocks.clear();
		lastTargetFingerprint = Integer.MIN_VALUE;
		reloadChunks();
	}

	private void reloadChunks() {
		var client = mc();
		if (client != null && client.levelRenderer != null) {
			client.levelRenderer.allChanged();
		}
	}

	private record Highlight(BlockPos pos, int color, double distanceSq) {
	}
}
