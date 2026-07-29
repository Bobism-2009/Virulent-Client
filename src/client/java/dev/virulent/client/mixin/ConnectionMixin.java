package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.combat.Criticals;
import dev.virulent.client.module.modules.combat.MaceKill;
import dev.virulent.client.module.modules.player.AntiHunger;
import dev.virulent.client.util.ServerRotations;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void virulent$onSend(Packet<?> packet, CallbackInfo ci) {
		ServerRotations.onOutgoing(packet);
		AntiHunger.onOutgoing(packet, ci);
		if (ci.isCancelled()) {
			return;
		}

		Criticals.onOutgoing(packet, ci);
		if (ci.isCancelled()) {
			return;
		}

		if (packet instanceof ServerboundAttackPacket attackPacket) {
			MaceKill.onAttackPacket(attackPacket, ci);
		}
	}
}
