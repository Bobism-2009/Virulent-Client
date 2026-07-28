package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.render.Fullbright;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium bakes lightmap coords into meshes. At night, high sky-light samples the
 * darkened sky axis of the lightmap — so we boost via the block-light axis instead.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
public class SodiumLightDataAccessMixin {
	@ModifyReturnValue(method = "getLightmap", at = @At("RETURN"))
	private static int virulent$fullbright(int original) {
		float factor = Fullbright.brightnessFactor();
		if (factor <= 0.0f) {
			return original;
		}

		return Fullbright.boostPackedLight(original);
	}

	@ModifyReturnValue(method = "packAO", at = @At("RETURN"))
	private static int virulent$fullbrightAo(int packed) {
		float factor = Fullbright.brightnessFactor();
		if (factor <= 0.0f) {
			return packed;
		}

		// unpackAO equivalent then re-pack toward 1.0
		int raw = (packed >>> 12) & 65535;
		float ao = raw * (1.0f / 4096.0f);
		float lifted = Mth.lerp(factor, ao, 1.0f);
		int out = Mth.clamp((int) (lifted * 4096.0f), 0, 65535);
		return out << 12;
	}
}
