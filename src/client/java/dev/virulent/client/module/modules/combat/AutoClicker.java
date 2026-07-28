package dev.virulent.client.module.modules.combat;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class AutoClicker extends Module {
	private final NumberSetting cps = addSetting(new NumberSetting("CPS", 12.0, 1.0, 20.0, 1.0));
	private final BooleanSetting ignoreFriends = addSetting(new BooleanSetting("Ignore Friends", true));

	private int cooldown;

	public AutoClicker() {
		super("AutoClicker", "Automatically clicks while holding attack.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().gameMode == null || !mc().options.keyAttack.isDown()) {
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		if (mc().hitResult != null && mc().hitResult.getType() == HitResult.Type.ENTITY) {
			Entity target = ((EntityHitResult) mc().hitResult).getEntity();
			if (!(ignoreFriends.getValue() && friends().isFriend(target))) {
				mc().gameMode.attack(mc().player, target);
			}
		}

		mc().player.swing(InteractionHand.MAIN_HAND);
		cooldown = Math.max(1, (int) (20.0 / cps.getValue()));
	}

	private FriendsManager friends() {
		return VirulentClient.getInstance().getFriendsManager();
	}
}
