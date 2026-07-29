package dev.virulent.client.module.modules.combat;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.mixin.ClientLevelAccessor;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Port of Trouser-Streak CrossbowMachineGun (by agreed).
 * Hold right-click with a crossbow to fire rapidly.
 */
public final class CrossbowMachineGun extends Module {
	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 0.0, 0.0, 20.0, 1.0));
	private final BooleanSetting correctSequence = addSetting(new BooleanSetting("Correct Sequence", true));

	private int timer;

	public CrossbowMachineGun() {
		super(
			"CrossbowMachineGun",
			"Turns your crossbow into a machine gun. Hold right click to fire.",
			Category.COMBAT,
			GLFW.GLFW_KEY_UNKNOWN
		);
	}

	@Override
	protected void onDisable() {
		timer = 0;
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().getConnection() == null) {
			return;
		}

		int delayTicks = delay.getValue().intValue();
		if (delayTicks > 0) {
			if (timer++ < delayTicks) {
				return;
			}
			timer = 0;
		}

		boolean mainCrossbow = mc().player.getMainHandItem().is(Items.CROSSBOW);
		boolean offCrossbow = mc().player.getOffhandItem().is(Items.CROSSBOW);
		if ((!mainCrossbow && !offCrossbow) || !mc().options.keyUse.isDown()) {
			return;
		}

		InteractionHand hand = mainCrossbow ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		int sequence = 0;
		if (correctSequence.getValue() && mc().level instanceof ClientLevel clientLevel) {
			sequence = ((ClientLevelAccessor) clientLevel).virulent$getBlockStatePredictionHandler().currentSequence();
		}

		mc().getConnection().send(new ServerboundUseItemPacket(
			hand,
			sequence,
			mc().player.getYRot(),
			mc().player.getXRot()
		));
	}
}
