package dev.virulent.client.module.modules.movement;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

public final class SafeWalk extends Module {
	private static SafeWalk instance;

	private final BooleanSetting scaffoldOnly = addSetting(new BooleanSetting("Scaffold Only", false));

	public SafeWalk() {
		super("SafeWalk", "Prevents walking off block edges.", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean shouldStayOnGround(Entity entity) {
		if (instance == null || !instance.isEnabled()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || entity != client.player) {
			return false;
		}
		if (instance.scaffoldOnly.getValue()) {
			Module scaffold = VirulentClient.getInstance().getModuleManager().getModule("Scaffold");
			return scaffold != null && scaffold.isEnabled();
		}
		return true;
	}
}
