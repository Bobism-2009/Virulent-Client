package dev.virulent.client.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ModeSetting extends Setting<String> {
	private final List<String> modes = new ArrayList<>();

	public ModeSetting(String name, String defaultValue, String... modes) {
		super(name, defaultValue);
		this.modes.addAll(Arrays.asList(modes));
	}

	public List<String> getModes() {
		return Collections.unmodifiableList(modes);
	}

	public void replaceModes(List<String> newModes) {
		String current = getValue();
		modes.clear();
		if (newModes.isEmpty()) {
			modes.add("None");
		} else {
			modes.addAll(newModes);
		}
		if (modes.contains(current)) {
			setValue(current);
		} else {
			super.setValue(modes.getFirst());
		}
	}

	public void cycle() {
		int index = modes.indexOf(getValue());
		int next = (index + 1) % modes.size();
		setValue(modes.get(next));
	}

	@Override
	public void setValue(String value) {
		if (modes.contains(value)) {
			super.setValue(value);
		}
	}
}
