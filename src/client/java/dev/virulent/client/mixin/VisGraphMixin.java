package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.render.WallHack;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public abstract class VisGraphMixin {
	@Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
	private void virulent$chunkOcclusion(BlockPos pos, CallbackInfo ci) {
		if (!WallHack.shouldOccludeChunks()) {
			ci.cancel();
		}
	}
}
