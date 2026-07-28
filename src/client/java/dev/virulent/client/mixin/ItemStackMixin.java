package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.virulent.client.module.modules.performance.FpsBooster;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	@ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
	private boolean virulent$noEnchantmentGlint(boolean original) {
		return original && !FpsBooster.hideEnchantmentGlint();
	}
}
