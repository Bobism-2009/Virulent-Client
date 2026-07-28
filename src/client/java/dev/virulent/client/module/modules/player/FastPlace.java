package dev.virulent.client.module.modules.player;

import dev.virulent.client.mixin.MinecraftAccessor;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class FastPlace extends Module {
	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 0.0, 0.0, 4.0, 1.0));

	public FastPlace() {
		super("FastPlace", "Places blocks faster.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player != null) {
			((MinecraftAccessor) mc()).setRightClickDelay(delay.getValue().intValue());
		}
	}
}
