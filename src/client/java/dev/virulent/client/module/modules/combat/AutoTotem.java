package dev.virulent.client.module.modules.combat;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class AutoTotem extends Module {
	private static final int OFFHAND_SWAP_BUTTON = 40;

	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));
	private final BooleanSetting soft = addSetting(new BooleanSetting("Soft", false));
	private final NumberSetting health = addSetting(new NumberSetting("Health", 10.0, 1.0, 20.0, 0.5));

	private int cooldown;

	public AutoTotem() {
		super("AutoTotem", "Keeps a totem of undying in your offhand.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	protected void onDisable() {
		cooldown = 0;
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().gameMode == null) {
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		if (!(mc().player.containerMenu instanceof InventoryMenu)) {
			return;
		}

		ItemStack offhand = mc().player.getOffhandItem();
		if (offhand.is(Items.TOTEM_OF_UNDYING)) {
			return;
		}

		if (soft.getValue() && mc().player.getHealth() + mc().player.getAbsorptionAmount() > health.getValue()) {
			return;
		}

		int invSlot = findTotemSlot();
		if (invSlot == -1) {
			return;
		}

		int containerSlot = toContainerSlot(invSlot);
		mc().gameMode.handleContainerInput(
			mc().player.containerMenu.containerId,
			containerSlot,
			OFFHAND_SWAP_BUTTON,
			ContainerInput.SWAP,
			mc().player
		);
		cooldown = delay.getValue().intValue();
	}

	private int findTotemSlot() {
		Inventory inventory = mc().player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.TOTEM_OF_UNDYING)) {
				return slot;
			}
		}
		return -1;
	}

	private static int toContainerSlot(int inventorySlot) {
		if (Inventory.isHotbarSlot(inventorySlot)) {
			return InventoryMenu.USE_ROW_SLOT_START + inventorySlot;
		}
		return inventorySlot;
	}
}
