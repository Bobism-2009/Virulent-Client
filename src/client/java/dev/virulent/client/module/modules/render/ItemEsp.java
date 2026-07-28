package dev.virulent.client.module.modules.render;

import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.Render2DUtil;
import dev.virulent.client.util.RenderUtil;
import dev.virulent.client.util.WorldToScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class ItemEsp extends Module {
	private final ModeSetting filter = addSetting(new ModeSetting("Filter", "All", "All", "Valuables"));
	private final BooleanSetting boxes = addSetting(new BooleanSetting("Boxes", true));
	private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", false));
	private final BooleanSetting names = addSetting(new BooleanSetting("Names", true));
	private final BooleanSetting showCount = addSetting(new BooleanSetting("Show Count", true));
	private final NumberSetting nameScale = addSetting(new NumberSetting("Name Scale", 0.5, 0.2, 1.5, 0.05));
	private final NumberSetting range = addSetting(new NumberSetting("Range", 64.0, 8.0, 128.0, 8.0));
	private final NumberSetting minCount = addSetting(new NumberSetting("Min Count", 1.0, 1.0, 64.0, 1.0));
	private final NumberSetting thickness = addSetting(new NumberSetting("Thickness", 1.0, 0.5, 5.0, 0.5));

	public ItemEsp() {
		super("ItemESP", "Highlights dropped items through walls.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render3DEvent.class, this::onRender3D);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	public boolean needsWorldRender() {
		return isEnabled() && (boxes.getValue() || names.getValue());
	}

	private void onRender3D(Render3DEvent event) {
		if (mc().level == null || mc().player == null || (!boxes.getValue() && !names.getValue())) {
			return;
		}

		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean drawBoxes = boxes.getValue();
		boolean drawNames = names.getValue();

		RenderUtil.beginLines(event.getContext());
		for (ItemEntity entity : nearbyItems()) {
			ItemStack stack = entity.getItem();
			int color = colorFor(stack);
			Vec3 pos = entity.getPosition(tickDelta);
			AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));

			if (drawBoxes) {
				RenderUtil.addBox(box.inflate(0.05), color);
			}
			if (drawNames) {
				String label = labelFor(stack);
				Vec3 namePos = pos.add(0.0, entity.getBbHeight() + 0.35, 0.0);
				Gizmos.billboardText(
					label,
					namePos,
					TextGizmo.Style.forColorAndCentered(color).withScale(nameScale.getValue().floatValue())
				).setAlwaysOnTop();
			}
		}
		RenderUtil.endLines();
	}

	private void onRender2D(Render2DEvent event) {
		if (mc().level == null || mc().player == null || mc().options.hideGui || !tracers.getValue()) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		float cursorX = mc().getWindow().getGuiScaledWidth() * 0.5f;
		float cursorY = mc().getWindow().getGuiScaledHeight() * 0.5f;
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float lineWidth = thickness.getValue().floatValue();

		for (ItemEntity entity : nearbyItems()) {
			Vec3 pos = entity.getPosition(tickDelta).add(0.0, entity.getBbHeight() * 0.5, 0.0);
			float[] screen = WorldToScreen.project(pos, tickDelta);
			if (screen == null) {
				continue;
			}
			Render2DUtil.drawLine(context, cursorX, cursorY, screen[0], screen[1], colorFor(entity.getItem()), lineWidth);
		}
	}

	private Iterable<ItemEntity> nearbyItems() {
		double rangeValue = range.getValue();
		AABB searchBox = mc().player.getBoundingBox().inflate(rangeValue);
		int min = minCount.getValue().intValue();
		boolean valuablesOnly = filter.getValue().equals("Valuables");

		return mc().level.getEntitiesOfClass(ItemEntity.class, searchBox, entity -> {
			if (!entity.isAlive()) {
				return false;
			}
			ItemStack stack = entity.getItem();
			if (stack.isEmpty() || stack.getCount() < min) {
				return false;
			}
			if (mc().player.distanceTo(entity) > rangeValue) {
				return false;
			}
			return !valuablesOnly || isValuable(stack);
		});
	}

	private String labelFor(ItemStack stack) {
		String name = stack.getHoverName().getString();
		if (showCount.getValue() && stack.getCount() > 1) {
			return name + " x" + stack.getCount();
		}
		return name;
	}

	private static int colorFor(ItemStack stack) {
		if (isHighValue(stack)) {
			return 0xFFFF55FF; // magenta — netherite / totem / shulker tier
		}
		if (isValuable(stack)) {
			return 0xFF55FFFF; // cyan — diamonds / enchanted / rare
		}
		return switch (stack.getRarity()) {
			case EPIC -> 0xFFFF55FF;
			case RARE -> 0xFF5555FF;
			case UNCOMMON -> 0xFF55FF55;
			default -> 0xFFFFAA00;
		};
	}

	private static boolean isValuable(ItemStack stack) {
		if (isHighValue(stack)) {
			return true;
		}
		if (stack.isEnchanted() || stack.hasFoil()) {
			return true;
		}
		Rarity rarity = stack.getRarity();
		if (rarity == Rarity.RARE || rarity == Rarity.EPIC) {
			return true;
		}
		Item item = stack.getItem();
		return item == Items.DIAMOND
			|| item == Items.DIAMOND_BLOCK
			|| item == Items.EMERALD
			|| item == Items.EMERALD_BLOCK
			|| item == Items.GOLDEN_APPLE
			|| item == Items.ENCHANTED_GOLDEN_APPLE
			|| item == Items.END_CRYSTAL
			|| item == Items.EXPERIENCE_BOTTLE
			|| item == Items.DIAMOND_SWORD
			|| item == Items.DIAMOND_PICKAXE
			|| item == Items.DIAMOND_AXE
			|| item == Items.DIAMOND_SHOVEL
			|| item == Items.DIAMOND_HOE
			|| item == Items.DIAMOND_HELMET
			|| item == Items.DIAMOND_CHESTPLATE
			|| item == Items.DIAMOND_LEGGINGS
			|| item == Items.DIAMOND_BOOTS
			|| stack.is(ItemTags.DIAMOND_TOOL_MATERIALS);
	}

	private static boolean isHighValue(ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.NETHERITE_INGOT
			|| item == Items.NETHERITE_SCRAP
			|| item == Items.NETHERITE_BLOCK
			|| item == Items.ANCIENT_DEBRIS
			|| item == Items.TOTEM_OF_UNDYING
			|| item == Items.ELYTRA
			|| item == Items.ENCHANTED_GOLDEN_APPLE
			|| item == Items.NETHER_STAR
			|| item == Items.NETHERITE_SWORD
			|| item == Items.NETHERITE_PICKAXE
			|| item == Items.NETHERITE_AXE
			|| item == Items.NETHERITE_SHOVEL
			|| item == Items.NETHERITE_HOE
			|| item == Items.NETHERITE_HELMET
			|| item == Items.NETHERITE_CHESTPLATE
			|| item == Items.NETHERITE_LEGGINGS
			|| item == Items.NETHERITE_BOOTS) {
			return true;
		}
		return stack.is(ItemTags.NETHERITE_TOOL_MATERIALS) || stack.is(ItemTags.SHULKER_BOXES);
	}
}
