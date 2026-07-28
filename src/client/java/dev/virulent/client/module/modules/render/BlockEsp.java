package dev.virulent.client.module.modules.render;

import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BlockEspConfig;
import dev.virulent.client.setting.BlockEspConfigSetting;
import dev.virulent.client.setting.BlockEspConfigsSetting;
import dev.virulent.client.setting.BlockListSetting;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.util.Render2DUtil;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.util.WorldToScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockEsp extends Module {
	/** How many chunks to fully scan each tick. */
	private static final int CHUNKS_PER_TICK = 4;
	/** Max boxes drawn per frame (nearest kept). */
	private static final int MAX_RENDER = 512;
	/** Prevents wall blocks (e.g. deepslate) from drowning out chests in one chunk. */
	private static final int MAX_PER_BLOCK_PER_CHUNK = 32;

	private static BlockEsp instance;

	private final BlockListSetting blocks = addSetting(new BlockListSetting("Blocks"));
	private final BlockEspConfigSetting defaultBlockConfig = addSetting(
		new BlockEspConfigSetting("Default Block Config", BlockEspConfig.defaults())
	);
	private final BlockEspConfigsSetting blockConfigs = addSetting(new BlockEspConfigsSetting("Block Configs"));
	private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", false));

	private final Map<Long, ChunkCache> chunkCaches = new HashMap<>();
	private final Set<Long> queued = new HashSet<>();
	private final List<Long> scanQueue = new ArrayList<>();

	private Set<Block> targetSet = Set.of();
	private Map<Block, BlockEspConfig> configCache = Map.of();
	private int targetsFingerprint = Integer.MIN_VALUE;
	private int configFingerprint = Integer.MIN_VALUE;

	private long[] renderPositions = new long[0];
	private Block[] renderBlocks = new Block[0];
	private int renderCount;
	private boolean renderListDirty = true;
	private int renderRebuildCooldown;

	public BlockEsp() {
		super("BlockESP", "Renders specified blocks through walls.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
		subscribe(Render3DEvent.class, this::onRender3D);
		subscribe(Render2DEvent.class, this::onRender2D);
		blocks.onChange(value -> {
			targetsFingerprint = Integer.MIN_VALUE;
			clearChunkData();
		});
		defaultBlockConfig.onChange(value -> configFingerprint = Integer.MIN_VALUE);
		blockConfigs.onChange(value -> configFingerprint = Integer.MIN_VALUE);
	}

	public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
		BlockEsp self = instance;
		if (self == null || !self.isEnabled() || self.targetSet.isEmpty()) {
			return;
		}
		if (!self.targetSet.contains(oldState.getBlock()) && !self.targetSet.contains(newState.getBlock())) {
			return;
		}
		self.invalidateChunk(ChunkPos.pack(pos));
	}

	public BlockListSetting getBlocks() {
		return blocks;
	}

	public BlockEspConfigSetting getDefaultBlockConfig() {
		return defaultBlockConfig;
	}

	public BlockEspConfigsSetting getBlockConfigs() {
		return blockConfigs;
	}

	public BlockEspConfig configFor(Block block) {
		BlockEspConfig cached = configCache.get(block);
		if (cached != null) {
			return cached;
		}
		BlockEspConfig custom = blockConfigs.get(block);
		return custom != null ? custom : defaultBlockConfig.getValue();
	}

	@Override
	public void onTick() {
		if (mc().level == null || mc().player == null) {
			return;
		}
		if (blocks.size() == 0) {
			if (!chunkCaches.isEmpty() || renderCount > 0) {
				clearChunkData();
				targetSet = Set.of();
				targetsFingerprint = Integer.MIN_VALUE;
			}
			return;
		}

		refreshTargetsIfNeeded();
		refreshConfigCacheIfNeeded();
		if (targetSet.isEmpty()) {
			return;
		}

		// Keep scan radius modest — full render distance is what made this laggy.
		int viewDist = Math.min(8, Math.max(3, mc().options.getEffectiveRenderDistance()));
		int playerChunkX = SectionPos.blockToSectionCoord(mc().player.getBlockX());
		int playerChunkZ = SectionPos.blockToSectionCoord(mc().player.getBlockZ());
		Level level = mc().level;

		purgeDistantChunks(playerChunkX, playerChunkZ, viewDist);
		queueMissingChunks(level, playerChunkX, playerChunkZ, viewDist);
		scanQueuedChunks(level, CHUNKS_PER_TICK);

		if (renderListDirty) {
			if (renderRebuildCooldown > 0) {
				renderRebuildCooldown--;
			} else {
				rebuildRenderList();
				renderRebuildCooldown = 2;
			}
		}
	}

	private void onRender3D(Render3DEvent event) {
		if (mc().level == null || mc().player == null || renderCount == 0) {
			return;
		}

		RenderUtil.beginFilledBoxes(event.getContext());
		for (int i = 0; i < renderCount; i++) {
			long packed = renderPositions[i];
			BlockEspConfig config = configFor(renderBlocks[i]);
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			AABB box = new AABB(x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98);
			switch (config.getShapeMode()) {
				case LINES -> RenderUtil.addBox(box, config.getLineColor());
				case SIDES -> RenderUtil.addFilledBox(box, config.getSideColor());
				case BOTH -> {
					RenderUtil.addFilledBox(box, config.getSideColor());
					RenderUtil.addBox(box, config.getLineColor());
				}
			}
		}
		RenderUtil.endFilledBoxes();
	}

	private void onRender2D(Render2DEvent event) {
		if (!tracers.getValue() || mc().level == null || mc().player == null || mc().options.hideGui || renderCount == 0) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		float cursorX = mc().getWindow().getGuiScaledWidth() * 0.5f;
		float cursorY = mc().getWindow().getGuiScaledHeight() * 0.5f;
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);

		for (int i = 0; i < renderCount; i++) {
			BlockEspConfig config = configFor(renderBlocks[i]);
			if (!config.isTracer()) {
				continue;
			}
			long packed = renderPositions[i];
			Vec3 target = new Vec3(
				BlockPos.getX(packed) + 0.5,
				BlockPos.getY(packed) + 0.5,
				BlockPos.getZ(packed) + 0.5
			);
			float[] screen = WorldToScreen.project(target, tickDelta);
			if (screen == null) {
				continue;
			}
			Render2DUtil.drawLine(context, cursorX, cursorY, screen[0], screen[1], config.getTracerColor(), 1.0f);
		}
	}

	private void refreshTargetsIfNeeded() {
		int fingerprint = blocks.getValue().hashCode();
		if (fingerprint == targetsFingerprint) {
			return;
		}
		targetsFingerprint = fingerprint;
		// Identity set — Block instances are singletons from the registry.
		targetSet = Set.copyOf(blocks.getValue());
		clearChunkData();
	}

	private void refreshConfigCacheIfNeeded() {
		int fingerprint = System.identityHashCode(defaultBlockConfig.getValue()) * 31 + blockConfigs.getValue().hashCode();
		if (fingerprint == configFingerprint) {
			return;
		}
		configFingerprint = fingerprint;
		Map<Block, BlockEspConfig> next = new IdentityHashMap<>();
		for (Block block : targetSet) {
			BlockEspConfig custom = blockConfigs.get(block);
			next.put(block, custom != null ? custom : defaultBlockConfig.getValue());
		}
		configCache = next;
	}

	private void purgeDistantChunks(int playerChunkX, int playerChunkZ, int viewDist) {
		Iterator<Map.Entry<Long, ChunkCache>> iterator = chunkCaches.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Long, ChunkCache> entry = iterator.next();
			ChunkCache cache = entry.getValue();
			if (Math.abs(cache.chunkX - playerChunkX) > viewDist || Math.abs(cache.chunkZ - playerChunkZ) > viewDist) {
				iterator.remove();
				queued.remove(entry.getKey());
				renderListDirty = true;
			}
		}
	}

	private void queueMissingChunks(Level level, int playerChunkX, int playerChunkZ, int viewDist) {
		// Expanding ring so nearby chunks are queued (and scanned) first — no full-list sort each tick.
		for (int radius = 0; radius <= viewDist; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (radius != 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int chunkX = playerChunkX + dx;
					int chunkZ = playerChunkZ + dz;
					if (!level.hasChunk(chunkX, chunkZ)) {
						continue;
					}
					long key = ChunkPos.pack(chunkX, chunkZ);
					if (!chunkCaches.containsKey(key) && queued.add(key)) {
						scanQueue.add(key);
					}
				}
			}
		}
	}

	private void scanQueuedChunks(Level level, int budget) {
		int scanned = 0;
		while (scanned < budget && !scanQueue.isEmpty()) {
			long key = scanQueue.removeFirst();
			queued.remove(key);
			int chunkX = ChunkPos.getX(key);
			int chunkZ = ChunkPos.getZ(key);
			if (!level.hasChunk(chunkX, chunkZ)) {
				continue;
			}
			chunkCaches.put(key, scanChunk(level.getChunk(chunkX, chunkZ)));
			renderListDirty = true;
			scanned++;
		}
	}

	private ChunkCache scanChunk(LevelChunk chunk) {
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();
		List<Long> positions = new ArrayList<>(32);
		List<Block> foundBlocks = new ArrayList<>(32);
		Map<Block, Integer> perBlockCounts = new IdentityHashMap<>();

		Level level = mc().level;
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = level.getMinSectionY();
		int blockMinX = chunk.getPos().getMinBlockX();
		int blockMinZ = chunk.getPos().getMinBlockZ();

		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			LevelChunkSection section = sections[sectionIndex];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			if (!section.maybeHas(state -> {
				Block block = state.getBlock();
				return !state.isAir() && targetSet.contains(block);
			})) {
				continue;
			}

			int sectionMinY = SectionPos.sectionToBlockCoord(minSectionY + sectionIndex);
			for (int localY = 0; localY < 16; localY++) {
				int y = sectionMinY + localY;
				for (int localZ = 0; localZ < 16; localZ++) {
					int z = blockMinZ + localZ;
					for (int localX = 0; localX < 16; localX++) {
						BlockState state = section.getBlockState(localX, localY, localZ);
						if (state.isAir()) {
							continue;
						}
						Block block = state.getBlock();
						if (!targetSet.contains(block)) {
							continue;
						}

						int count = perBlockCounts.getOrDefault(block, 0);
						if (count >= MAX_PER_BLOCK_PER_CHUNK) {
							continue;
						}
						perBlockCounts.put(block, count + 1);
						positions.add(BlockPos.asLong(blockMinX + localX, y, z));
						foundBlocks.add(block);
					}
				}
			}
		}

		long[] packed = new long[positions.size()];
		Block[] types = new Block[foundBlocks.size()];
		for (int i = 0; i < packed.length; i++) {
			packed[i] = positions.get(i);
			types[i] = foundBlocks.get(i);
		}
		return new ChunkCache(chunkX, chunkZ, packed, types);
	}

	private void rebuildRenderList() {
		List<Highlight> all = new ArrayList<>();
		for (ChunkCache cache : chunkCaches.values()) {
			for (int i = 0; i < cache.positions.length; i++) {
				all.add(new Highlight(cache.positions[i], cache.blocks[i]));
			}
		}

		if (all.isEmpty()) {
			renderPositions = new long[0];
			renderBlocks = new Block[0];
			renderCount = 0;
			renderListDirty = false;
			return;
		}

		double px = mc().player.getX();
		double py = mc().player.getY();
		double pz = mc().player.getZ();
		all.sort(Comparator.comparingDouble(h -> {
			double dx = BlockPos.getX(h.pos) + 0.5 - px;
			double dy = BlockPos.getY(h.pos) + 0.5 - py;
			double dz = BlockPos.getZ(h.pos) + 0.5 - pz;
			return dx * dx + dy * dy + dz * dz;
		}));

		int limit = Math.min(MAX_RENDER, all.size());
		if (renderPositions.length < limit) {
			renderPositions = new long[limit];
			renderBlocks = new Block[limit];
		}
		for (int i = 0; i < limit; i++) {
			Highlight h = all.get(i);
			renderPositions[i] = h.pos;
			renderBlocks[i] = h.block;
		}
		renderCount = limit;
		renderListDirty = false;
	}

	private void invalidateChunk(long key) {
		chunkCaches.remove(key);
		if (queued.add(key)) {
			scanQueue.add(key);
		}
		renderListDirty = true;
	}

	private void clearChunkData() {
		chunkCaches.clear();
		queued.clear();
		scanQueue.clear();
		renderPositions = new long[0];
		renderBlocks = new Block[0];
		renderCount = 0;
		renderListDirty = true;
		renderRebuildCooldown = 0;
	}

	@Override
	protected void onEnable() {
		targetsFingerprint = Integer.MIN_VALUE;
		configFingerprint = Integer.MIN_VALUE;
		clearChunkData();
	}

	@Override
	protected void onDisable() {
		clearChunkData();
		targetSet = Set.of();
		configCache = Map.of();
		targetsFingerprint = Integer.MIN_VALUE;
		configFingerprint = Integer.MIN_VALUE;
	}

	private record ChunkCache(int chunkX, int chunkZ, long[] positions, Block[] blocks) {
	}

	private record Highlight(long pos, Block block) {
	}
}
