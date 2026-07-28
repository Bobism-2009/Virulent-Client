package dev.virulent.client.gui.clickgui;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.modules.misc.SeedCracker;
import dev.virulent.client.seed.SeedState;
import dev.virulent.client.seed.StructureHit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SeedCrackerScreen extends Screen {
	private static final int PAD = 8;
	private static final int ROW_H = 14;

	private final Screen parent;
	private String seedInput = "";
	private boolean seedFocused = true;
	private String status = "";
	private int statusColor = GuiPaint.TEXT_MUTED;
	private int scroll;

	public SeedCrackerScreen(Screen parent) {
		super(Component.literal("SeedCracker"));
		this.parent = parent;
		SeedState state = SeedState.get();
		if (state.hasWorldSeed()) {
			seedInput = Long.toString(state.getWorldSeed());
		}
	}

	private SeedCracker module() {
		var mod = VirulentClient.getInstance().getModuleManager().getModule("SeedCracker");
		return mod instanceof SeedCracker seedCracker ? seedCracker : null;
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
		SeedState state = SeedState.get();
		context.text(font, "SeedCracker", panelX + PAD, panelY + 6, GuiPaint.TEXT);

		String meta = state.hasWorldSeed()
			? "Active: " + state.getWorldSeed() + " (" + state.getSource() + ")"
			: state.getHashedSeed() != null
				? "Hashed only: " + state.getHashedSeed()
				: "No seed set";
		context.text(font, meta, panelX + PAD, panelY + 18, state.hasWorldSeed() ? 0xFF88FF88 : 0xFFFFAA66);

		int inputY = panelY + 34;
		GuiPaint.inset(context, panelX + PAD, inputY, panelW - PAD * 2, 14,
			seedFocused ? GuiPaint.PANEL_HOVER : GuiPaint.PANEL_BG,
			seedFocused ? 0xFF4CFF66 : GuiPaint.BORDER);
		String shown = seedInput.isEmpty() && !seedFocused ? "Paste world seed..." : seedInput + (seedFocused ? "_" : "");
		context.text(font, shown, panelX + PAD + 4, inputY + 3,
			seedInput.isEmpty() && !seedFocused ? GuiPaint.TEXT_MUTED : GuiPaint.TEXT);

		int btnY = inputY + 18;
		drawButton(context, panelX + PAD, btnY, 52, "Set", mouseX, mouseY, 0xFF284028, 0xFF88FF88);
		drawButton(context, panelX + PAD + 56, btnY, 52, "Clear", mouseX, mouseY, 0xFF402828, 0xFFFF8888);
		drawButton(context, panelX + PAD + 112, btnY, 52, "Scan", mouseX, mouseY, 0xFF283040, 0xFF88AAFF);
		drawButton(context, panelX + PAD + 168, btnY, 70, "Waypoints", mouseX, mouseY, 0xFF302840, 0xFFE14CFF);
		drawButton(context, panelX + panelW - PAD - 48, btnY, 48, "Done", mouseX, mouseY, 0xFF284028, 0xFF88FF88);

		context.text(font, "Reverse-crack structures→seed: use SeedcrackerX 2.16.0 alongside; it can push seeds here.",
			panelX + PAD, btnY + 18, GuiPaint.TEXT_DIM);

		int listTop = btnY + 32;
		int listBottom = panelY + panelH - 22;
		SeedCracker module = module();
		var hits = module != null ? module.getHits() : java.util.List.<StructureHit>of();
		int visible = Math.max(1, (listBottom - listTop) / ROW_H);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, hits.size() - visible)));

		Minecraft client = Minecraft.getInstance();
		double px = client.player != null ? client.player.getX() : 0;
		double pz = client.player != null ? client.player.getZ() : 0;

		for (int i = 0; i < visible; i++) {
			int index = scroll + i;
			if (index >= hits.size()) {
				break;
			}
			StructureHit hit = hits.get(index);
			int rowY = listTop + i * ROW_H;
			GuiPaint.fill(context, panelX + PAD, rowY, panelX + panelW - PAD, rowY + ROW_H - 1, GuiPaint.PANEL_BG);
			GuiPaint.accentStrip(context, panelX + PAD, rowY, ROW_H - 1, hit.color());
			String line = hit.name() + "  " + hit.blockX() + ", " + hit.blockZ()
				+ "  " + hit.distanceBlocks(px, pz) + "m";
			context.text(font, line, panelX + PAD + 6, rowY + 3, hit.color());
		}

		context.text(
			font,
			status.isEmpty() ? "Set seed → Scan → structures ESP in world" : status,
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
		int inputY = panelY + 34;
		int btnY = inputY + 18;

		if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD && mouseY >= inputY && mouseY < inputY + 14) {
			seedFocused = true;
			return true;
		}

		if (mouseY >= btnY && mouseY < btnY + 14) {
			if (mouseX >= panelX + PAD && mouseX < panelX + PAD + 52) {
				if (SeedState.get().tryParseAndSet(seedInput)) {
					SeedCracker module = module();
					if (module != null) {
						module.rescan();
					}
					setStatus("Seed set to " + SeedState.get().getWorldSeed(), 0xFF88FF88);
				} else {
					setStatus("Invalid seed", 0xFFFF8888);
				}
				return true;
			}
			if (mouseX >= panelX + PAD + 56 && mouseX < panelX + PAD + 108) {
				SeedState.get().clearWorldSeed();
				seedInput = "";
				SeedCracker module = module();
				if (module != null) {
					module.rescan();
				}
				setStatus("Cleared world seed", 0xFFFFAA66);
				return true;
			}
			if (mouseX >= panelX + PAD + 112 && mouseX < panelX + PAD + 164) {
				SeedCracker module = module();
				if (module != null) {
					if (!module.isEnabled()) {
						module.setEnabled(true);
					}
					module.rescan();
					setStatus("Scanned " + module.getHits().size() + " structures", 0xFF88AAFF);
				}
				return true;
			}
			if (mouseX >= panelX + PAD + 168 && mouseX < panelX + PAD + 238) {
				SeedCracker module = module();
				if (module != null) {
					int added = module.waypointNearby();
					setStatus("Added " + added + " waypoints", 0xFFE14CFF);
				}
				return true;
			}
			if (mouseX >= panelX + panelW - PAD - 48 && mouseX < panelX + panelW - PAD) {
				Minecraft.getInstance().setScreen(parent);
				return true;
			}
		}

		seedFocused = false;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical) * 3);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			Minecraft.getInstance().setScreen(parent);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			if (SeedState.get().tryParseAndSet(seedInput)) {
				SeedCracker module = module();
				if (module != null) {
					module.rescan();
				}
				setStatus("Seed set to " + SeedState.get().getWorldSeed(), 0xFF88FF88);
			}
			return true;
		}
		if (seedFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE && !seedInput.isEmpty()) {
			seedInput = seedInput.substring(0, seedInput.length() - 1);
			return true;
		}
		if (seedFocused && event.key() == GLFW.GLFW_KEY_V && event.hasControlDown()) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null) {
				seedInput = clip.trim();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!seedFocused || !event.isAllowedChatCharacter() || seedInput.length() >= 32) {
			return super.charTyped(event);
		}
		seedInput += event.codepointAsString();
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
