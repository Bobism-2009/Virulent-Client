package dev.virulent.client.module.modules.combat;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class AutoTotem extends Module {
	private static final int OFFHAND_SWAP_BUTTON = 40;

	private static AutoTotem instance;

	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));
	private final BooleanSetting soft = addSetting(new BooleanSetting("Soft", false));
	private final NumberSetting health = addSetting(new NumberSetting("Health", 10.0, 1.0, 20.0, 0.5));
	private final BooleanSetting bypass = addSetting(new BooleanSetting("Bypass", true));

	private int cooldown;

	public AutoTotem() {
		super("AutoTotem", "Keeps a totem of undying in your offhand.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static boolean shouldBypass() {
		return isActive() && instance.bypass.getValue();
	}

	@Override
	protected void onDisable() {
		cooldown = 0;
	}

	@Override
	public void onTick() {
		trySwap(false);
	}

	/**
	 * Called from the packet listener the instant the server tells us our own totem
	 * popped. If we send the swap right now the packet reaches the server before the
	 * next tick, so a fresh totem is in the offhand before the next damage event —
	 * this is what makes "totem bypass" (multiple pops in a chain) actually work.
	 */
	public static void onTotemPopped() {
		if (!shouldBypass()) {
			return;
		}
		instance.trySwap(true);
	}

	private void trySwap(boolean force) {
		Minecraft mc = mc();
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}

		if (!force && cooldown > 0) {
			cooldown--;
			return;
		}

		if (!(mc.player.containerMenu instanceof InventoryMenu)) {
			return;
		}

		ItemStack offhand = mc.player.getOffhandItem();
		if (offhand.is(Items.TOTEM_OF_UNDYING)) {
			return;
		}

		if (!force && soft.getValue()
			&& mc.player.getHealth() + mc.player.getAbsorptionAmount() > health.getValue()) {
			return;
		}

		int invSlot = findTotemSlot();
		if (invSlot == -1) {
			return;
		}

		int containerSlot = toContainerSlot(invSlot);
		mc.gameMode.handleContainerInput(
			mc.player.containerMenu.containerId,
			containerSlot,
			OFFHAND_SWAP_BUTTON,
			ContainerInput.SWAP,
			mc.player
		);
		// Optimistically mirror the swap client-side so a second bypass call in the
		// same tick doesn't pick the same source slot again.
		Inventory inv = mc.player.getInventory();
		ItemStack source = inv.getItem(invSlot);
		inv.setItem(invSlot, offhand);
		inv.setItem(Inventory.SLOT_OFFHAND, source);

		if (!force) {
			cooldown = delay.getValue().intValue();
		}
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
