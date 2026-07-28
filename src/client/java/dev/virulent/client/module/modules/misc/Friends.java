package dev.virulent.client.module.modules.misc;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.ActionSetting;
import dev.virulent.client.setting.BooleanSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class Friends extends Module {
	private final ActionSetting manage = addSetting(new ActionSetting("Manage", "Open"));
	private final BooleanSetting middleClick = addSetting(new BooleanSetting("Middle Click", true));
	private final BooleanSetting chatFeedback = addSetting(new BooleanSetting("Chat Feedback", true));

	private boolean wasMiddleDown;

	public Friends() {
		super("Friends", "Manage friends. Combat/ESP respect the list.", Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
	}

	public ActionSetting getManageSetting() {
		return manage;
	}

	@Override
	public void onTick() {
		if (!middleClick.getValue() || mc().player == null || mc().level == null || mc().screen != null) {
			wasMiddleDown = false;
			return;
		}

		long window = mc().getWindow().handle();
		boolean middleDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
		if (middleDown && !wasMiddleDown) {
			toggleLookedAt();
		}
		wasMiddleDown = middleDown;
	}

	public void toggleLookedAt() {
		if (!(mc().hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player player)) {
			notify("Look at a player to add/remove.", false);
			return;
		}
		if (player == mc().player) {
			return;
		}

		String name = player.getGameProfile().name();
		boolean nowFriend = manager().toggle(name);
		notify((nowFriend ? "Added friend: " : "Removed friend: ") + name, nowFriend);
	}

	public boolean addByName(String name) {
		boolean added = manager().add(name);
		if (added) {
			notify("Added friend: " + name.trim(), true);
		}
		return added;
	}

	public boolean removeByName(String name) {
		boolean removed = manager().remove(name);
		if (removed) {
			notify("Removed friend: " + name.trim(), false);
		}
		return removed;
	}

	private void notify(String message, boolean positive) {
		if (!chatFeedback.getValue() || mc().player == null) {
			return;
		}
		mc().player.sendSystemMessage(Component.literal("[Friends] " + message));
	}

	private FriendsManager manager() {
		return VirulentClient.getInstance().getFriendsManager();
	}
}
