package dev.virulent.client.gui.clickgui;

import dev.virulent.client.setting.BlockEspConfig;
import dev.virulent.client.setting.BlockEspConfigSetting;
import dev.virulent.client.setting.BlockEspConfigsSetting;
import dev.virulent.client.setting.BlockListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BlockEspConfigsScreen extends Screen {
	private static final int ROW_H = 14;
	private static final int PAD = 8;

	private final Screen parent;
	private final BlockListSetting blocks;
	private final BlockEspConfigSetting defaultConfig;
	private final BlockEspConfigsSetting configs;
	private final List<Block> rows = new ArrayList<>();
	private int scroll;

	public BlockEspConfigsScreen(
		Screen parent,
		BlockListSetting blocks,
		BlockEspConfigSetting defaultConfig,
		BlockEspConfigsSetting configs
	) {
		super(Component.literal(configs.getName()));
		this.parent = parent;
		this.blocks = blocks;
		this.defaultConfig = defaultConfig;
		this.configs = configs;
		rebuildRows();
	}

	private void rebuildRows() {
		rows.clear();
		rows.addAll(blocks.getValue());
		rows.sort(Comparator.comparing(this::blockId));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xCC101018);

		int panelW = Math.min(320, width - 40);
		int panelH = Math.min(280, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF012121A);
		var font = Minecraft.getInstance().font;
		context.text(font, "Block Configs", panelX + PAD, panelY + 6, 0xFFE0E0E0);
		context.text(font, "Click a block to edit its override", panelX + PAD, panelY + 18, 0xFF888888);

		int listTop = panelY + 34;
		int listBottom = panelY + panelH - 28;
		int visible = Math.max(1, (listBottom - listTop) / ROW_H);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));

		if (rows.isEmpty()) {
			context.text(font, "Select blocks first.", panelX + PAD, listTop + 4, 0xFF666666);
		}

		for (int i = 0; i < visible; i++) {
			int index = scroll + i;
			if (index >= rows.size()) {
				break;
			}
			Block block = rows.get(index);
			int rowY = listTop + i * ROW_H;
			boolean custom = configs.get(block) != null;
			boolean hovered = mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
				&& mouseY >= rowY && mouseY < rowY + ROW_H;
			context.fill(panelX + PAD, rowY, panelX + panelW - PAD, rowY + ROW_H - 1,
				hovered ? 0xFF303040 : 0xFF161620);
			context.text(font, blockId(block), panelX + PAD + 3, rowY + 3, 0xFFCCCCCC);
			String tag = custom ? "Custom" : "Default";
			context.text(font, tag, panelX + panelW - PAD - font.width(tag) - 3, rowY + 3, custom ? 0xFF66FFAA : 0xFF666666);
		}

		int doneX = panelX + panelW - PAD - 40;
		int btnY = panelY + panelH - 22;
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
		int doneX = panelX + panelW - PAD - 40;
		int btnY = panelY + panelH - 22;

		if (button == 0 && mouseX >= doneX && mouseX < doneX + 40 && mouseY >= btnY && mouseY < btnY + 14) {
			Minecraft.getInstance().setScreen(parent);
			return true;
		}

		int listTop = panelY + 34;
		int listBottom = panelY + panelH - 28;
		if (button == 0 && mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
			&& mouseY >= listTop && mouseY < listBottom) {
			int row = (int) ((mouseY - listTop) / ROW_H);
			int index = scroll + row;
			if (index >= 0 && index < rows.size()) {
				Block block = rows.get(index);
				BlockEspConfig current = configs.get(block);
				BlockEspConfig edit = current != null ? current.copy() : defaultConfig.getValue().copy();
				Minecraft.getInstance().setScreen(new BlockEspConfigScreen(
					this,
					"Config: " + blockId(block),
					edit,
					saved -> {
						if (saved.equals(defaultConfig.getValue())) {
							configs.remove(block);
						} else {
							configs.put(block, saved);
						}
					}
				));
				return true;
			}
		}

		if (button == 1 && mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
			&& mouseY >= listTop && mouseY < listBottom) {
			int row = (int) ((mouseY - listTop) / ROW_H);
			int index = scroll + row;
			if (index >= 0 && index < rows.size()) {
				configs.remove(rows.get(index));
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical) * 3);
		return true;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			Minecraft.getInstance().setScreen(parent);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private String blockId(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return id == null ? "unknown" : id.toString();
	}
}
