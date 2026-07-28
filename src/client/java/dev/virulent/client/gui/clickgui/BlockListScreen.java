package dev.virulent.client.gui.clickgui;

import dev.virulent.client.setting.BlockListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BlockListScreen extends Screen {
	private static final int ROW_H = 14;
	private static final int PAD = 8;

	private final Screen parent;
	private final BlockListSetting setting;
	private final List<Block> allBlocks = new ArrayList<>();
	private final List<Block> filtered = new ArrayList<>();

	private String query = "";
	private boolean searchFocused = true;
	private int scroll;

	public BlockListScreen(Screen parent, BlockListSetting setting) {
		super(Component.literal(setting.getName()));
		this.parent = parent;
		this.setting = setting;

		for (Block block : BuiltInRegistries.BLOCK) {
			if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
				continue;
			}
			allBlocks.add(block);
		}
		allBlocks.sort(Comparator.comparing(this::blockId));
		rebuildFilter();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xCC101018);

		int panelW = Math.min(320, width - 40);
		int panelH = Math.min(280, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF012121A);
		context.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF2A2A3A);
		context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF2A2A3A);

		var font = Minecraft.getInstance().font;
		context.text(font, setting.getName() + " (" + setting.size() + " selected)", panelX + PAD, panelY + 6, 0xFFE0E0E0);

		int searchY = panelY + 22;
		context.fill(panelX + PAD, searchY, panelX + panelW - PAD, searchY + 14, 0xFF1A1A22);
		String searchText = query.isEmpty() && !searchFocused ? "Search..." : query + (searchFocused ? "_" : "");
		context.text(font, searchText, panelX + PAD + 3, searchY + 3, query.isEmpty() && !searchFocused ? 0xFF666666 : 0xFFFFFFFF);

		int listTop = searchY + 18;
		int listBottom = panelY + panelH - 28;
		int visible = Math.max(1, (listBottom - listTop) / ROW_H);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - visible)));

		for (int i = 0; i < visible; i++) {
			int index = scroll + i;
			if (index >= filtered.size()) {
				break;
			}
			Block block = filtered.get(index);
			int rowY = listTop + i * ROW_H;
			boolean selected = setting.contains(block);
			boolean hovered = mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
				&& mouseY >= rowY && mouseY < rowY + ROW_H;
			context.fill(
				panelX + PAD,
				rowY,
				panelX + panelW - PAD,
				rowY + ROW_H - 1,
				hovered ? 0xFF303040 : (selected ? 0xFF202830 : 0xFF161620)
			);
			String name = blockId(block);
			context.text(font, name, panelX + PAD + 3, rowY + 3, selected ? 0xFF66FFAA : 0xFFCCCCCC);
			context.text(font, selected ? "ON" : "OFF", panelX + panelW - PAD - font.width(selected ? "ON" : "OFF") - 3, rowY + 3,
				selected ? 0xFF66FFAA : 0xFF666666);
		}

		int clearX = panelX + PAD;
		int doneX = panelX + panelW - PAD - 40;
		int btnY = panelY + panelH - 22;
		context.fill(clearX, btnY, clearX + 40, btnY + 14, 0xFF402828);
		context.centeredText(font, "Clear", clearX + 20, btnY + 3, 0xFFFF8888);
		context.fill(doneX, btnY, doneX + 40, btnY + 14, 0xFF284028);
		context.centeredText(font, "Done", doneX + 20, btnY + 3, 0xFF88FF88);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		int panelW = Math.min(320, width - 40);
		int panelH = Math.min(280, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		int searchY = panelY + 22;
		if (button == 0 && mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
			&& mouseY >= searchY && mouseY < searchY + 14) {
			searchFocused = true;
			return true;
		}

		int listTop = searchY + 18;
		int listBottom = panelY + panelH - 28;
		int visible = Math.max(1, (listBottom - listTop) / ROW_H);

		if (button == 0 && mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
			&& mouseY >= listTop && mouseY < listBottom) {
			int row = (int) ((mouseY - listTop) / ROW_H);
			int index = scroll + row;
			if (index >= 0 && index < filtered.size()) {
				setting.toggle(filtered.get(index));
				return true;
			}
		}

		int clearX = panelX + PAD;
		int doneX = panelX + panelW - PAD - 40;
		int btnY = panelY + panelH - 22;
		if (button == 0 && mouseY >= btnY && mouseY < btnY + 14) {
			if (mouseX >= clearX && mouseX < clearX + 40) {
				setting.clear();
				return true;
			}
			if (mouseX >= doneX && mouseX < doneX + 40) {
				Minecraft.getInstance().setScreen(parent);
				return true;
			}
		}

		searchFocused = false;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical) * 3);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key();
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			Minecraft.getInstance().setScreen(parent);
			return true;
		}
		if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			rebuildFilter();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (searchFocused && event.isAllowedChatCharacter()) {
			query += event.codepointAsString();
			rebuildFilter();
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void rebuildFilter() {
		filtered.clear();
		String q = query.toLowerCase(Locale.ROOT).trim();
		for (Block block : allBlocks) {
			if (q.isEmpty() || blockId(block).contains(q)) {
				filtered.add(block);
			}
		}
		scroll = 0;
	}

	private String blockId(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return id == null ? "unknown" : id.toString();
	}
}
