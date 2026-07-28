package dev.virulent.client.setting;

/**
 * Clickable setting that opens a UI action (not a stored toggle value).
 */
public final class ActionSetting extends Setting<String> {
	public ActionSetting(String name, String label) {
		super(name, label);
	}

	public String getLabel() {
		return getValue();
	}
}
