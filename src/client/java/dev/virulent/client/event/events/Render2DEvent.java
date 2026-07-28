package dev.virulent.client.event.events;

import dev.virulent.client.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Render2DEvent implements Event {
	private final GuiGraphicsExtractor context;
	private final Object tickCounter;

	public Render2DEvent(GuiGraphicsExtractor context, Object tickCounter) {
		this.context = context;
		this.tickCounter = tickCounter;
	}

	public GuiGraphicsExtractor getContext() {
		return context;
	}

	public Object getTickCounter() {
		return tickCounter;
	}
}
