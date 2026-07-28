package dev.virulent.client.gui.clickgui;

/**
 * ClickGUI architecture presets — not just color skins.
 * <ul>
 *   <li>{@link #DEFAULT} — Virulent single window with sidebar</li>
 *   <li>{@link #METEOR} — floating per-category panels (Meteor-style)</li>
 *   <li>{@link #WURST} — classic column windows (Wurst-style)</li>
 * </ul>
 */
public enum GuiLayoutStyle {
	DEFAULT("Default", 0xFF4CFF66, 0xFF101018),
	/** Meteor purple accent (145, 61, 226) — solid header like real Meteor Client. */
	METEOR("Meteor", 0xFF913DE2, 0xFF913DE2),
	WURST("Wurst", 0xFFFFD24A, 0xFF3A3A3A);

	private final String label;
	private final int defaultAccent;
	private final int defaultHeader;

	GuiLayoutStyle(String label, int defaultAccent, int defaultHeader) {
		this.label = label;
		this.defaultAccent = defaultAccent;
		this.defaultHeader = defaultHeader;
	}

	public String getLabel() {
		return label;
	}

	public int defaultAccent() {
		return defaultAccent;
	}

	public int defaultHeader() {
		return defaultHeader;
	}

	public GuiLayoutStyle next() {
		GuiLayoutStyle[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static GuiLayoutStyle fromName(String name) {
		if (name == null) {
			return DEFAULT;
		}
		// Migrate old color-theme names to a real layout.
		return switch (name.toUpperCase()) {
			case "METEOR", "TOPBAR", "TERMINAL" -> METEOR;
			case "WURST", "COMPACT", "SLATE" -> WURST;
			case "CLASSIC", "DEFAULT" -> DEFAULT;
			default -> {
				try {
					yield GuiLayoutStyle.valueOf(name);
				} catch (IllegalArgumentException ignored) {
					yield DEFAULT;
				}
			}
		};
	}
}
