package dev.virulent.client.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.ModeSetting;
import dev.virulent.client.setting.NumberSetting;
import dev.virulent.client.util.ServerRotations;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

public final class HandView extends Module {
	private static HandView instance;

	private final BooleanSetting serverRotations = addSetting(new BooleanSetting("Server Rotations", false));
	private final BooleanSetting oldAnimations = addSetting(new BooleanSetting("Old Animations", false));
	private final BooleanSetting skipSwapping = addSetting(new BooleanSetting("Skip Swapping", false));
	private final BooleanSetting disableEating = addSetting(new BooleanSetting("Disable Eating", false));
	private final BooleanSetting swordSlash = addSetting(new BooleanSetting("Sword Slash", false));
	private final ModeSetting swingMode = addSetting(new ModeSetting("Swing Mode", "None", "None", "Mainhand", "Offhand"));
	private final NumberSetting swingSpeed = addSetting(new NumberSetting("Swing Speed", 6.0, 0.0, 20.0, 1.0));
	private final NumberSetting mainProgress = addSetting(new NumberSetting("Main Hand Progress", 0.0, 0.0, 1.0, 0.01));
	private final NumberSetting offProgress = addSetting(new NumberSetting("Off Hand Progress", 0.0, 0.0, 1.0, 0.01));

	private final NumberSetting mainScaleX = addSetting(new NumberSetting("Main Scale X", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting mainScaleY = addSetting(new NumberSetting("Main Scale Y", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting mainScaleZ = addSetting(new NumberSetting("Main Scale Z", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting mainPosX = addSetting(new NumberSetting("Main Pos X", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting mainPosY = addSetting(new NumberSetting("Main Pos Y", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting mainPosZ = addSetting(new NumberSetting("Main Pos Z", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting mainRotX = addSetting(new NumberSetting("Main Rot X", 0.0, -180.0, 180.0, 1.0));
	private final NumberSetting mainRotY = addSetting(new NumberSetting("Main Rot Y", 0.0, -180.0, 180.0, 1.0));
	private final NumberSetting mainRotZ = addSetting(new NumberSetting("Main Rot Z", 0.0, -180.0, 180.0, 1.0));

	private final NumberSetting offScaleX = addSetting(new NumberSetting("Off Scale X", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting offScaleY = addSetting(new NumberSetting("Off Scale Y", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting offScaleZ = addSetting(new NumberSetting("Off Scale Z", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting offPosX = addSetting(new NumberSetting("Off Pos X", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting offPosY = addSetting(new NumberSetting("Off Pos Y", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting offPosZ = addSetting(new NumberSetting("Off Pos Z", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting offRotX = addSetting(new NumberSetting("Off Rot X", 0.0, -180.0, 180.0, 1.0));
	private final NumberSetting offRotY = addSetting(new NumberSetting("Off Rot Y", 0.0, -180.0, 180.0, 1.0));
	private final NumberSetting offRotZ = addSetting(new NumberSetting("Off Rot Z", 0.0, -180.0, 180.0, 1.0));

	private final NumberSetting armScaleX = addSetting(new NumberSetting("Arm Scale X", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting armScaleY = addSetting(new NumberSetting("Arm Scale Y", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting armScaleZ = addSetting(new NumberSetting("Arm Scale Z", 1.0, 0.0, 5.0, 0.1));
	private final NumberSetting armPosX = addSetting(new NumberSetting("Arm Pos X", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting armPosY = addSetting(new NumberSetting("Arm Pos Y", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting armPosZ = addSetting(new NumberSetting("Arm Pos Z", 0.0, -3.0, 3.0, 0.1));
	private final NumberSetting armRotX = addSetting(new NumberSetting("Arm Rot X", 0.0, -180.0, 180.0, 1.0));
	private final NumberSetting armRotY = addSetting(new NumberSetting("Arm Rot Y", 0.0, -180.0, 180.0, 1.0));
	private final NumberSetting armRotZ = addSetting(new NumberSetting("Arm Rot Z", 0.0, -180.0, 180.0, 1.0));

	public HandView() {
		super("HandView", "Customize first-person hand and held item rendering.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	public static HandView get() {
		return instance;
	}

	public boolean oldAnimations() {
		return isActive() && oldAnimations.getValue();
	}

	public boolean skipSwapping() {
		return isActive() && skipSwapping.getValue();
	}

	public boolean disableEating() {
		return isActive() && disableEating.getValue();
	}

	public boolean swordSlash() {
		return isActive() && swordSlash.getValue();
	}

	public String getSwingMode() {
		return swingMode.getValue();
	}

	public int getSwingSpeed() {
		return swingSpeed.getValue().intValue();
	}

	public float getMainProgress() {
		return mainProgress.getValue().floatValue();
	}

	public float getOffProgress() {
		return offProgress.getValue().floatValue();
	}

	public void applyHeldItemTransforms(InteractionHand hand, PoseStack poseStack) {
		if (!isActive()) {
			return;
		}

		if (serverRotations.getValue() && ServerRotations.isTracking()) {
			poseStack.mulPose(Axis.XP.rotationDegrees(mc().player.getXRot() - ServerRotations.getPitch()));
			poseStack.mulPose(Axis.YP.rotationDegrees(mc().player.getYRot() - ServerRotations.getYaw()));
		}

		if (hand == InteractionHand.MAIN_HAND) {
			apply(poseStack, mainRotX, mainRotY, mainRotZ, mainScaleX, mainScaleY, mainScaleZ, mainPosX, mainPosY, mainPosZ);
		} else {
			apply(poseStack, offRotX, offRotY, offRotZ, offScaleX, offScaleY, offScaleZ, offPosX, offPosY, offPosZ);
		}
	}

	public void applyArmTransforms(PoseStack poseStack) {
		if (!isActive()) {
			return;
		}
		apply(poseStack, armRotX, armRotY, armRotZ, armScaleX, armScaleY, armScaleZ, armPosX, armPosY, armPosZ);
	}

	private static void apply(
		PoseStack poseStack,
		NumberSetting rotX,
		NumberSetting rotY,
		NumberSetting rotZ,
		NumberSetting scaleX,
		NumberSetting scaleY,
		NumberSetting scaleZ,
		NumberSetting posX,
		NumberSetting posY,
		NumberSetting posZ
	) {
		poseStack.mulPose(Axis.XP.rotationDegrees(rotX.getValue().floatValue()));
		poseStack.mulPose(Axis.YP.rotationDegrees(rotY.getValue().floatValue()));
		poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ.getValue().floatValue()));
		poseStack.scale(scaleX.getValue().floatValue(), scaleY.getValue().floatValue(), scaleZ.getValue().floatValue());
		poseStack.translate(posX.getValue().floatValue(), posY.getValue().floatValue(), posZ.getValue().floatValue());
	}
}
