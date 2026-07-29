package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.performance.FpsBooster;
import dev.virulent.client.module.modules.render.BarrierEsp;
import dev.virulent.client.module.modules.render.BlockEsp;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Unique
	private BlockState virulent$previousState;

	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("HEAD")
	)
	private void virulent$capturePrevious(
		BlockPos pos,
		BlockState state,
		int flags,
		int recursionLeft,
		CallbackInfoReturnable<Boolean> cir
	) {
		virulent$previousState = ((ClientLevel) (Object) this).getBlockState(pos);
	}

	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("RETURN")
	)
	private void virulent$blockEspInvalidate(
		BlockPos pos,
		BlockState state,
		int flags,
		int recursionLeft,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValue() || virulent$previousState == null) {
			virulent$previousState = null;
			return;
		}
		BlockEsp.onBlockChanged(pos, virulent$previousState, state);
		BarrierEsp.onBlockChanged(pos, virulent$previousState, state);
		virulent$previousState = null;
	}

	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void virulent$noDestroyParticles(BlockPos pos, BlockState blockState, CallbackInfo ci) {
		if (FpsBooster.hideBreakParticles()) {
			ci.cancel();
		}
	}

	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
	private void virulent$noBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
		if (FpsBooster.hideBreakParticles()) {
			ci.cancel();
		}
	}

	@Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
	private void virulent$noAmbientParticles(int posX, int posY, int posZ, CallbackInfo ci) {
		if (FpsBooster.hideAmbientParticles()) {
			ci.cancel();
		}
	}
}
