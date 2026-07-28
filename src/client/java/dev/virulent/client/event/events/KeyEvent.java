package dev.virulent.client.event.events;

import dev.virulent.client.event.Event;

public final class KeyEvent implements Event {
	private final int key;
	private final int action;

	public KeyEvent(int key, int action) {
		this.key = key;
		this.action = action;
	}

	public int getKey() {
		return key;
	}

	public int getAction() {
		return action;
	}

	public boolean isPressed() {
		return action == 1;
	}

	public boolean isReleased() {
		return action == 0;
	}
}
