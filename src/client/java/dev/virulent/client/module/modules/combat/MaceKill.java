package dev.virulent.client.module.modules.combat;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.BooleanSetting;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spoofs a tall fall before mace attacks so smash damage applies.
 * Inspired by TrouserStreak / Meteor MaceKill settings.
 */
public final class MaceKill extends Module {
	private static MaceKill instance;

	private final BooleanSetting swingArm = addSetting(new BooleanSetting("Swing Arm", true));
	private final BooleanSetting disableWhenBlocked = addSetting(new BooleanSetting("Disable When Blocked", true));
	private final NumberSetting fallHeight = addSetting(new NumberSetting("Fall Height", 30.0, 1.0, 169.0, 1.0));
	private final NumberSetting spamPackets = addSetting(new NumberSetting("Spam Packets", 3.0, 1.0, 17.0, 1.0));
	private final BooleanSetting useOffset = addSetting(new BooleanSetting("Use Offset", true));
	private final NumberSetting horizontalOffset = addSetting(new NumberSetting("Horizontal Offset", 0.05, 0.0, 0.99, 0.01));
	private final NumberSetting yOffset = addSetting(new NumberSetting("Y Offset", 0.01, 0.0, 0.99, 0.01));
	private final BooleanSetting bypassTotems = addSetting(new BooleanSetting("Bypass Totems", false));
	private final NumberSetting attacks = addSetting(new NumberSetting("Attacks", 3.0, 1.0, 3.0, 1.0));
	private final NumberSetting heightIncrease = addSetting(new NumberSetting("Height Increase", 20.0, 1.0, 100.0, 1.0));
	private final BooleanSetting chatFeedback = addSetting(new BooleanSetting("Chat Feedback", true));

	private boolean sendingAttacks;
	private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
	private final Map<Vec3, Boolean> positionCache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Vec3, Boolean> eldest) {
			return size() > 256;
		}
	};

	public MaceKill() {
		super(
			"MaceKill",
			"Makes the Mace powerful when swung. Can also bypass totem usage.",
			Category.COMBAT,
			GLFW.GLFW_KEY_UNKNOWN
		);
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/**
	 * Called from {@link dev.virulent.client.mixin.ConnectionMixin} before attack packets leave.
	 */
	public static void onAttackPacket(ServerboundAttackPacket packet, CallbackInfo ci) {
		if (!isActive() || instance.sendingAttacks) {
			return;
		}
		instance.handleAttack(packet, ci);
	}

	private void handleAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
		if (mc().player == null || mc().level == null || mc().getConnection() == null) {
			return;
		}
		if (mc().player.isPassenger() || !mc().player.getMainHandItem().is(Items.MACE)) {
			return;
		}

		Entity entity = mc().level.getEntity(packet.entityId());
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		if (disableWhenBlocked.getValue() && (target.isBlocking() || target.isInvulnerable())) {
			return;
		}

		int baseBlocks = maxHeightAbovePlayer();
		if (baseBlocks <= 0) {
			feedback("No valid space above you to attack from.");
			return;
		}

		ci.cancel();

		Vec3 previousPos = mc().player.position();
		int currentHeight = baseBlocks;
		int attackCount = bypassTotems.getValue() ? attacks.getValue().intValue() : 1;

		for (int i = 0; i < spamPackets.getValue().intValue(); i++) {
			mc().player.connection.send(new ServerboundMovePlayerPacket.Rot(
				mc().player.getYRot(),
				mc().player.getXRot(),
				false,
				mc().player.horizontalCollision
			));
		}

		try {
			for (int i = 0; i < attackCount; i++) {
				int blocks = i == 0 ? baseBlocks : currentHeight;
				Vec3 peak = new Vec3(mc().player.getX(), mc().player.getY() + blocks, mc().player.getZ());
				sendMove(peak);
				sendMove(previousPos);
				mc().player.setPos(previousPos);

				sendingAttacks = true;
				if (swingArm.getValue()) {
					mc().player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
					mc().player.swing(InteractionHand.MAIN_HAND);
				}
				mc().player.connection.send(new ServerboundAttackPacket(target.getId()));
				currentHeight += heightIncrease.getValue().intValue();
			}

			positionCache.clear();

			if (useOffset.getValue()) {
				Vec3 offsetHome = offsetHome(previousPos);
				sendMove(offsetHome);
				mc().player.setPos(offsetHome);
			}

			if (chatFeedback.getValue()) {
				feedback("MaceKill x" + attackCount + " @ " + baseBlocks + " fall");
			}
		} finally {
			sendingAttacks = false;
		}
	}

	private void sendMove(Vec3 pos) {
		if (mc().getConnection() == null || mc().player == null) {
			return;
		}
		mc().player.connection.send(new ServerboundMovePlayerPacket.PosRot(
			pos,
			mc().player.getYRot(),
			mc().player.getXRot(),
			false,
			mc().player.horizontalCollision
		));
	}

	private Vec3 offsetHome(Vec3 base) {
		double dx = horizontalOffset.getValue();
		double dy = yOffset.getValue();
		List<Vec3> offsets = new ArrayList<>(List.of(
			base.add(dx, dy, 0),
			base.add(-dx, dy, 0),
			base.add(0, dy, dx),
			base.add(0, dy, -dx),
			base.add(dx, dy, dx),
			base.add(-dx, dy, -dx),
			base.add(-dx, dy, dx),
			base.add(dx, dy, -dx)
		));
		Collections.shuffle(offsets);
		for (Vec3 pos : offsets) {
			if (!invalid(pos)) {
				return pos;
			}
		}
		Vec3 noHorizontal = base.add(0, dy, 0);
		if (!invalid(noHorizontal)) {
			return noHorizontal;
		}
		return base;
	}

	private boolean invalid(Vec3 pos) {
		if (mc().level == null || mc().player == null) {
			return true;
		}

		BlockPos floored = BlockPos.containing(pos);
		int chunkX = floored.getX() >> 4;
		int chunkZ = floored.getZ() >> 4;
		if (mc().level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
			return true;
		}

		Boolean cached = positionCache.get(pos);
		if (cached != null) {
			return cached;
		}

		Vec3 delta = pos.subtract(mc().player.position());
		AABB box = mc().player.getBoundingBox().move(delta);

		mutablePos.set(floored);
		for (int x = -1; x <= 1; x++) {
			mutablePos.setX(floored.getX() + x);
			for (int y = -1; y <= 1; y++) {
				mutablePos.setY(floored.getY() + y);
				for (int z = -1; z <= 1; z++) {
					mutablePos.setZ(floored.getZ() + z);
					BlockState state = mc().level.getBlockState(mutablePos);
					if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
						|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
						|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)) {
						positionCache.put(pos, true);
						return true;
					}
				}
			}
		}

		for (Entity other : mc().level.getEntities(mc().player, box)) {
			if (other.canBeCollidedWith(mc().player)) {
				positionCache.put(pos, true);
				return true;
			}
		}

		boolean collides = mc().level.getBlockCollisions(mc().player, box).iterator().hasNext();
		positionCache.put(pos, collides);
		return collides;
	}

	private int maxHeightAbovePlayer() {
		return fallHeight.getValue().intValue();
	}

	private void feedback(String text) {
		if (mc().player != null) {
			mc().player.sendSystemMessage(Component.literal("[Virulent] " + text));
		}
	}
}
