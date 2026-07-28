package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.performance.FpsBooster;
import dev.virulent.client.module.modules.render.NoWeather;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public class LevelMixin {
	@ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
	private float virulent$noRain(float original) {
		return NoWeather.isActive() || FpsBooster.hideWeather() ? 0.0f : original;
	}

	@ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
	private float virulent$noThunder(float original) {
		return NoWeather.isActive() || FpsBooster.hideWeather() ? 0.0f : original;
	}
}
