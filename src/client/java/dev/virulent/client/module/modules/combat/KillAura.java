package dev.virulent.client.module.modules.combat;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.CombatUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

public final class KillAura extends Module {
	private final NumberSetting range = addSetting(new NumberSetting("Range", 4.0, 3.0, 6.0, 0.1));
	private final ModeSetting targetMode = addSetting(new ModeSetting("Target", "Distance", "Distance", "Health", "Angle"));
	private final ModeSetting rotations = addSetting(new ModeSetting("Rotations", "Snap", "Off", "Snap", "Smooth"));
	private final NumberSetting rotationSpeed = addSetting(new NumberSetting("Rot Speed", 12.0, 1.0, 20.0, 1.0));
	private final ModeSetting cooldownMode = addSetting(new ModeSetting("Cooldown", "Weapon", "Weapon", "CPS"));
	private final NumberSetting minStrength = addSetting(new NumberSetting("Min Strength", 0.9, 0.5, 1.0, 0.05));
	private final NumberSetting cps = addSetting(new NumberSetting("CPS", 10.0, 1.0, 20.0, 1.0));
	private final NumberSetting maxFov = addSetting(new NumberSetting("Max FOV", 180.0, 30.0, 180.0, 5.0));
	private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", true));
	private final BooleanSetting ignoreFriends = addSetting(new BooleanSetting("Ignore Friends", true));
	private final BooleanSetting raycast = addSetting(new BooleanSetting("Raycast", true));

	private int attackCooldown;

	public KillAura() {
		super("KillAura", "Smart combat aura with rotations, raycast, and weapon cooldown.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onTick() {
		if (mc().player == null || mc().level == null || mc().gameMode == null) {
			return;
		}

		LivingEntity target = findTarget();
		if (target == null) {
			return;
		}

		applyRotations(target);

		if (!isRotationReady(target) || !isAttackReady()) {
			return;
		}

		mc().gameMode.attack(mc().player, target);
		mc().player.swing(InteractionHand.MAIN_HAND);

		if ("CPS".equals(cooldownMode.getValue())) {
			attackCooldown = Math.max(1, (int) (20.0 / cps.getValue()));
		}
	}

	private void applyRotations(LivingEntity target) {
		String mode = rotations.getValue();
		if ("Off".equals(mode)) {
			return;
		}

		float[] needed = CombatUtil.getRotationsToEntity(mc().player, target);
		float yaw = needed[0];
		float pitch = needed[1];

		if ("Smooth".equals(mode)) {
			float factor = rotationSpeed.getValue().floatValue() / 20.0f;
			yaw = CombatUtil.lerpAngle(mc().player.getYRot(), yaw, factor);
			pitch = mc().player.getXRot() + (pitch - mc().player.getXRot()) * factor;
		}

		CombatUtil.applyRotations(mc().player, yaw, pitch);
	}

	private boolean isRotationReady(LivingEntity target) {
		return switch (rotations.getValue()) {
			case "Smooth" -> CombatUtil.getAngleTo(mc().player, target) <= 12.0;
			case "Snap", "Off" -> true;
			default -> true;
		};
	}

	private boolean isAttackReady() {
		if ("CPS".equals(cooldownMode.getValue())) {
			if (attackCooldown > 0) {
				attackCooldown--;
				return false;
			}
			return true;
		}

		return mc().player.getAttackStrengthScale(0.5f) >= minStrength.getValue().floatValue();
	}

	private LivingEntity findTarget() {
		double rangeValue = range.getValue();
		double fovLimit = maxFov.getValue();
		AABB searchBox = mc().player.getBoundingBox().inflate(rangeValue);

		Comparator<LivingEntity> comparator = switch (targetMode.getValue()) {
			case "Health" -> Comparator.comparingDouble(LivingEntity::getHealth);
			case "Angle" -> Comparator.comparingDouble(entity -> CombatUtil.getAngleTo(mc().player, entity));
			default -> Comparator.comparingDouble(mc().player::distanceTo);
		};

		return mc().level.getEntities(mc().player, searchBox).stream()
			.filter(this::isValidTarget)
			.map(entity -> (LivingEntity) entity)
			.filter(entity -> mc().player.distanceTo(entity) <= rangeValue)
			.filter(entity -> !raycast.getValue() || CombatUtil.hasLineOfSight(mc().player, entity))
			.filter(entity -> fovLimit >= 180.0 || CombatUtil.getAngleTo(mc().player, entity) <= fovLimit)
			.min(comparator)
			.orElse(null);
	}

	private boolean isValidTarget(Entity entity) {
		if (entity == mc().player || !(entity instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		if (ignoreFriends.getValue() && friends().isFriend(entity)) {
			return false;
		}
		return !playersOnly.getValue() || entity instanceof Player;
	}

	private FriendsManager friends() {
		return VirulentClient.getInstance().getFriendsManager();
	}
}
