package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.performance.FpsBooster;
import dev.virulent.client.module.modules.render.Fullbright;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
	private int virulent$fullbrightEntity(int original, Entity entity, float partialTick) {
		return Fullbright.boostPackedLight(original);
	}

	@ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
	private <E extends Entity> boolean virulent$cullDistantEntities(boolean original, E entity, Frustum frustum, double camX, double camY, double camZ) {
		if (!original || !FpsBooster.cullEntities() || entity instanceof Player) {
			return original;
		}
		Entity localPlayer = Minecraft.getInstance().player;
		if (localPlayer == null || entity == localPlayer) {
			return original;
		}
		double dx = entity.getX() - camX;
		double dy = entity.getY() - camY;
		double dz = entity.getZ() - camZ;
		double distSq = dx * dx + dy * dy + dz * dz;
		return distSq <= FpsBooster.cullDistanceSq();
	}
}
