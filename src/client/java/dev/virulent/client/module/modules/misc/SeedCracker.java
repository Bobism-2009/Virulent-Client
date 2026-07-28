package dev.virulent.client.module.modules.misc;

import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.seed.SeedState;
import dev.virulent.client.seed.StructureHit;
import dev.virulent.client.seed.StructureLocator;
import dev.virulent.client.setting.ActionSetting;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.waypoint.Waypoint;
import dev.virulent.client.waypoint.WaypointManager;
import dev.virulent.client.VirulentClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SeedCracker extends Module {
	private final ActionSetting manage = addSetting(new ActionSetting("Manage", "Open"));
	private final BooleanSetting esp = addSetting(new BooleanSetting("ESP", true));
	private final BooleanSetting hud = addSetting(new BooleanSetting("HUD", true));
	private final NumberSetting radius = addSetting(new NumberSetting("Radius", 200.0, 32.0, 1024.0, 16.0));
	private final NumberSetting maxShown = addSetting(new NumberSetting("Max Shown", 24.0, 4.0, 100.0, 1.0));
	private final BooleanSetting filterRare = addSetting(new BooleanSetting("Rare Only", true));

	private final List<StructureHit> hits = new ArrayList<>();
	private long lastScanMs;
	private ChunkPos lastScanChunk;
	private Long lastScanSeed;

	public SeedCracker() {
		super(
			"SeedCracker",
			"Locate structures from a known seed. Paste a seed or import from SeedcrackerX.",
			Category.MISC,
			GLFW.GLFW_KEY_UNKNOWN
		);
		subscribe(Render3DEvent.class, this::onRender3D);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	public ActionSetting getManageSetting() {
		return manage;
	}

	public List<StructureHit> getHits() {
		return hits;
	}

	public void rescan() {
		lastScanMs = 0;
		lastScanChunk = null;
		lastScanSeed = null;
		scanIfNeeded(true);
	}

	@Override
	public void onTick() {
		SeedState.get().syncFromGame();
		scanIfNeeded(false);
	}

	private void scanIfNeeded(boolean force) {
		if (mc().level == null || mc().player == null) {
			hits.clear();
			return;
		}
		SeedState state = SeedState.get();
		if (!state.hasWorldSeed()) {
			hits.clear();
			return;
		}

		long seed = state.getWorldSeed();
		ChunkPos chunk = mc().player.chunkPosition();
		long now = System.currentTimeMillis();
		int cx = StructureLocator.chunkX(chunk);
		int cz = StructureLocator.chunkZ(chunk);
		boolean moved = lastScanChunk == null
			|| Math.abs(cx - StructureLocator.chunkX(lastScanChunk)) >= 8
			|| Math.abs(cz - StructureLocator.chunkZ(lastScanChunk)) >= 8;
		boolean seedChanged = lastScanSeed == null || lastScanSeed != seed;
		if (!force && !seedChanged && !moved && now - lastScanMs < 2000) {
			return;
		}

		List<StructureHit> found = StructureLocator.locate(
			mc().level,
			seed,
			cx,
			cz,
			radius.getValue().intValue()
		);
		if (filterRare.getValue()) {
			found = found.stream().filter(SeedCracker::isInteresting).toList();
		}

		hits.clear();
		int limit = maxShown.getValue().intValue();
		for (int i = 0; i < found.size() && i < limit; i++) {
			hits.add(found.get(i));
		}
		lastScanMs = now;
		lastScanChunk = chunk;
		lastScanSeed = seed;
	}

	private static boolean isInteresting(StructureHit hit) {
		String name = hit.name().toLowerCase(Locale.ROOT);
		return name.contains("village")
			|| name.contains("pyramid")
			|| name.contains("temple")
			|| name.contains("hut")
			|| name.contains("igloo")
			|| name.contains("shipwreck")
			|| name.contains("monument")
			|| name.contains("mansion")
			|| name.contains("outpost")
			|| name.contains("ancient")
			|| name.contains("trial")
			|| name.contains("trail")
			|| name.contains("bastion")
			|| name.contains("fortress")
			|| name.contains("end_city")
			|| name.contains("ruined_portal")
			|| name.contains("treasure");
	}

	private void onRender3D(Render3DEvent event) {
		if (!esp.getValue() || hits.isEmpty() || mc().player == null) {
			return;
		}
		RenderUtil.beginLines(event.getContext());
		for (StructureHit hit : hits) {
			double x = hit.blockX() + 0.5;
			double z = hit.blockZ() + 0.5;
			double y = mc().player.getY();
			AABB box = new AABB(x - 4, y - 8, z - 4, x + 4, y + 32, z + 4);
			RenderUtil.addBox(box, hit.color());
			RenderUtil.addLine(new Vec3(x, y - 16, z), new Vec3(x, y + 64, z), hit.color());
		}
		RenderUtil.endLines();
	}

	private void onRender2D(Render2DEvent event) {
		if (!hud.getValue() || mc().options.hideGui) {
			return;
		}
		GuiGraphicsExtractor context = event.getContext();
		var font = mc().font;
		SeedState state = SeedState.get();
		int y = 48;
		if (state.hasWorldSeed()) {
			context.text(font, "Seed " + state.getWorldSeed() + " (" + state.getSource() + ")", 4, y, 0xFF88FF88);
		} else if (state.getHashedSeed() != null) {
			context.text(font, "Hashed seed " + state.getHashedSeed() + " — paste full seed", 4, y, 0xFFFFAA66);
		} else {
			context.text(font, "SeedCracker: no seed — open Manage", 4, y, 0xFFFF8888);
		}
		y += 10;
		if (mc().player != null) {
			for (int i = 0; i < Math.min(8, hits.size()); i++) {
				StructureHit hit = hits.get(i);
				int dist = hit.distanceBlocks(mc().player.getX(), mc().player.getZ());
				context.text(
					font,
					hit.name() + "  " + hit.blockX() + " " + hit.blockZ() + "  " + dist + "m",
					4,
					y,
					hit.color()
				);
				y += 10;
			}
		}
	}

	public int waypointNearby() {
		if (mc().player == null || mc().level == null || hits.isEmpty()) {
			return 0;
		}
		WaypointManager manager = VirulentClient.getInstance().getWaypointManager();
		String dim = manager.currentDimensionId();
		int added = 0;
		for (StructureHit hit : hits) {
			String name = hit.name() + " " + hit.blockX() + " " + hit.blockZ();
			boolean exists = false;
			for (Waypoint waypoint : manager.getWaypoints()) {
				if (waypoint.getName().equalsIgnoreCase(name)) {
					exists = true;
					break;
				}
			}
			if (exists) {
				continue;
			}
			manager.add(new Waypoint(
				name.length() > 24 ? name.substring(0, 24) : name,
				hit.blockX() + 0.5,
				mc().player.getY(),
				hit.blockZ() + 0.5,
				dim,
				hit.color()
			));
			added++;
		}
		return added;
	}
}
