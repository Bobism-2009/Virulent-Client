package dev.virulent.client.input;

import dev.virulent.client.VirulentClient;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Vanilla Controls entries. Module binds stay in the Virulent ClickGUI only;
 * this registers the menu toggle for Minecraft's keybinds screen.
 */
public final class ClientKeybinds {
	private static KeyMapping clickGui;

	private ClientKeybinds() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(VirulentClient.MOD_ID, "controls")
		);
		clickGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.virulent.clickgui",
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			category
		));
	}

	public static KeyMapping clickGui() {
		return clickGui;
	}

	public static int clickGuiKey() {
		if (clickGui == null) {
			return GLFW.GLFW_KEY_RIGHT_SHIFT;
		}
		return KeyMappingHelper.getBoundKeyOf(clickGui).getValue();
	}
}
