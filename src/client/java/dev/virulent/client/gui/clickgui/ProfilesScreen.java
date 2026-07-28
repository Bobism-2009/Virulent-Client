package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProfilesScreen extends Screen {
	private static final int ROW_H = 16;
	private static final int PAD = 8;

	private final Screen parent;
	private final List<String> profiles = new ArrayList<>();
	private final List<Map.Entry<String, String>> bindings = new ArrayList<>();

	private String nameInput = "";
	private boolean nameFocused = true;
	private int scroll;
	private String status = "";
	private int statusColor = GuiPaint.TEXT_DIM;

	public ProfilesScreen(Screen parent) {
		super(Component.literal("Config Profiles"));
		this.parent = parent;
		refresh();
		nameInput = VirulentClient.getInstance().getConfigManager().getActiveProfile();
	}

	private void refresh() {
		ConfigManager configs = VirulentClient.getInstance().getConfigManager();
		profiles.clear();
		profiles.addAll(configs.listProfiles());
		bindings.clear();
		bindings.addAll(configs.getServerProfiles().entrySet());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, GuiPaint.OVERLAY);

		int panelW = Math.min(380, width - 40);
		int panelH = Math.min(340, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		GuiPaint.inset(context, panelX, panelY, panelW, panelH, GuiPaint.WINDOW_BG, GuiPaint.BORDER);
		GuiPaint.topAccent(context, panelX, panelY, panelW, 0xFF4CFF66);

		var font = Minecraft.getInstance().font;
		ConfigManager configs = VirulentClient.getInstance().getConfigManager();
		context.text(font, "Config Profiles", panelX + PAD, panelY + 6, GuiPaint.TEXT);
		context.text(font, "Active: " + configs.getActiveProfile(), panelX + PAD, panelY + 18, 0xFF4CFF66);

		String serverKey = configs.currentServerKey();
		String bound = serverKey == null ? null : configs.profileForServer(serverKey);
		String serverLine;
		if (serverKey == null) {
			serverLine = "Server: join a world to bind";
		} else if (bound == null) {
			serverLine = "Server: " + shortKey(serverKey) + "  (unbound)";
		} else {
			serverLine = "Server: " + shortKey(serverKey) + "  →  " + bound;
		}
		context.text(font, serverLine, panelX + PAD, panelY + 30, GuiPaint.TEXT_MUTED);

		int nameY = panelY + 44;
		GuiPaint.inset(context, panelX + PAD, nameY, panelW - PAD * 2, 14,
			nameFocused ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG,
			nameFocused ? 0xFF4CFF66 : GuiPaint.BORDER);
		String nameText = nameInput.isEmpty() && !nameFocused ? "Profile name..." : nameInput + (nameFocused ? "_" : "");
		context.text(font, nameText, panelX + PAD + 4, nameY + 3,
			nameInput.isEmpty() && !nameFocused ? GuiPaint.TEXT_MUTED : GuiPaint.TEXT);

		int btnY = nameY + 18;
		drawButton(context, panelX + PAD, btnY, 48, "Save", mouseX, mouseY, 0xFF284028, 0xFF88FF88);
		drawButton(context, panelX + PAD + 52, btnY, 48, "Load", mouseX, mouseY, 0xFF283040, 0xFF88AAFF);
		drawButton(context, panelX + PAD + 104, btnY, 48, "Delete", mouseX, mouseY, 0xFF402828, 0xFFFF8888);
		drawButton(context, panelX + panelW - PAD - 48, btnY, 48, "Done", mouseX, mouseY, 0xFF284028, 0xFF88FF88);

		int btn2Y = btnY + 18;
		drawButton(context, panelX + PAD, btn2Y, 56, "Bind", mouseX, mouseY, 0xFF283828, 0xFFAAFFAA);
		drawButton(context, panelX + PAD + 60, btn2Y, 56, "Unbind", mouseX, mouseY, 0xFF382828, 0xFFFFAAAA);
		String autoLabel = configs.isAutoSwitchServers() ? "Auto:ON" : "Auto:OFF";
		int autoFg = configs.isAutoSwitchServers() ? 0xFF88FF88 : 0xFFFFAA66;
		drawButton(context, panelX + PAD + 120, btn2Y, 64, autoLabel, mouseX, mouseY, 0xFF283040, autoFg);

		int listTop = btn2Y + 22;
		int listBottom = panelY + panelH - 22;
		int bindingRows = bindings.isEmpty() ? 0 : bindings.size() + 1;
		int totalRows = profiles.size() + bindingRows;
		int visible = Math.max(1, (listBottom - listTop) / ROW_H);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows - visible)));

		for (int i = 0; i < visible; i++) {
			int index = scroll + i;
			if (index >= totalRows) {
				break;
			}
			int rowY = listTop + i * ROW_H;
			boolean hovered = mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
				&& mouseY >= rowY && mouseY < rowY + ROW_H;

			if (index < profiles.size()) {
				String name = profiles.get(index);
				boolean active = name.equalsIgnoreCase(configs.getActiveProfile());
				GuiPaint.fill(context, panelX + PAD, rowY, panelX + panelW - PAD, rowY + ROW_H - 1,
					hovered ? GuiPaint.PANEL_HOVER : (active ? GuiPaint.blend(0xFF4CFF66, GuiPaint.PANEL_BG, 0.82f) : GuiPaint.PANEL_BG));
				if (active) {
					GuiPaint.accentStrip(context, panelX + PAD, rowY, ROW_H - 1, 0xFF4CFF66);
				}
				context.text(font, name, panelX + PAD + 6, rowY + 4, active ? 0xFF4CFF66 : GuiPaint.TEXT);
				if (active) {
					context.text(font, "ACTIVE", panelX + panelW - PAD - font.width("ACTIVE") - 4, rowY + 4, 0xFF4CFF66);
				}
				continue;
			}

			int bindingIndex = index - profiles.size();
			if (bindingIndex == 0) {
				GuiPaint.fill(context, panelX + PAD, rowY, panelX + panelW - PAD, rowY + ROW_H - 1, GuiPaint.PANEL_BG);
				context.text(font, "Server bindings", panelX + PAD + 6, rowY + 4, GuiPaint.TEXT_DIM);
				continue;
			}

			Map.Entry<String, String> binding = bindings.get(bindingIndex - 1);
			boolean current = serverKey != null && serverKey.equals(binding.getKey());
			GuiPaint.fill(context, panelX + PAD, rowY, panelX + panelW - PAD, rowY + ROW_H - 1,
				hovered ? GuiPaint.PANEL_HOVER : (current ? GuiPaint.blend(0xFF88AAFF, GuiPaint.PANEL_BG, 0.85f) : GuiPaint.PANEL_BG));
			String label = shortKey(binding.getKey()) + "  →  " + binding.getValue();
			context.text(font, label, panelX + PAD + 6, rowY + 4, current ? 0xFF88AAFF : GuiPaint.TEXT_MUTED);
		}

		if (!status.isEmpty()) {
			context.text(font, status, panelX + PAD, panelY + panelH - 14, statusColor);
		} else {
			context.text(font, "Bind links this server to the named profile (auto-loads on join)",
				panelX + PAD, panelY + panelH - 14, GuiPaint.TEXT_MUTED);
		}
	}

	private static String shortKey(String key) {
		if (key.length() <= 28) {
			return key;
		}
		return key.substring(0, 25) + "...";
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
		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
		GuiPaint.fill(context, x, y, x + w, y + 14, hovered ? GuiPaint.blend(fg, bg, 0.75f) : bg);
		context.centeredText(Minecraft.getInstance().font, label, x + w / 2, y + 3, fg);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}

		int panelW = Math.min(380, width - 40);
		int panelH = Math.min(340, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		int nameY = panelY + 44;
		if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD && mouseY >= nameY && mouseY < nameY + 14) {
			nameFocused = true;
			return true;
		}

		int btnY = nameY + 18;
		ConfigManager configs = VirulentClient.getInstance().getConfigManager();
		if (mouseY >= btnY && mouseY < btnY + 14) {
			if (mouseX >= panelX + PAD && mouseX < panelX + PAD + 48) {
				saveCurrent(configs);
				return true;
			}
			if (mouseX >= panelX + PAD + 52 && mouseX < panelX + PAD + 100) {
				loadSelected(configs);
				return true;
			}
			if (mouseX >= panelX + PAD + 104 && mouseX < panelX + PAD + 152) {
				deleteSelected(configs);
				return true;
			}
			if (mouseX >= panelX + panelW - PAD - 48 && mouseX < panelX + panelW - PAD) {
				Minecraft.getInstance().setScreen(parent);
				return true;
			}
		}

		int btn2Y = btnY + 18;
		if (mouseY >= btn2Y && mouseY < btn2Y + 14) {
			if (mouseX >= panelX + PAD && mouseX < panelX + PAD + 56) {
				bindServer(configs);
				return true;
			}
			if (mouseX >= panelX + PAD + 60 && mouseX < panelX + PAD + 116) {
				unbindServer(configs);
				return true;
			}
			if (mouseX >= panelX + PAD + 120 && mouseX < panelX + PAD + 184) {
				configs.setAutoSwitchServers(!configs.isAutoSwitchServers());
				status = configs.isAutoSwitchServers()
					? "Auto-switch enabled"
					: "Auto-switch disabled";
				statusColor = configs.isAutoSwitchServers() ? 0xFF88FF88 : 0xFFFFAA66;
				return true;
			}
		}

		int listTop = btn2Y + 22;
		int listBottom = panelY + panelH - 22;
		int bindingRows = bindings.isEmpty() ? 0 : bindings.size() + 1;
		int totalRows = profiles.size() + bindingRows;
		if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD && mouseY >= listTop && mouseY < listBottom) {
			int row = (int) ((mouseY - listTop) / ROW_H);
			int index = scroll + row;
			if (index >= 0 && index < profiles.size()) {
				nameInput = profiles.get(index);
				nameFocused = true;
				status = "Selected \"" + nameInput + "\"";
				statusColor = GuiPaint.TEXT_DIM;
				return true;
			}
			int bindingIndex = index - profiles.size();
			if (bindingIndex > 0 && bindingIndex - 1 < bindings.size()) {
				Map.Entry<String, String> binding = bindings.get(bindingIndex - 1);
				nameInput = binding.getValue();
				nameFocused = true;
				status = "Binding " + shortKey(binding.getKey()) + " → " + binding.getValue();
				statusColor = 0xFF88AAFF;
				return true;
			}
		}

		nameFocused = false;
		return super.mouseClicked(event, doubleClick);
	}

	private void bindServer(ConfigManager configs) {
		String name = nameInput.isBlank() ? configs.getActiveProfile() : nameInput.trim();
		if (configs.currentServerKey() == null) {
			status = "Join a server or world first";
			statusColor = GuiPaint.DANGER;
			return;
		}
		if (ConfigManager.sanitizeName(name) == null) {
			status = "Invalid profile name";
			statusColor = GuiPaint.DANGER;
			return;
		}
		if (!configs.profileExists(name)) {
			configs.saveProfile(name);
		}
		if (configs.bindCurrentServer(name)) {
			refresh();
			nameInput = name;
			status = "Bound " + shortKey(configs.currentServerKey()) + " → " + name;
			statusColor = 0xFF88FF88;
		} else {
			status = "Failed to bind server";
			statusColor = GuiPaint.DANGER;
		}
	}

	private void unbindServer(ConfigManager configs) {
		String key = configs.currentServerKey();
		if (key == null) {
			status = "Join a server or world first";
			statusColor = GuiPaint.DANGER;
			return;
		}
		if (configs.unbindCurrentServer()) {
			refresh();
			status = "Unbound " + shortKey(key);
			statusColor = 0xFFFF8888;
		} else {
			status = "No binding for this server";
			statusColor = GuiPaint.DANGER;
		}
	}

	private void saveCurrent(ConfigManager configs) {
		String name = nameInput.isBlank() ? configs.getActiveProfile() : nameInput.trim();
		if (ConfigManager.sanitizeName(name) == null) {
			status = "Invalid name (letters, numbers, space . _ -)";
			statusColor = GuiPaint.DANGER;
			return;
		}
		if (configs.saveProfile(name)) {
			refresh();
			nameInput = name;
			status = "Saved profile \"" + name + "\"";
			statusColor = 0xFF88FF88;
		} else {
			status = "Failed to save profile";
			statusColor = GuiPaint.DANGER;
		}
	}

	private void loadSelected(ConfigManager configs) {
		String name = nameInput.isBlank() ? configs.getActiveProfile() : nameInput.trim();
		if (!configs.profileExists(name) && !name.equalsIgnoreCase(configs.getActiveProfile())) {
			status = "Profile not found";
			statusColor = GuiPaint.DANGER;
			return;
		}
		if (configs.loadProfile(name)) {
			refresh();
			nameInput = configs.getActiveProfile();
			status = "Loaded profile \"" + configs.getActiveProfile() + "\"";
			statusColor = 0xFF88AAFF;
		} else {
			status = "Failed to load profile";
			statusColor = GuiPaint.DANGER;
		}
	}

	private void deleteSelected(ConfigManager configs) {
		String name = nameInput.trim();
		if (name.isEmpty()) {
			status = "Select a profile to delete";
			statusColor = GuiPaint.DANGER;
			return;
		}
		if (name.equalsIgnoreCase(ConfigManager.DEFAULT_PROFILE)) {
			status = "Can't delete the default profile";
			statusColor = GuiPaint.DANGER;
			return;
		}
		if (configs.deleteProfile(name)) {
			refresh();
			nameInput = configs.getActiveProfile();
			status = "Deleted \"" + name + "\"";
			statusColor = 0xFFFF8888;
		} else {
			status = "Failed to delete profile";
			statusColor = GuiPaint.DANGER;
		}
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
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			saveCurrent(VirulentClient.getInstance().getConfigManager());
			return true;
		}
		if (nameFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty()) {
			nameInput = nameInput.substring(0, nameInput.length() - 1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!nameFocused || !event.isAllowedChatCharacter() || nameInput.length() >= 32) {
			return super.charTyped(event);
		}
		String next = nameInput + event.codepointAsString();
		if (ConfigManager.sanitizeName(next) != null || next.endsWith(" ")) {
			nameInput = next;
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
