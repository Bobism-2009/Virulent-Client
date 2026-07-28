package dev.virulent.client.module.modules.combat;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class TriggerBot extends Module {
	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 5.0, 0.0, 20.0, 1.0));
	private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", true));
	private final BooleanSetting ignoreFriends = addSetting(new BooleanSetting("Ignore Friends", true));

	private int cooldown;

	public TriggerBot() {
		super("TriggerBot", "Attacks entities under your crosshair.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().gameMode == null) {
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		if (!(mc().hitResult instanceof EntityHitResult entityHit)) {
			return;
		}

		Entity target = entityHit.getEntity();
		if (!(target instanceof LivingEntity living) || !living.isAlive() || target == mc().player) {
			return;
		}

		if (playersOnly.getValue() && !(target instanceof Player)) {
			return;
		}
		if (ignoreFriends.getValue() && friends().isFriend(target)) {
			return;
		}

		mc().gameMode.attack(mc().player, target);
		mc().player.swing(InteractionHand.MAIN_HAND);
		cooldown = delay.getValue().intValue();
	}

	private FriendsManager friends() {
		return VirulentClient.getInstance().getFriendsManager();
	}
}
