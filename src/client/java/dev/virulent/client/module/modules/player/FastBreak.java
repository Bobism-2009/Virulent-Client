package dev.virulent.client.module.modules.player;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

public final class FastBreak extends Module {
	private final NumberSetting haste = addSetting(new NumberSetting("Haste", 1.0, 1.0, 5.0, 1.0));

	public FastBreak() {
		super("FastBreak", "Break blocks faster.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player == null) {
			return;
		}

		int amplifier = Math.max(0, haste.getValue().intValue() - 1);
		mc().player.addEffect(new MobEffectInstance(MobEffects.HASTE, 2, amplifier, false, false, false));
	}
}
