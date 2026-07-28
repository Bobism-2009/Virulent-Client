package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.modules.render.Waypoints;
import dev.virulent.client.waypoint.Waypoint;
import dev.virulent.client.waypoint.WaypointCoords;
import dev.virulent.client.waypoint.WaypointManager;
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
import java.util.Optional;

public final class WaypointsScreen extends Screen {
	private static final int ROW_H = 16;
	private static final int PAD = 8;

	private final Screen parent;
	private final List<Waypoint> visible = new ArrayList<>();

	private String renameInput = "";
	private boolean renameFocused;
	private Waypoint selected;
	private int scroll;
	private String status = "";
	private int statusColor = GuiPaint.TEXT_MUTED;

	public WaypointsScreen(Screen parent) {
		super(Component.literal("Waypoints"));
		this.parent = parent;
		refresh();
	}

	private WaypointManager manager() {
		return VirulentClient.getInstance().getWaypointManager();
	}

	private void refresh() {
		visible.clear();
		visible.addAll(manager().getWaypoints());
		if (selected != null && !visible.contains(selected)) {
			selected = null;
			renameInput = "";
		}
	}

	private void setStatus(String text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, GuiPaint.OVERLAY);

		int panelW = Math.min(420, width - 40);
		int panelH = Math.min(340, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;

		GuiPaint.inset(context, panelX, panelY, panelW, panelH, GuiPaint.WINDOW_BG, GuiPaint.BORDER);
		GuiPaint.topAccent(context, panelX, panelY, panelW, 0xFF4CFF66);

		var font = Minecraft.getInstance().font;
		context.text(font, "Waypoints (" + visible.size() + ")", panelX + PAD, panelY + 6, GuiPaint.TEXT);

		int btnY = panelY + 22;
		drawButton(context, panelX + PAD, btnY, 70, "Add Here", mouseX, mouseY, 0xFF284028, 0xFF88FF88);
		drawButton(context, panelX + PAD + 74, btnY, 70, "Add Look", mouseX, mouseY, 0xFF283040, 0xFF88AAFF);
		drawButton(context, panelX + PAD + 148, btnY, 52, "Delete", mouseX, mouseY, 0xFF402828, 0xFFFF8888);
		drawButton(context, panelX + PAD + 204, btnY, 52, "Color", mouseX, mouseY, 0xFF302840, 0xFFE14CFF);
		drawButton(context, panelX + panelW - PAD - 48, btnY, 48, "Done", mouseX, mouseY, 0xFF284028, 0xFF88FF88);

		int btnY2 = btnY + 16;
		drawButton(context, panelX + PAD, btnY2, 52, "Copy", mouseX, mouseY, 0xFF283828, 0xFFAAFFAA);
		drawButton(context, panelX + PAD + 56, btnY2, 52, "Paste", mouseX, mouseY, 0xFF283040, 0xFF88AAFF);
		drawButton(context, panelX + PAD + 112, btnY2, 70, "Convert", mouseX, mouseY, 0xFF403028, 0xFFFFAA66);
		drawButton(context, panelX + PAD + 186, btnY2, 70, "Death", mouseX, mouseY, 0xFF402020, 0xFFFF8888);

		int renameY = btnY2 + 18;
		GuiPaint.inset(context, panelX + PAD, renameY, panelW - PAD * 2, 14,
			renameFocused ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG,
			renameFocused ? 0xFF4CFF66 : GuiPaint.BORDER);
		String renameText = renameInput.isEmpty() && !renameFocused
			? "Select a waypoint to rename..."
			: renameInput + (renameFocused ? "_" : "");
		context.text(font, renameText, panelX + PAD + 4, renameY + 3,
			renameInput.isEmpty() && !renameFocused ? GuiPaint.TEXT_MUTED : GuiPaint.TEXT);

		int listTop = renameY + 20;
		int listBottom = panelY + panelH - 22;
		int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, visible.size() - visibleRows)));

		Minecraft client = Minecraft.getInstance();
		String dimension = manager().currentDimensionId();
		double px = client.player != null ? client.player.getX() : 0;
		double py = client.player != null ? client.player.getY() : 0;
		double pz = client.player != null ? client.player.getZ() : 0;

		for (int i = 0; i < visibleRows; i++) {
			int index = scroll + i;
			if (index >= visible.size()) {
				break;
			}
			Waypoint waypoint = visible.get(index);
			int rowY = listTop + i * ROW_H;
			boolean selectedRow = waypoint == selected;
			boolean hovered = mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
				&& mouseY >= rowY && mouseY < rowY + ROW_H;
			GuiPaint.fill(context, panelX + PAD, rowY, panelX + panelW - PAD, rowY + ROW_H - 1,
				hovered || selectedRow ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG);
			GuiPaint.accentStrip(context, panelX + PAD, rowY, ROW_H - 1, waypoint.getColor());

			boolean sameDim = waypoint.getDimension().equals(dimension);
			int dist;
			if (sameDim) {
				dist = (int) Math.round(Math.sqrt(
					(waypoint.getX() - px) * (waypoint.getX() - px)
						+ (waypoint.getY() - py) * (waypoint.getY() - py)
						+ (waypoint.getZ() - pz) * (waypoint.getZ() - pz)
				));
			} else {
				Optional<double[]> linked = WaypointCoords.crossDimPos(waypoint, dimension);
				if (linked.isPresent()) {
					double[] xyz = linked.get();
					dist = (int) Math.round(Math.sqrt(
						(xyz[0] - px) * (xyz[0] - px)
							+ (xyz[1] - py) * (xyz[1] - py)
							+ (xyz[2] - pz) * (xyz[2] - pz)
					));
				} else {
					dist = -1;
				}
			}

			String line = waypoint.getName()
				+ "  " + (int) Math.round(waypoint.getX())
				+ ", " + (int) Math.round(waypoint.getY())
				+ ", " + (int) Math.round(waypoint.getZ())
				+ (sameDim
					? "  " + dist + "m"
					: dist >= 0
						? "  [" + WaypointCoords.shortDim(waypoint.getDimension()) + " ~" + dist + "m]"
						: "  [" + WaypointCoords.shortDim(waypoint.getDimension()) + "]");
			context.text(font, trim(font, line, panelW - PAD * 2 - 10), panelX + PAD + 6, rowY + 4,
				selectedRow ? waypoint.getColor() : GuiPaint.TEXT);
		}

		context.text(
			font,
			status.isEmpty() ? "Copy/Paste share coords  |  Convert OW↔Nether  |  Enter rename" : status,
			panelX + PAD,
			panelY + panelH - 14,
			status.isEmpty() ? GuiPaint.TEXT_MUTED : statusColor
		);
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

	private static String trim(net.minecraft.client.gui.Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String clipped = text;
		while (clipped.length() > 3 && font.width(clipped + "...") > maxWidth) {
			clipped = clipped.substring(0, clipped.length() - 1);
		}
		return clipped + "...";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		double mouseX = event.x();
		double mouseY = event.y();

		int panelW = Math.min(420, width - 40);
		int panelH = Math.min(340, height - 40);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;
		int btnY = panelY + 22;
		int btnY2 = btnY + 16;

		Waypoints module = (Waypoints) VirulentClient.getInstance().getModuleManager().getModule("Waypoints");

		if (mouseY >= btnY && mouseY < btnY + 14) {
			if (mouseX >= panelX + PAD && mouseX < panelX + PAD + 70) {
				if (module != null) {
					module.addAtPlayer();
					refresh();
					setStatus("Added waypoint at you", 0xFF88FF88);
				}
				return true;
			}
			if (mouseX >= panelX + PAD + 74 && mouseX < panelX + PAD + 144) {
				if (module != null) {
					module.addAtLook();
					refresh();
					setStatus("Added waypoint at look", 0xFF88FF88);
				}
				return true;
			}
			if (mouseX >= panelX + PAD + 148 && mouseX < panelX + PAD + 200) {
				if (selected != null) {
					manager().remove(selected);
					selected = null;
					renameInput = "";
					refresh();
					setStatus("Deleted waypoint", 0xFFFF8888);
				} else {
					setStatus("Select a waypoint first", 0xFFFFAA66);
				}
				return true;
			}
			if (mouseX >= panelX + PAD + 204 && mouseX < panelX + PAD + 256) {
				if (selected != null) {
					selected.setColor(manager().nextColor());
					manager().scheduleSave();
					setStatus("Recolored " + selected.getName(), 0xFFE14CFF);
				} else {
					setStatus("Select a waypoint first", 0xFFFFAA66);
				}
				return true;
			}
			if (mouseX >= panelX + panelW - PAD - 48 && mouseX < panelX + panelW - PAD) {
				Minecraft.getInstance().setScreen(parent);
				return true;
			}
		}

		if (mouseY >= btnY2 && mouseY < btnY2 + 14) {
			if (mouseX >= panelX + PAD && mouseX < panelX + PAD + 52) {
				copySelected();
				return true;
			}
			if (mouseX >= panelX + PAD + 56 && mouseX < panelX + PAD + 108) {
				pasteClipboard();
				return true;
			}
			if (mouseX >= panelX + PAD + 112 && mouseX < panelX + PAD + 182) {
				convertSelected();
				return true;
			}
			if (mouseX >= panelX + PAD + 186 && mouseX < panelX + PAD + 256) {
				markDeathHere();
				return true;
			}
		}

		int renameY = btnY2 + 18;
		if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD && mouseY >= renameY && mouseY < renameY + 14) {
			renameFocused = selected != null;
			return true;
		}

		int listTop = renameY + 20;
		int listBottom = panelY + panelH - 22;
		if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD && mouseY >= listTop && mouseY < listBottom) {
			int row = (int) ((mouseY - listTop) / ROW_H);
			int index = scroll + row;
			if (index >= 0 && index < visible.size()) {
				selected = visible.get(index);
				renameInput = selected.getName();
				renameFocused = true;
				setStatus("Selected " + selected.getName(), 0xFF88FF88);
				return true;
			}
		}

		renameFocused = false;
		return super.mouseClicked(event, doubleClick);
	}

	private void copySelected() {
		if (selected == null) {
			setStatus("Select a waypoint first", 0xFFFFAA66);
			return;
		}
		String share = WaypointCoords.formatShare(selected);
		Minecraft.getInstance().keyboardHandler.setClipboard(share);
		setStatus("Copied " + WaypointCoords.formatReadable(selected), 0xFF88FF88);
	}

	private void pasteClipboard() {
		String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
		Optional<Waypoint> parsed = WaypointCoords.parse(
			clip,
			manager().currentDimensionId(),
			manager().nextColor()
		);
		if (parsed.isEmpty()) {
			setStatus("Clipboard is not coords (virulent:/x y z/name @ x y z)", 0xFFFF8888);
			return;
		}
		Waypoint waypoint = parsed.get();
		manager().add(waypoint);
		selected = waypoint;
		renameInput = waypoint.getName();
		refresh();
		setStatus("Imported " + WaypointCoords.formatReadable(waypoint), 0xFF88FF88);
	}

	private void convertSelected() {
		if (selected == null) {
			setStatus("Select a waypoint first", 0xFFFFAA66);
			return;
		}
		Optional<Waypoint> converted = WaypointCoords.convertOwNether(selected, manager().nextColor());
		if (converted.isEmpty()) {
			setStatus("Convert only works for Overworld ↔ Nether", 0xFFFFAA66);
			return;
		}
		Waypoint waypoint = converted.get();
		manager().add(waypoint);
		selected = waypoint;
		renameInput = waypoint.getName();
		refresh();
		setStatus("Created " + WaypointCoords.formatReadable(waypoint), 0xFFFFAA66);
	}

	private void markDeathHere() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			setStatus("No player to mark death", 0xFFFF8888);
			return;
		}
		Waypoint death = manager().recordDeath(
			client.player.getX(),
			client.player.getY(),
			client.player.getZ(),
			manager().currentDimensionId()
		);
		selected = death;
		renameInput = death.getName();
		refresh();
		setStatus("Death marked at " + (int) Math.round(death.getX())
			+ " " + (int) Math.round(death.getY())
			+ " " + (int) Math.round(death.getZ()), 0xFFFF8888);
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
			if (selected != null && !renameInput.isBlank()) {
				selected.setName(renameInput.trim());
				manager().scheduleSave();
				setStatus("Renamed to " + selected.getName(), 0xFF88FF88);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_DELETE && selected != null) {
			manager().remove(selected);
			selected = null;
			renameInput = "";
			refresh();
			setStatus("Deleted waypoint", 0xFFFF8888);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_C && event.hasControlDown() && !renameFocused) {
			copySelected();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_V && event.hasControlDown() && !renameFocused) {
			pasteClipboard();
			return true;
		}
		if (renameFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !renameInput.isEmpty()) {
			renameInput = renameInput.substring(0, renameInput.length() - 1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!renameFocused || selected == null || !event.isAllowedChatCharacter() || renameInput.length() >= 24) {
			return super.charTyped(event);
		}
		renameInput += event.codepointAsString();
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
