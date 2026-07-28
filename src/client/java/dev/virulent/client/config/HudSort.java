package dev.virulent.client.config;

public enum HudSort {
	LENGTH("Len"),
	ALPHABETICAL("Abc"),
	CATEGORY("Cat");

	private final String label;

	HudSort(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public HudSort next() {
		HudSort[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
