package dev.virulent.client.module.modules.player;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.CombatUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Port of Meteor Client EXPThrower — throws XP bottles while looking down.
 */
public final class XPThrow extends Module {
	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));

	private int timer;

	public XPThrow() {
		super("XPThrow", "Automatically throws XP bottles from your hotbar.", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	protected void onDisable() {
		timer = 0;
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().gameMode == null) {
			return;
		}

		if (timer > 0) {
			timer--;
			return;
		}

		HotbarXp found = findXpBottle();
		if (found == null) {
			return;
		}

		// Look straight down so bottles land on you (mending repair).
		CombatUtil.applyRotations(mc().player, mc().player.getYRot(), 90.0f);

		if (found.hand != null) {
			mc().gameMode.useItem(mc().player, found.hand);
		} else {
			Inventory inv = mc().player.getInventory();
			int previous = inv.getSelectedSlot();
			inv.setSelectedSlot(found.slot);
			mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND);
			inv.setSelectedSlot(previous);
		}

		timer = delay.getValue().intValue();
	}

	private HotbarXp findXpBottle() {
		ItemStack main = mc().player.getMainHandItem();
		if (main.is(Items.EXPERIENCE_BOTTLE)) {
			return new HotbarXp(InteractionHand.MAIN_HAND, mc().player.getInventory().getSelectedSlot());
		}
		ItemStack off = mc().player.getOffhandItem();
		if (off.is(Items.EXPERIENCE_BOTTLE)) {
			return new HotbarXp(InteractionHand.OFF_HAND, -1);
		}

		Inventory inv = mc().player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			if (inv.getItem(slot).is(Items.EXPERIENCE_BOTTLE)) {
				return new HotbarXp(null, slot);
			}
		}
		return null;
	}

	private record HotbarXp(InteractionHand hand, int slot) {
	}
}
