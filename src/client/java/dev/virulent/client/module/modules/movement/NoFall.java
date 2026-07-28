package dev.virulent.client.module.modules.movement;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

public final class NoFall extends Module {
	private static NoFall instance;
	private static boolean spoofGround;

	public NoFall() {
		super("NoFall", "Prevents fall damage by spoofing ground state.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static void beginSpoof() {
		spoofGround = false;
		if (!isActive()) {
			return;
		}

		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		// Read real ground state before enabling the onGround redirect.
		spoofGround = !player.onGround() || player.fallDistance > 0.0f;
	}

	public static void endSpoof() {
		spoofGround = false;
	}

	public static boolean shouldSpoofGround(Entity entity) {
		if (!spoofGround || !isActive()) {
			return false;
		}
		return entity == Minecraft.getInstance().player;
	}

	@Override
	public void onTick() {
		var player = mc().player;
		if (player == null) {
			return;
		}

		player.fallDistance = 0.0f;
	}
}
