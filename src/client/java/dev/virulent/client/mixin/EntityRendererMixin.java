package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.render.Fullbright;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
	private int virulent$fullbrightEntity(int original, Entity entity, float partialTick) {
		return Fullbright.boostPackedLight(original);
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void virulent$fullbrightRenderState(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		state.lightCoords = Fullbright.boostPackedLight(state.lightCoords);
	}
}
