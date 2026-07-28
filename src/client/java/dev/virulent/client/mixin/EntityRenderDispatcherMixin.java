package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.render.Fullbright;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
	private int virulent$fullbrightEntity(int original, Entity entity, float partialTick) {
		return Fullbright.boostPackedLight(original);
	}
}
