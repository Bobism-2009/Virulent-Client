package dev.virulent.client.module;

public enum Category {
	COMBAT("Combat"),
	MOVEMENT("Movement"),
	RENDER("Render"),
	PLAYER("Player"),
	MISC("Misc"),
	PERFORMANCE("Perf");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
