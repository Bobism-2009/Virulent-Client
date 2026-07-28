package dev.virulent.client.util;

import dev.virulent.client.mixin.ClientInputAccessor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Forces forward/jump/sprint onto {@link ClientInput} after KeyboardInput rebuilds
 * key state from KeyMappings, so bot movement is not lost mid-tick.
 */
public final class BotMovement {
	private static boolean forward;
	private static boolean jump;
	private static boolean sprint;

	private BotMovement() {
	}

	public static void set(boolean forwardPressed, boolean jumpPressed, boolean sprintPressed) {
		forward = forwardPressed;
		jump = jumpPressed;
		sprint = sprintPressed;
	}

	public static void clear() {
		forward = false;
		jump = false;
		sprint = false;
	}

	public static boolean isActive() {
		return forward || jump || sprint;
	}

	public static void apply(ClientInput input, ClientInputAccessor access) {
		if (!isActive()) {
			return;
		}
		Input current = input.keyPresses;
		boolean fwd = forward || current.forward();
		boolean back = !forward && current.backward();
		boolean jumpPressed = jump || current.jump();
		boolean sprintPressed = sprint || current.sprint();
		input.keyPresses = new Input(
			fwd,
			back,
			current.left(),
			current.right(),
			jumpPressed,
			current.shift(),
			sprintPressed
		);
		float forwardImpulse = impulse(fwd, back);
		float strafeImpulse = impulse(current.left(), current.right());
		access.virulent$setMoveVector(new Vec2(strafeImpulse, forwardImpulse).normalized());
	}

	private static float impulse(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0f;
		}
		return positive ? 1.0f : -1.0f;
	}
}
