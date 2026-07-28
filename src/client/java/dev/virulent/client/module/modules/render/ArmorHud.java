package dev.virulent.client.module.modules.render;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ArmorHud extends Module {
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET
	};

	private static final int ICON = 16;
	private static final int PAD = 4;
	private static final int GAP = 3;
	private static final int BAR_H = 2;
	private static final int BAR_GAP = 3;
	private static final int TEXT_W = 26;

	private final ModeSetting orientation = addSetting(new ModeSetting("Orientation", "Vertical", "Vertical", "Horizontal"));
	private final ModeSetting position = addSetting(new ModeSetting(
		"Position",
		"Hotbar Right",
		"Hotbar Left",
		"Hotbar Right",
		"Top Right",
		"Bottom Right",
		"Top Left",
		"Bottom Left"
	));
	private final ModeSetting durability = addSetting(new ModeSetting("Durability", "Bar", "Bar", "Percent", "Both", "None"));
	private final BooleanSetting showEmpty = addSetting(new BooleanSetting("Show Empty", true));
	private final NumberSetting offsetX = addSetting(new NumberSetting("Offset X", 0.0, -200.0, 200.0, 1.0));
	private final NumberSetting offsetY = addSetting(new NumberSetting("Offset Y", 0.0, -200.0, 200.0, 1.0));

	public ArmorHud() {
		super("ArmorHud", "Shows equipped armor and durability on screen.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	private void onRender2D(Render2DEvent event) {
		if (mc().player == null || mc().options.hideGui) {
			return;
		}

		List<ItemStack> stacks = new ArrayList<>(4);
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = mc().player.getItemBySlot(slot);
			if (showEmpty.getValue() || !stack.isEmpty()) {
				stacks.add(stack);
			}
		}
		if (stacks.isEmpty()) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		int screenW = mc().getWindow().getGuiScaledWidth();
		int screenH = mc().getWindow().getGuiScaledHeight();
		boolean vertical = orientation.getValue().equals("Vertical");
		String durMode = durability.getValue();
		boolean showBar = durMode.equals("Bar") || durMode.equals("Both");
		boolean showPercent = durMode.equals("Percent") || durMode.equals("Both");
		int accent = VirulentClient.getInstance().getGuiSettings().getAccentColor() | 0xFF000000;

		int cellW;
		int cellH;
		if (vertical) {
			cellW = ICON + (showPercent ? TEXT_W + 4 : 0) + (showBar && !showPercent ? 0 : 0);
			if (showBar && showPercent) {
				cellW = ICON + 6 + 34 + TEXT_W;
			} else if (showBar) {
				cellW = ICON + 6 + 34;
			} else if (showPercent) {
				cellW = ICON + 6 + TEXT_W;
			} else {
				cellW = ICON;
			}
			cellH = ICON;
		} else {
			cellW = ICON;
			cellH = ICON + (showBar ? BAR_GAP + BAR_H : 0) + (showPercent ? 9 : 0);
		}

		int count = stacks.size();
		int innerW = vertical ? cellW : count * cellW + (count - 1) * GAP;
		int innerH = vertical ? count * cellH + (count - 1) * GAP : cellH;
		int panelW = innerW + PAD * 2;
		int panelH = innerH + PAD * 2;

		int[] origin = anchor(screenW, screenH, panelW, panelH);
		int panelX = origin[0] + offsetX.getValue().intValue();
		int panelY = origin[1] + offsetY.getValue().intValue();

		drawPanel(context, panelX, panelY, panelW, panelH, accent);

		int x = panelX + PAD;
		int y = panelY + PAD;
		for (ItemStack stack : stacks) {
			drawSlot(context, stack, x, y, cellW, cellH, vertical, showBar, showPercent);
			if (vertical) {
				y += cellH + GAP;
			} else {
				x += cellW + GAP;
			}
		}
	}

	private void drawPanel(GuiGraphicsExtractor context, int x, int y, int w, int h, int accent) {
		// Soft shadow
		context.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
		// Body
		context.fill(x, y, x + w, y + h, 0xD012121A);
		// Inner wash
		context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x22181828);
		// Border
		context.fill(x, y, x + w, y + 1, 0xFF2A2A3A);
		context.fill(x, y + h - 1, x + w, y + h, 0xFF2A2A3A);
		context.fill(x, y, x + 1, y + h, 0xFF2A2A3A);
		context.fill(x + w - 1, y, x + w, y + h, 0xFF2A2A3A);
		// Accent edge
		context.fill(x, y, x + 2, y + h, accent);
	}

	private void drawSlot(
		GuiGraphicsExtractor context,
		ItemStack stack,
		int x,
		int y,
		int cellW,
		int cellH,
		boolean vertical,
		boolean showBar,
		boolean showPercent
	) {
		// Slot well
		context.fill(x, y, x + ICON, y + ICON, 0x66101018);
		context.fill(x, y, x + ICON, y + 1, 0x33FFFFFF);
		context.fill(x, y + ICON - 1, x + ICON, y + ICON, 0x22000000);

		if (!stack.isEmpty()) {
			// Icon only — skip itemDecorations so vanilla durability bar never doubles up.
			context.item(stack, x, y);
		} else {
			context.fill(x + 3, y + 3, x + ICON - 3, y + ICON - 3, 0x22FFFFFF);
		}

		float remaining = remaining(stack);
		int color = stack.isEmpty() ? 0xFF555555 : durabilityColor(remaining);

		if (vertical) {
			int metaX = x + ICON + 4;
			int midY = y + (ICON - 8) / 2;
			if (showBar) {
				int barW = showPercent ? 30 : Math.max(28, cellW - ICON - 4);
				drawBar(context, metaX, midY + 3, barW, remaining, color);
				metaX += barW + 4;
			}
			if (showPercent && !stack.isEmpty() && stack.isDamageableItem()) {
				String text = Math.round(remaining * 100.0f) + "%";
				context.text(mc().font, text, metaX, midY, color);
			}
		} else {
			int metaY = y + ICON + BAR_GAP;
			if (showBar) {
				drawBar(context, x + 1, metaY, ICON - 2, remaining, color);
				metaY += BAR_H + 2;
			}
			if (showPercent && !stack.isEmpty() && stack.isDamageableItem()) {
				String text = Math.round(remaining * 100.0f) + "%";
				int tw = mc().font.width(text);
				context.text(mc().font, text, x + (ICON - tw) / 2, metaY, color);
			}
		}
	}

	private void drawBar(GuiGraphicsExtractor context, int x, int y, int w, float remaining, int color) {
		context.fill(x, y, x + w, y + BAR_H, 0xFF1A1A22);
		if (remaining > 0.0f) {
			int fill = Math.max(1, Math.round(w * remaining));
			context.fill(x, y, x + fill, y + BAR_H, color);
		}
	}

	private static float remaining(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return 1.0f;
		}
		int max = stack.getMaxDamage();
		if (max <= 0) {
			return 1.0f;
		}
		return 1.0f - (float) stack.getDamageValue() / (float) max;
	}

	private int[] anchor(int screenW, int screenH, int panelW, int panelH) {
		int hotbarLeft = screenW / 2 - 91;
		int hotbarRight = screenW / 2 + 91;
		int hotbarTop = screenH - 26;

		return switch (position.getValue()) {
			case "Hotbar Left" -> new int[]{hotbarLeft - panelW - 8, hotbarTop - panelH + ICON};
			case "Top Right" -> new int[]{screenW - panelW - 10, 10};
			case "Bottom Right" -> new int[]{screenW - panelW - 10, screenH - panelH - 10};
			case "Top Left" -> new int[]{10, 10};
			case "Bottom Left" -> new int[]{10, screenH - panelH - 10};
			default -> new int[]{hotbarRight + 8, hotbarTop - panelH + ICON}; // Hotbar Right
		};
	}

	private static int durabilityColor(float remaining) {
		if (remaining > 0.6f) {
			return 0xFF55FF55;
		}
		if (remaining > 0.3f) {
			return 0xFFFFFF55;
		}
		return 0xFFFF5555;
	}
}
