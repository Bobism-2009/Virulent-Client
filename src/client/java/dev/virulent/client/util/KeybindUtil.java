package dev.virulent.client.util;

import org.lwjgl.glfw.GLFW;

public final class KeybindUtil {
	private KeybindUtil() {
	}

	public static String getName(int key) {
		if (key == GLFW.GLFW_KEY_UNKNOWN) {
			return "NONE";
		}
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			return "RSHIFT";
		}
		if (key == GLFW.GLFW_KEY_LEFT_SHIFT) {
			return "LSHIFT";
		}
		if (key == GLFW.GLFW_KEY_SPACE) {
			return "SPACE";
		}
		String name = GLFW.glfwGetKeyName(key, 0);
		return name != null ? name.toUpperCase() : "KEY " + key;
	}
}
