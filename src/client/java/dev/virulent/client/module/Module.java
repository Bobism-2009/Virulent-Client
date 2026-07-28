package dev.virulent.client.module;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	protected static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();

	private boolean enabled;
	private int keyBind;
	private final int defaultKeyBind;

	protected Module(String name, String description, Category category, int keyBind) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.keyBind = keyBind;
		this.defaultKeyBind = keyBind;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public int getKeyBind() {
		return keyBind;
	}

	public void setKeyBind(int keyBind) {
		this.keyBind = keyBind;
		VirulentClient.getInstance().getConfigManager().scheduleSave();
	}

	public void resetToDefaults() {
		setKeyBind(defaultKeyBind);
		for (Setting<?> setting : settings) {
			setting.reset();
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
		VirulentClient.getInstance().getConfigManager().scheduleSave();
	}

	protected <T extends Setting<?>> T addSetting(T setting) {
		settings.add(setting);
		return setting;
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	public void onTick() {
	}

	protected <T extends dev.virulent.client.event.Event> void subscribe(Class<T> eventType, java.util.function.Consumer<T> listener) {
		VirulentClient.getInstance().getEventBus().subscribe(eventType, event -> {
			if (isEnabled()) {
				listener.accept(event);
			}
		});
	}
}
