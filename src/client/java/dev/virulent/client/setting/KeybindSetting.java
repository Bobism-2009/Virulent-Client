package dev.virulent.client.setting;

import dev.virulent.client.util.KeybindUtil;
import org.lwjgl.glfw.GLFW;

public final class KeybindSetting extends Setting<Integer> {
	public KeybindSetting(String name, int defaultKey) {
		super(name, defaultKey);
	}

	public String getKeyName() {
		return KeybindUtil.getName(getValue());
	}
}
