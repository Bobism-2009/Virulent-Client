package dev.virulent.client.module.modules.render;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BlockListSetting;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

public final class WallHack extends Module {
	private static WallHack instance;

	private final NumberSetting opacity = addSetting(new NumberSetting("Opacity", 0.0, 0.0, 255.0, 1.0));
	private final BlockListSetting blocks = addSetting(new BlockListSetting("Blocks"));
	private final BooleanSetting occludeChunks = addSetting(new BooleanSetting("Occlude Chunks", false));

	public WallHack() {
		super("WallHack", "Makes blocks translucent.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
		opacity.onChange(value -> reloadChunksIfActive());
		blocks.onChange(value -> reloadChunksIfActive());
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static boolean shouldOccludeChunks() {
		return !isActive() || instance.occludeChunks.getValue();
	}

	/**
	 * @return alpha 0..255 for wallhacked blocks, or {@code -1} for no change
	 */
	public static int getAlpha(BlockState state) {
		if (!isActive() || state == null || state.isAir()) {
			return -1;
		}
		Block block = state.getBlock();
		if (!instance.blocks.contains(block)) {
			return -1;
		}
		return instance.opacity.getValue().intValue();
	}

	public BlockListSetting getBlocks() {
		return blocks;
	}

	@Override
	protected void onEnable() {
		reloadChunks();
	}

	@Override
	protected void onDisable() {
		reloadChunks();
	}

	private void reloadChunksIfActive() {
		if (isEnabled()) {
			reloadChunks();
		}
	}

	private void reloadChunks() {
		var client = mc();
		if (client != null && client.levelRenderer != null) {
			client.levelRenderer.allChanged();
		}
	}
}
