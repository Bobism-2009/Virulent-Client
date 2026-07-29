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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Highlights invisible barrier blocks through walls.
 */
public final class BarrierEsp extends Module {
	private static final int CHUNKS_PER_TICK = 4;
	private static final int MAX_RENDER = 1024;
	private static final int LINE_COLOR = 0xFFFF55FF;
	private static final int SIDE_COLOR = 0x55FF55FF;
	private static final int TRACER_COLOR = 0xFFFF55FF;

	private static BarrierEsp instance;

	private final ModeSetting shape = addSetting(new ModeSetting("Shape", "Both", "Lines", "Sides", "Both"));
	private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", false));
	private final NumberSetting range = addSetting(new NumberSetting("Chunk Range", 6.0, 2.0, 12.0, 1.0));

	private final Map<Long, ChunkCache> chunkCaches = new HashMap<>();
	private final Set<Long> queued = new HashSet<>();
	private final List<Long> scanQueue = new ArrayList<>();

	private long[] renderPositions = new long[0];
	private int renderCount;
	private boolean renderListDirty = true;
	private int renderRebuildCooldown;

	public BarrierEsp() {
		super("BarrierESP", "Shows where barrier blocks are through walls.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
		subscribe(Render3DEvent.class, this::onRender3D);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
		BarrierEsp self = instance;
		if (self == null || !self.isEnabled()) {
			return;
		}
		boolean wasBarrier = oldState.is(Blocks.BARRIER);
		boolean isBarrier = newState.is(Blocks.BARRIER);
		if (!wasBarrier && !isBarrier) {
			return;
		}
		self.invalidateChunk(ChunkPos.pack(pos));
	}

	public boolean needsWorldRender() {
		return isEnabled();
	}

	@Override
	public void onTick() {
		if (mc().level == null || mc().player == null) {
			return;
		}

		int viewDist = Math.min(range.getValue().intValue(), Math.max(2, mc().options.getEffectiveRenderDistance()));
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

		String mode = shape.getValue();
		RenderUtil.beginFilledBoxes(event.getContext());
		for (int i = 0; i < renderCount; i++) {
			long packed = renderPositions[i];
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			AABB box = new AABB(x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98);
			switch (mode) {
				case "Lines" -> RenderUtil.addBox(box, LINE_COLOR);
				case "Sides" -> RenderUtil.addFilledBox(box, SIDE_COLOR);
				default -> {
					RenderUtil.addFilledBox(box, SIDE_COLOR);
					RenderUtil.addBox(box, LINE_COLOR);
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
			Render2DUtil.drawLine(context, cursorX, cursorY, screen[0], screen[1], TRACER_COLOR, 1.0f);
		}
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
		List<Long> positions = new ArrayList<>(16);

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
			// Barriers are not air, but some anti-xray / empty sections still skip — maybeHas catches them.
			if (!section.maybeHas(state -> state.is(Blocks.BARRIER))) {
				continue;
			}

			int sectionMinY = SectionPos.sectionToBlockCoord(minSectionY + sectionIndex);
			for (int localY = 0; localY < 16; localY++) {
				int y = sectionMinY + localY;
				for (int localZ = 0; localZ < 16; localZ++) {
					int z = blockMinZ + localZ;
					for (int localX = 0; localX < 16; localX++) {
						BlockState state = section.getBlockState(localX, localY, localZ);
						if (!state.is(Blocks.BARRIER)) {
							continue;
						}
						positions.add(BlockPos.asLong(blockMinX + localX, y, z));
					}
				}
			}
		}

		long[] packed = new long[positions.size()];
		for (int i = 0; i < packed.length; i++) {
			packed[i] = positions.get(i);
		}
		return new ChunkCache(chunkX, chunkZ, packed);
	}

	private void rebuildRenderList() {
		List<Long> all = new ArrayList<>();
		for (ChunkCache cache : chunkCaches.values()) {
			for (long pos : cache.positions) {
				all.add(pos);
			}
		}

		if (all.isEmpty()) {
			renderPositions = new long[0];
			renderCount = 0;
			renderListDirty = false;
			return;
		}

		double px = mc().player.getX();
		double py = mc().player.getY();
		double pz = mc().player.getZ();
		all.sort(Comparator.comparingDouble(pos -> {
			double dx = BlockPos.getX(pos) + 0.5 - px;
			double dy = BlockPos.getY(pos) + 0.5 - py;
			double dz = BlockPos.getZ(pos) + 0.5 - pz;
			return dx * dx + dy * dy + dz * dz;
		}));

		int limit = Math.min(MAX_RENDER, all.size());
		if (renderPositions.length < limit) {
			renderPositions = new long[limit];
		}
		for (int i = 0; i < limit; i++) {
			renderPositions[i] = all.get(i);
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
		renderCount = 0;
		renderListDirty = true;
		renderRebuildCooldown = 0;
	}

	@Override
	protected void onEnable() {
		clearChunkData();
	}

	@Override
	protected void onDisable() {
		clearChunkData();
	}

	private record ChunkCache(int chunkX, int chunkZ, long[] positions) {
	}
}
