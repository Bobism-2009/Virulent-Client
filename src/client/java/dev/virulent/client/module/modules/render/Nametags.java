package dev.virulent.client.module.modules.render;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.WorldToScreen;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Nametags extends Module {
	private static final int ICON = 16;
	private static final int ICON_GAP = 2;
	private static final int HAND_GAP = 5;
	private static final int ROW_GAP = 2;
	private static final float ENCHANT_SCALE = 0.5f;

	private static final EquipmentSlot[] ARMOR = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET
	};

	private static final Map<String, String> ENCHANT_ABBREV = buildAbbrevMap();

	private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", true));
	private final ModeSetting friendsMode = addSetting(new ModeSetting("Friends", "Highlight", "Normal", "Highlight", "Hide"));
	private final BooleanSetting items = addSetting(new BooleanSetting("Items", true));
	private final BooleanSetting itemDurability = addSetting(new BooleanSetting("Item Durability", true));
	private final BooleanSetting enchants = addSetting(new BooleanSetting("Enchants", true));
	private final BooleanSetting health = addSetting(new BooleanSetting("Health", true));
	private final BooleanSetting healthBar = addSetting(new BooleanSetting("Health Bar", true));
	private final BooleanSetting armorPercent = addSetting(new BooleanSetting("Armor %", true));
	private final BooleanSetting distance = addSetting(new BooleanSetting("Distance", true));
	private final BooleanSetting ping = addSetting(new BooleanSetting("Ping", true));
	private final BooleanSetting background = addSetting(new BooleanSetting("Background", true));
	private final BooleanSetting matchRenderDistance = addSetting(new BooleanSetting("Render Distance", true));
	private final NumberSetting scale = addSetting(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.05));
	private final NumberSetting range = addSetting(new NumberSetting("Range", 128.0, 8.0, 512.0, 8.0));

	public Nametags() {
		super("Nametags", "Shows entity names, gear, enchants, distance, and ping.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		subscribe(Render2DEvent.class, this::onRender2D);
	}

	private void onRender2D(Render2DEvent event) {
		if (mc().level == null || mc().player == null || mc().options.hideGui) {
			return;
		}

		GuiGraphicsExtractor context = event.getContext();
		Font font = mc().font;
		double rangeValue = effectiveRange();
		float tickDelta = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float tagScale = scale.getValue().floatValue();

		if (playersOnly.getValue()) {
			for (Player player : mc().level.players()) {
				renderTarget(context, font, player, true, rangeValue, tickDelta, tagScale);
			}
			return;
		}

		AABB searchBox = mc().player.getBoundingBox().inflate(rangeValue);
		for (Entity entity : mc().level.getEntities(mc().player, searchBox)) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			renderTarget(context, font, living, entity instanceof Player, rangeValue, tickDelta, tagScale);
		}
	}

	private void renderTarget(
		GuiGraphicsExtractor context,
		Font font,
		LivingEntity living,
		boolean player,
		double rangeValue,
		float tickDelta,
		float tagScale
	) {
		if (!isValidTarget(living, rangeValue)) {
			return;
		}

		boolean friend = friends().isFriend(living);
		int nameColor = colorFor(player, living.isInvisible(), friend);

		Vec3 pos = living.getPosition(tickDelta);
		Vec3 labelPos = pos.add(0.0, living.getBbHeight() + 0.35, 0.0);
		// Clamp off-screen / above / behind targets to the viewport edge so tags still show in RD.
		float[] screen = WorldToScreen.projectClamped(labelPos, tickDelta, 18.0f);
		if (screen == null) {
			return;
		}

		boolean onScreen = screen[2] >= 0.5f;
		float drawScale = onScreen ? tagScale : tagScale * 0.85f;
		drawNametag(context, font, living, player, screen[0], screen[1], nameColor, drawScale, onScreen);
	}

	private double effectiveRange() {
		if (matchRenderDistance.getValue()) {
			return mc().options.getEffectiveRenderDistance() * 16.0;
		}
		return range.getValue();
	}

	private void drawNametag(
		GuiGraphicsExtractor context,
		Font font,
		LivingEntity living,
		boolean isPlayer,
		float x,
		float y,
		int nameColor,
		float tagScale,
		boolean onScreen
	) {
		List<ItemStack> gear = collectGear(living);
		boolean showItems = items.getValue() && anyPresent(gear);
		boolean showEnchants = enchants.getValue() && showItems;

		List<List<String>> enchantCols = new ArrayList<>(gear.size());
		int maxEnchantLines = 0;
		for (ItemStack stack : gear) {
			List<String> lines = showEnchants ? enchantLines(stack) : List.of();
			enchantCols.add(lines);
			maxEnchantLines = Math.max(maxEnchantLines, lines.size());
		}

		String nameLine = buildNameLine(living, isPlayer);
		int nameW = font.width(nameLine);

		int[] colW = columnWidths(font, gear, enchantCols, showItems);
		int itemsW = showItems ? rowWidth(colW) : 0;

		int padX = 4;
		int padY = 3;
		int boxW = Math.max(nameW, itemsW) + padX * 2;

		int enchantH = showEnchants && maxEnchantLines > 0
			? Math.round(maxEnchantLines * font.lineHeight * ENCHANT_SCALE) + 1
			: 0;
		int durH = showItems && itemDurability.getValue() ? font.lineHeight : 0;
		boolean bar = healthBar.getValue() && living.getMaxHealth() > 0.0f;

		int boxH = padY;
		if (showItems) {
			boxH += enchantH + ICON + durH + ROW_GAP;
		}
		boxH += font.lineHeight;
		if (bar) {
			boxH += ROW_GAP + 3;
		}
		boxH += padY;

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(tagScale, tagScale);

		float offsetY;
		if (onScreen) {
			offsetY = -boxH - 2.0f;
		} else {
			// Keep the full tag inside the viewport when pinned to an edge.
			int screenH = mc().getWindow().getGuiScaledHeight();
			boolean nearTop = y < screenH * 0.25f;
			offsetY = nearTop ? 10.0f : -boxH - 8.0f;

			int mx = 0;
			int my = 0;
			context.fill(mx - 2, my - 2, mx + 3, my + 3, nameColor);
			context.fill(mx - 1, my - 4, mx + 2, my + 5, nameColor | 0xFF000000);
			context.fill(mx - 4, my - 1, mx + 5, my + 2, nameColor | 0xFF000000);
		}
		pose.translate(-boxW * 0.5f, offsetY);

		if (background.getValue()) {
			context.fill(-1, -1, boxW + 1, boxH + 1, 0xAA000000);
			context.fill(0, 0, boxW, boxH, 0xCC101018);
			context.fill(0, 0, 2, boxH, nameColor);
		}

		int cursorY = padY;
		if (showItems) {
			int itemsX = (boxW - itemsW) / 2;
			drawGearColumns(context, font, gear, enchantCols, colW, itemsX, cursorY, enchantH);
			cursorY += enchantH + ICON + durH + ROW_GAP;
		}

		context.text(font, nameLine, (boxW - nameW) / 2, cursorY, nameColor);
		cursorY += font.lineHeight;

		if (bar) {
			cursorY += ROW_GAP;
			int barX = padX;
			int barW = Math.max(1, boxW - padX * 2);
			float hp = living.getHealth() + living.getAbsorptionAmount();
			float ratio = Mth.clamp(hp / living.getMaxHealth(), 0.0f, 1.0f);
			context.fill(barX, cursorY, barX + barW, cursorY + 3, 0xFF2A2A38);
			context.fill(barX, cursorY, barX + Math.round(barW * ratio), cursorY + 3, healthColor(ratio));
		}

		pose.popMatrix();
	}

	private void drawGearColumns(
		GuiGraphicsExtractor context,
		Font font,
		List<ItemStack> gear,
		List<List<String>> enchantCols,
		int[] colW,
		int x,
		int y,
		int enchantH
	) {
		int cursor = x;
		for (int i = 0; i < gear.size(); i++) {
			ItemStack stack = gear.get(i);
			int w = colW[i];
			int iconX = cursor + (w - ICON) / 2;

			List<String> lines = enchantCols.get(i);
			if (!lines.isEmpty()) {
				var pose = context.pose();
				pose.pushMatrix();
				pose.translate(cursor + w * 0.5f, y + enchantH);
				pose.scale(ENCHANT_SCALE, ENCHANT_SCALE);
				int lineY = -font.lineHeight;
				for (int li = lines.size() - 1; li >= 0; li--) {
					String text = lines.get(li);
					int tw = font.width(text);
					context.text(font, text, -tw / 2, lineY, 0xFF9BE7FF);
					lineY -= font.lineHeight;
				}
				pose.popMatrix();
			}

			context.fill(iconX, y + enchantH, iconX + ICON, y + enchantH + ICON, 0x66101018);
			if (!stack.isEmpty()) {
				context.item(stack, iconX, y + enchantH);
				context.itemDecorations(font, stack, iconX, y + enchantH);
			}

			if (itemDurability.getValue() && !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
				float r = 1.0f - (float) stack.getDamageValue() / (float) stack.getMaxDamage();
				String pct = Math.round(r * 100.0f) + "%";
				int tw = font.width(pct);
				context.text(font, pct, cursor + (w - tw) / 2, y + enchantH + ICON, durabilityColor(r));
			}

			cursor += w;
			if (i == 3) {
				cursor += HAND_GAP;
			} else if (i < gear.size() - 1) {
				cursor += ICON_GAP;
			}
		}
	}

	private int[] columnWidths(Font font, List<ItemStack> gear, List<List<String>> enchantCols, boolean showItems) {
		int[] widths = new int[gear.size()];
		if (!showItems) {
			return widths;
		}
		for (int i = 0; i < gear.size(); i++) {
			int w = ICON;
			if (enchants.getValue()) {
				for (String line : enchantCols.get(i)) {
					w = Math.max(w, Math.round(font.width(line) * ENCHANT_SCALE));
				}
			}
			if (itemDurability.getValue()) {
				ItemStack stack = gear.get(i);
				if (!stack.isEmpty() && stack.isDamageableItem()) {
					w = Math.max(w, font.width("100%"));
				}
			}
			widths[i] = w;
		}
		return widths;
	}

	private static int rowWidth(int[] colW) {
		int total = 0;
		for (int i = 0; i < colW.length; i++) {
			total += colW[i];
			if (i == 3) {
				total += HAND_GAP;
			} else if (i < colW.length - 1) {
				total += ICON_GAP;
			}
		}
		return total;
	}

	private List<String> enchantLines(ItemStack stack) {
		if (stack.isEmpty()) {
			return List.of();
		}
		ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		if (ench.isEmpty()) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : ench.entrySet()) {
			String key = enchantKey(entry.getKey());
			int level = entry.getIntValue();
			String abbr = abbreviate(key);
			if (level <= 1 && ("mending".equals(key) || "aqua_affinity".equals(key)
				|| "silk_touch".equals(key) || "infinity".equals(key)
				|| "channeling".equals(key) || "multishot".equals(key)
				|| "binding_curse".equals(key) || "vanishing_curse".equals(key)
				|| "flame".equals(key))) {
				lines.add(abbr);
			} else {
				lines.add(abbr + roman(level));
			}
		}
		return lines;
	}

	private String buildNameLine(LivingEntity living, boolean isPlayer) {
		StringBuilder line = new StringBuilder(living.getName().getString());
		if (health.getValue()) {
			float hp = living.getHealth() + living.getAbsorptionAmount();
			line.append(' ').append(formatHealth(hp)).append("hp");
		}
		if (armorPercent.getValue()) {
			int avg = averageArmorDurability(living);
			if (avg >= 0) {
				line.append(' ').append(avg).append('%');
			}
		}
		if (distance.getValue() && mc().player != null) {
			line.append(' ').append(Math.round(mc().player.distanceTo(living))).append('m');
		}
		if (ping.getValue() && isPlayer) {
			int p = pingFor((Player) living);
			if (p >= 0) {
				line.append(' ').append(p).append("ms");
			}
		}
		return line.toString();
	}

	private List<ItemStack> collectGear(LivingEntity living) {
		List<ItemStack> gear = new ArrayList<>(6);
		for (EquipmentSlot slot : ARMOR) {
			gear.add(living.getItemBySlot(slot));
		}
		gear.add(living.getOffhandItem());
		gear.add(living.getMainHandItem());
		return gear;
	}

	private static boolean anyPresent(List<ItemStack> gear) {
		for (ItemStack stack : gear) {
			if (!stack.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static String enchantKey(Holder<Enchantment> holder) {
		return holder.unwrapKey()
			.map(k -> k.identifier().getPath())
			.orElse("unknown");
	}

	private static String abbreviate(String path) {
		String hit = ENCHANT_ABBREV.get(path);
		if (hit != null) {
			return hit;
		}
		if (path.length() <= 4) {
			return capitalize(path);
		}
		return capitalize(path.substring(0, 4));
	}

	private static String capitalize(String s) {
		if (s.isEmpty()) {
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(level);
		};
	}

	private int averageArmorDurability(LivingEntity living) {
		int sum = 0;
		int count = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = living.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
				continue;
			}
			float r = 1.0f - (float) stack.getDamageValue() / (float) stack.getMaxDamage();
			sum += Math.round(r * 100.0f);
			count++;
		}
		return count == 0 ? -1 : sum / count;
	}

	private int pingFor(Player player) {
		if (mc().getConnection() == null) {
			return -1;
		}
		PlayerInfo info = mc().getConnection().getPlayerInfo(player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	private static String formatHealth(float hp) {
		if (hp >= 10.0f || hp == Math.rint(hp)) {
			return String.valueOf(Math.round(hp));
		}
		return String.format("%.1f", hp);
	}

	private static int healthColor(float ratio) {
		if (ratio > 0.66f) {
			return 0xFF4CFF66;
		}
		if (ratio > 0.33f) {
			return 0xFFFFD24A;
		}
		return 0xFFFF6B6B;
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

	private boolean isValidTarget(LivingEntity living, double rangeValue) {
		if (living == mc().player || !living.isAlive()) {
			return false;
		}
		if (living.isSpectator()) {
			return false;
		}
		boolean player = living instanceof Player;
		if (living.isInvisible() && !player) {
			return false;
		}
		if (playersOnly.getValue() && !player) {
			return false;
		}
		if ("Hide".equals(friendsMode.getValue()) && friends().isFriend(living)) {
			return false;
		}
		return !(mc().player.distanceTo(living) > rangeValue);
	}

	private int colorFor(boolean player, boolean invisible, boolean friend) {
		if (friend && "Highlight".equals(friendsMode.getValue())) {
			return FriendsManager.FRIEND_COLOR;
		}
		if (player) {
			return invisible ? 0xFFFFAA00 : 0xFFE8E8F0;
		}
		return 0xFFB026FF;
	}

	private FriendsManager friends() {
		return VirulentClient.getInstance().getFriendsManager();
	}

	private static Map<String, String> buildAbbrevMap() {
		Map<String, String> m = new HashMap<>();
		m.put("protection", "Prot");
		m.put("projectile_protection", "Proj");
		m.put("fire_protection", "Fire");
		m.put("blast_protection", "Blast");
		m.put("feather_falling", "Feat");
		m.put("respiration", "Resp");
		m.put("aqua_affinity", "Aqua");
		m.put("thorns", "Thorn");
		m.put("depth_strider", "Depth");
		m.put("frost_walker", "Frost");
		m.put("binding_curse", "Bind");
		m.put("soul_speed", "Soul");
		m.put("swift_sneak", "Swift");
		m.put("sharpness", "Sharp");
		m.put("smite", "Smite");
		m.put("bane_of_arthropods", "Bane");
		m.put("knockback", "KB");
		m.put("fire_aspect", "Asp");
		m.put("looting", "Loot");
		m.put("sweeping_edge", "Sweep");
		m.put("efficiency", "Eff");
		m.put("silk_touch", "Silk");
		m.put("unbreaking", "Unb");
		m.put("fortune", "Fort");
		m.put("power", "Pow");
		m.put("punch", "Punch");
		m.put("flame", "Flame");
		m.put("infinity", "Inf");
		m.put("luck_of_the_sea", "Luck");
		m.put("lure", "Lure");
		m.put("loyalty", "Loy");
		m.put("impaling", "Imp");
		m.put("riptide", "Rip");
		m.put("channeling", "Chan");
		m.put("multishot", "Multi");
		m.put("quick_charge", "QC");
		m.put("piercing", "Pierce");
		m.put("mending", "Mend");
		m.put("vanishing_curse", "Vanish");
		m.put("density", "Dens");
		m.put("breach", "Breach");
		m.put("wind_burst", "Wind");
		return m;
	}
}
