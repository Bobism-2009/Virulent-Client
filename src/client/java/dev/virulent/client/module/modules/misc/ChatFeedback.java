package dev.virulent.client.module.modules.misc;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

public final class ChatFeedback extends Module {
	private static ChatFeedback instance;

	private final BooleanSetting prefix = addSetting(new BooleanSetting("Prefix", true));

	public ChatFeedback() {
		super("ChatFeedback", "Shows a client chat message when you toggle a module.", Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static void onModuleToggled(Module module, boolean enabled) {
		if (instance == null) {
			return;
		}
		if (module instanceof Panic) {
			return;
		}
		// Still announce when this module itself is turned off.
		if (module != instance && !instance.isEnabled()) {
			return;
		}
		if (mc().player == null) {
			return;
		}
		if (VirulentClient.getInstance().getConfigManager().isLoading()) {
			return;
		}
		if (VirulentClient.getInstance().getModuleManager().isSuppressingToggleFeedback()) {
			return;
		}

		MutableComponent message = Component.empty();
		if (instance.prefix.getValue()) {
			message.append(Component.literal("[").withStyle(ChatFormatting.GRAY));
			message.append(Component.literal("Virulent").withStyle(ChatFormatting.LIGHT_PURPLE));
			message.append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
		}
		message.append(Component.literal(module.getName()).withStyle(ChatFormatting.WHITE));
		message.append(Component.literal(enabled ? " enabled" : " disabled")
			.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));

		mc().player.sendSystemMessage(message);
	}
}
