package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.combat.Velocity;
import dev.virulent.client.seed.SeedState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void virulent$hashedSeedLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
		SeedState.get().setHashedSeed(packet.commonPlayerSpawnInfo().seed());
	}

	@Inject(method = "handleRespawn", at = @At("TAIL"))
	private void virulent$hashedSeedRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
		SeedState.get().setHashedSeed(packet.commonPlayerSpawnInfo().seed());
	}

	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
	private void virulent$velocity(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		if (!Velocity.isActive()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || packet.id() != client.player.getId()) {
			return;
		}

		if (Velocity.cancelsKnockback()) {
			ci.cancel();
			return;
		}

		// Scale only the knockback delta vs current motion (packet.movement is already world units).
		Vec3 packetVel = packet.movement();
		Vec3 current = client.player.getDeltaMovement();
		double horizontal = Velocity.horizontal();
		double vertical = Velocity.vertical();
		client.player.setDeltaMovement(
			current.x + (packetVel.x - current.x) * horizontal,
			current.y + (packetVel.y - current.y) * vertical,
			current.z + (packetVel.z - current.z) * horizontal
		);
		ci.cancel();
	}
}
