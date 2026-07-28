package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.render.Fullbright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
	@ModifyReturnValue(method = "calculateDarknessScale", at = @At("RETURN"))
	private float virulent$noDarkness(float original, LivingEntity entity, float gamma, float partialTick) {
		if (Fullbright.brightnessFactor() <= 0.0f) {
			return original;
		}
		return Mth.lerp(Fullbright.brightnessFactor(), original, 0.0f);
	}
}
