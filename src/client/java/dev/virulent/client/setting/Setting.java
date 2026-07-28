package dev.virulent.client.setting;

import dev.virulent.client.VirulentClient;

import java.util.function.Consumer;

public abstract class Setting<T> {
	private final String name;
	private final T defaultValue;
	private T value;
	private Consumer<T> onChange;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String getName() {
		return name;
	}

	public T getValue() {
		return value;
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	public void setValue(T value) {
		this.value = value;
		if (onChange != null) {
			onChange.accept(value);
		}
		VirulentClient.getInstance().getConfigManager().scheduleSave();
	}

	public void reset() {
		setValue(defaultValue);
	}

	public boolean isDefault() {
		return defaultValue == null ? value == null : defaultValue.equals(value);
	}

	public Setting<T> onChange(Consumer<T> onChange) {
		this.onChange = onChange;
		return this;
	}
}
