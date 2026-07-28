package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.movement.Jesus;
import dev.virulent.client.module.modules.render.Xray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateMixin {
	@Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
	private void virulent$xrayHideBlocks(CallbackInfoReturnable<RenderShape> cir) {
		BlockState state = (BlockState) (Object) this;
		if (!Xray.shouldRender(state)) {
			cir.setReturnValue(RenderShape.INVISIBLE);
		}
	}

	@Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true)
	private void virulent$xrayNoOcclude(CallbackInfoReturnable<Boolean> cir) {
		BlockState state = (BlockState) (Object) this;
		if (!Xray.shouldRender(state)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isSolidRender", at = @At("HEAD"), cancellable = true)
	private void virulent$xrayNotSolid(CallbackInfoReturnable<Boolean> cir) {
		BlockState state = (BlockState) (Object) this;
		if (!Xray.shouldRender(state)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(
		method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void virulent$jesusSolid(
		BlockGetter level,
		BlockPos pos,
		CollisionContext context,
		CallbackInfoReturnable<VoxelShape> cir
	) {
		BlockState state = (BlockState) (Object) this;
		if (Jesus.shouldSolidify(state, context)) {
			cir.setReturnValue(Shapes.block());
		}
	}
}
