package dev.virulent.client.seed;

import net.minecraft.world.level.ChunkPos;

public record StructureHit(String name, ChunkPos chunk, int color) {
	public int blockX() {
		return chunk.getMiddleBlockX();
	}

	public int blockZ() {
		return chunk.getMiddleBlockZ();
	}

	public int distanceBlocks(double x, double z) {
		double dx = blockX() - x;
		double dz = blockZ() - z;
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}
}
