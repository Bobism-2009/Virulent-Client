package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.render.Fullbright;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Lightmap.class)
public class LightmapMixin {
	@ModifyReturnValue(method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F", at = @At("RETURN"))
	private static float virulent$fullbrightCurve(float original, DimensionType dimensionType, int lightLevel) {
		return Fullbright.lift(original);
	}
}
