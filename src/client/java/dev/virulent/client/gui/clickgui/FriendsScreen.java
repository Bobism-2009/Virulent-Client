package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.friend.FriendsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class FriendsScreen extends Screen {
	private static final int ROW_H = 16;
	private static final int PAD = 8;

	private final Screen parent;
	private final List<String> visible = new ArrayList<>();

	private String nameInput = "";
	private boolean nameFocused = true;
	private String selected;
	private int scroll;
	private String status = "";
	private int statusColor = GuiPaint.TEXT_MUTED;

	public FriendsScreen(Screen parent) {
		super(Component.literal("Friends"));
		this.parent = parent;
		refresh();
	}

	private FriendsManager manager() {
		return VirulentClient.getInstance().getFriendsManager();
	}

	private void refresh() {
		visible.clear();
		visible.addAll(manager().getFriends());
		if (selected != null) {
			boolean still = false;
			for (String name : visible) {
				if (name.equalsIgnoreCase(selected)) {
					selected = name;
					still = true;
					break;
				}
			}
			if (!still) {
				selected = null;
			}
		}
	}

	private void setStatus(String text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, GuiPaint.OVERLAY);

		int panelW = Math.min(360, width - 40);
		int panelH = Math.min(320, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		GuiPaint.inset(context, panelX, panelY, panelW, panelH, GuiPaint.WINDOW_BG, GuiPaint.BORDER);
		GuiPaint.topAccent(context, panelX, panelY, panelW, FriendsManager.FRIEND_COLOR);

		var font = Minecraft.getInstance().font;
		context.text(font, "Friends (" + visible.size() + ")", panelX + PAD, panelY + 6, GuiPaint.TEXT);

		int nameY = panelY + 22;
		GuiPaint.inset(context, panelX + PAD, nameY, panelW - PAD * 2, 14,
			nameFocused ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG,
			nameFocused ? FriendsManager.FRIEND_COLOR : GuiPaint.BORDER);
		String nameText = nameInput.isEmpty() && !nameFocused ? "Username..." : nameInput + (nameFocused ? "_" : "");
		context.text(font, nameText, panelX + PAD + 4, nameY + 3,
			nameInput.isEmpty() && !nameFocused ? GuiPaint.TEXT_MUTED : GuiPaint.TEXT);

		int btnY = nameY + 18;
		drawButton(context, panelX + PAD, btnY, 48, "Add", mouseX, mouseY, 0xFF284028, 0xFF88FF88);
		drawButton(context, panelX + PAD + 52, btnY, 56, "Add Look", mouseX, mouseY, 0xFF283040, 0xFF88AAFF);
		drawButton(context, panelX + PAD + 112, btnY, 52, "Delete", mouseX, mouseY, 0xFF402828, 0xFFFF8888);
		drawButton(context, panelX + PAD + 168, btnY, 48, "Clear", mouseX, mouseY, 0xFF402020, 0xFFFF6666);
		drawButton(context, panelX + panelW - PAD - 48, btnY, 48, "Done", mouseX, mouseY, 0xFF284028, 0xFF88FF88);

		int listTop = btnY + 22;
		int listBottom = panelY + panelH - 22;
		int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, visible.size() - visibleRows)));

		GuiPaint.inset(context, panelX + PAD, listTop, panelW - PAD * 2, listBottom - listTop,
			GuiPaint.WINDOW_INNER, GuiPaint.BORDER_SOFT);

		for (int i = 0; i < visibleRows; i++) {
			int index = scroll + i;
			if (index >= visible.size()) {
				break;
			}
			String name = visible.get(index);
			int rowY = listTop + 1 + i * ROW_H;
			boolean hover = mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
				&& mouseY >= rowY && mouseY < rowY + ROW_H;
			boolean isSelected = selected != null && selected.equalsIgnoreCase(name);
			if (isSelected) {
				GuiPaint.fill(context, panelX + PAD + 1, rowY, panelX + panelW - PAD - 1, rowY + ROW_H, 0xFF1A2830);
			} else if (hover) {
				GuiPaint.fill(context, panelX + PAD + 1, rowY, panelX + panelW - PAD - 1, rowY + ROW_H, GuiPaint.PANEL_HOVER);
			}

			boolean online = isOnline(name);
			int nameColor = online ? FriendsManager.FRIEND_COLOR : GuiPaint.TEXT_DIM;
			context.text(font, name, panelX + PAD + 6, rowY + 4, nameColor);
			context.text(font, online ? "online" : "offline",
				panelX + panelW - PAD - 6 - font.width(online ? "online" : "offline"),
				rowY + 4,
				online ? 0xFF88FF88 : GuiPaint.TEXT_MUTED);
		}

		if (!status.isEmpty()) {
			context.text(font, status, panelX + PAD, panelY + panelH - 14, statusColor);
		}
	}

	private boolean isOnline(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return false;
		}
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	private void drawButton(
		GuiGraphicsExtractor context,
		int x,
		int y,
		int w,
		String label,
		int mouseX,
		int mouseY,
		int bg,
		int fg
	) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
		GuiPaint.inset(context, x, y, w, 14, hover ? GuiPaint.PANEL_HOVER : bg, GuiPaint.BORDER);
		var font = Minecraft.getInstance().font;
		context.text(font, label, x + (w - font.width(label)) / 2, y + 3, fg);
	}

	private boolean clickButton(double mouseX, double mouseY, int x, int y, int w) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (button != 0) {
			return super.mouseClicked(event, doubleClick);
		}

		int panelW = Math.min(360, width - 40);
		int panelH = Math.min(320, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		int nameY = panelY + 22;
		if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
			&& mouseY >= nameY && mouseY < nameY + 14) {
			nameFocused = true;
			return true;
		}
		nameFocused = false;

		int btnY = nameY + 18;
		if (clickButton(mouseX, mouseY, panelX + PAD, btnY, 48)) {
			addFromInput();
			return true;
		}
		if (clickButton(mouseX, mouseY, panelX + PAD + 52, btnY, 56)) {
			addLook();
			return true;
		}
		if (clickButton(mouseX, mouseY, panelX + PAD + 112, btnY, 52)) {
			deleteSelected();
			return true;
		}
		if (clickButton(mouseX, mouseY, panelX + PAD + 168, btnY, 48)) {
			manager().clear();
			selected = null;
			refresh();
			setStatus("Cleared friends list", 0xFFFF8888);
			return true;
		}
		if (clickButton(mouseX, mouseY, panelX + panelW - PAD - 48, btnY, 48)) {
			onClose();
			return true;
		}

		int listTop = btnY + 22;
		int listBottom = panelY + panelH - 22;
		int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
		if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
			&& mouseY >= listTop && mouseY < listBottom) {
			int row = (int) ((mouseY - listTop - 1) / ROW_H);
			int index = scroll + row;
			if (index >= 0 && index < visible.size()) {
				selected = visible.get(index);
				nameInput = selected;
				setStatus("Selected " + selected, FriendsManager.FRIEND_COLOR);
			}
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	private void addFromInput() {
		String name = nameInput.trim();
		if (name.isEmpty()) {
			setStatus("Enter a username", GuiPaint.DANGER);
			return;
		}
		if (manager().add(name)) {
			selected = name;
			refresh();
			setStatus("Added " + name, FriendsManager.FRIEND_COLOR);
			nameInput = "";
		} else if (manager().isFriend(name)) {
			setStatus("Already a friend", GuiPaint.TEXT_MUTED);
		} else {
			setStatus("Invalid name", GuiPaint.DANGER);
		}
	}

	private void addLook() {
		Minecraft client = Minecraft.getInstance();
		if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player
			&& player != client.player) {
			String name = player.getGameProfile().name();
			if (manager().add(name)) {
				selected = name;
				nameInput = name;
				refresh();
				setStatus("Added " + name, FriendsManager.FRIEND_COLOR);
			} else {
				setStatus(name + " is already a friend", GuiPaint.TEXT_MUTED);
			}
			return;
		}
		setStatus("Look at a player first", GuiPaint.DANGER);
	}

	private void deleteSelected() {
		String target = selected != null ? selected : nameInput.trim();
		if (target.isEmpty()) {
			setStatus("Select a friend to delete", GuiPaint.DANGER);
			return;
		}
		if (manager().remove(target)) {
			setStatus("Removed " + target, 0xFFFF8888);
			selected = null;
			nameInput = "";
			refresh();
		} else {
			setStatus("Not on friends list", GuiPaint.DANGER);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (!nameFocused) {
			return super.keyPressed(event);
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			addFromInput();
			return true;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty()) {
			nameInput = nameInput.substring(0, nameInput.length() - 1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!nameFocused) {
			return super.charTyped(event);
		}
		char c = (char) event.codepoint();
		if (Character.isLetterOrDigit(c) || c == '_') {
			if (nameInput.length() < 16) {
				nameInput += c;
			}
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
