package dev.virulent.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.virulent.client.module.modules.performance.FpsBooster;
import dev.virulent.client.module.modules.render.NoHurtCam;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void virulent$noHurtCam(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
		if (NoHurtCam.isActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void virulent$noTotemAnimation(ItemStack itemStack, CallbackInfo ci) {
		if (FpsBooster.hideTotemAnimation() && itemStack.is(Items.TOTEM_OF_UNDYING)) {
			ci.cancel();
		}
	}
}
