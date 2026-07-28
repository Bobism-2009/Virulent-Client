package dev.virulent.client.seed;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Locates random-spread structures from a known world seed using vanilla placement math.
 * Results are candidate chunks (biome/exclusion filters may still reject some).
 */
public final class StructureLocator {
	private static final Map<String, Integer> COLORS = new HashMap<>();

	static {
		COLORS.put("village", 0xFF55FF55);
		COLORS.put("desert_pyramid", 0xFFE8D48B);
		COLORS.put("jungle_temple", 0xFF2E8B57);
		COLORS.put("jungle_pyramid", 0xFF2E8B57);
		COLORS.put("swamp_hut", 0xFF9B59B6);
		COLORS.put("igloo", 0xFFEEFFFF);
		COLORS.put("shipwreck", 0xFF8B4513);
		COLORS.put("ocean_ruin", 0xFF5DADE2);
		COLORS.put("monument", 0xFF1ABC9C);
		COLORS.put("mansion", 0xFF8E44AD);
		COLORS.put("pillager_outpost", 0xFFC0392B);
		COLORS.put("ruined_portal", 0xFF9B59B6);
		COLORS.put("buried_treasure", 0xFFF1C40F);
		COLORS.put("mineshaft", 0xFF7F8C8D);
		COLORS.put("stronghold", 0xFFE74C3C);
		COLORS.put("trail_ruins", 0xFFD35400);
		COLORS.put("trial_chambers", 0xFF3498DB);
		COLORS.put("ancient_city", 0xFF2C3E50);
		COLORS.put("fortress", 0xFFE74C3C);
		COLORS.put("bastion", 0xFFD35400);
		COLORS.put("end_city", 0xFFBB8FCE);
	}

	private StructureLocator() {
	}

	public static List<StructureHit> locate(Level level, long seed, int centerChunkX, int centerChunkZ, int radiusChunks) {
		Optional<Registry<StructureSet>> registry = level.registryAccess().lookup(Registries.STRUCTURE_SET);
		if (registry.isEmpty()) {
			return List.of();
		}

		List<StructureHit> hits = new ArrayList<>();
		for (var entry : registry.get().listElements().toList()) {
			ResourceKey<StructureSet> key = entry.key();
			StructureSet set = entry.value();
			StructurePlacement placement = set.placement();
			if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
				continue;
			}

			String name = shortName(key.identifier());
			int color = colorFor(name);
			int spacing = Math.max(1, spread.spacing());
			int regionRadius = (radiusChunks / spacing) + 2;

			for (int rx = -regionRadius; rx <= regionRadius; rx++) {
				for (int rz = -regionRadius; rz <= regionRadius; rz++) {
					int probeX = centerChunkX + rx * spacing;
					int probeZ = centerChunkZ + rz * spacing;
					ChunkPos chunk = spread.getPotentialStructureChunk(seed, probeX, probeZ);
					int cx = chunkX(chunk);
					int cz = chunkZ(chunk);
					int dx = cx - centerChunkX;
					int dz = cz - centerChunkZ;
					if (dx * dx + dz * dz > radiusChunks * radiusChunks) {
						continue;
					}
					// Approximate: skip protected frequency helpers; candidates may include extras.
					hits.add(new StructureHit(name, chunk, color));
				}
			}
		}

		hits.sort(Comparator.comparingInt(hit -> {
			int dx = chunkX(hit.chunk()) - centerChunkX;
			int dz = chunkZ(hit.chunk()) - centerChunkZ;
			return dx * dx + dz * dz;
		}));
		return hits;
	}

	public static int chunkX(ChunkPos chunk) {
		return chunk.getMinBlockX() >> 4;
	}

	public static int chunkZ(ChunkPos chunk) {
		return chunk.getMinBlockZ() >> 4;
	}

	private static String shortName(Identifier id) {
		String path = id.getPath();
		int slash = path.lastIndexOf('/');
		return slash >= 0 ? path.substring(slash + 1) : path;
	}

	private static int colorFor(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, Integer> entry : COLORS.entrySet()) {
			if (lower.contains(entry.getKey())) {
				return entry.getValue();
			}
		}
		return 0xFF4CFF66;
	}
}
