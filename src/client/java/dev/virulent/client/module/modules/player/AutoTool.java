package dev.virulent.client.module.modules.player;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

public final class AutoTool extends Module {
	public AutoTool() {
		super("AutoTool", "Switches to the best tool for the block you're mining.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().gameMode == null || !mc().options.keyAttack.isDown()) {
			return;
		}

		if (!(mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit)) {
			return;
		}

		BlockState state = mc().level.getBlockState(blockHit.getBlockPos());
		if (state.isAir()) {
			return;
		}

		int bestSlot = -1;
		float bestSpeed = 1.0f;

		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc().player.getInventory().getItem(slot);
			float speed = stack.getDestroySpeed(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = slot;
			}
		}

		if (bestSlot != -1) {
			mc().player.getInventory().setSelectedSlot(bestSlot);
		}
	}
}
