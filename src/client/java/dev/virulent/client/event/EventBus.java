package dev.virulent.client.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class EventBus {
	private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();

	public <T extends Event> void subscribe(Class<T> eventType, Consumer<T> listener) {
		listeners.computeIfAbsent(eventType, type -> new ArrayList<>()).add(listener);
	}

	public void post(Event event) {
		List<Consumer<?>> eventListeners = listeners.get(event.getClass());
		if (eventListeners == null) {
			return;
		}

		for (Consumer<?> listener : eventListeners) {
			@SuppressWarnings("unchecked")
			Consumer<Event> typedListener = (Consumer<Event>) listener;
			typedListener.accept(event);
		}
	}
}
