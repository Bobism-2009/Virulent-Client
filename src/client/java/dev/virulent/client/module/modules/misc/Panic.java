package dev.virulent.client.module.modules.misc;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.gui.clickgui.ClickGuiScreen;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import org.lwjgl.glfw.GLFW;

public final class Panic extends Module {
	private final BooleanSetting closeGui = addSetting(new BooleanSetting("Close GUI", true));

	public Panic() {
		super("Panic", "Instantly disables every active module.", Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public void toggle() {
		setEnabled(true);
	}

	@Override
	protected void onEnable() {
		VirulentClient.getInstance().getModuleManager().disableAll(this);

		if (closeGui.getValue() && mc().screen instanceof ClickGuiScreen) {
			VirulentClient.getInstance().getClickGui().toggle();
		}

		super.setEnabled(false);
	}
}
