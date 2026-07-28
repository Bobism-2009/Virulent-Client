package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.render.NoWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void virulent$noWeatherExtract(Level level, int ticks, float partialTick, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (NoWeather.isActive()) {
			state.reset();
			ci.cancel();
		}
	}

	@Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
	private void virulent$noWeatherParticles(ClientLevel level, Camera camera, int ticks, ParticleStatus status, int range, CallbackInfo ci) {
		if (NoWeather.isActive()) {
			ci.cancel();
		}
	}
}
