package dev.virulent.client.gui.clickgui;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight ClickGUI animation state: open/close, expand, and soft hover.
 */
final class GuiMotion {
	private static final float OPEN_SPEED = 11.0f;
	private static final float EXPAND_SPEED = 14.0f;
	private static final float HOVER_SPEED = 18.0f;

	private long lastMs;
	private float frameDt = 1.0f / 60.0f;
	private float openProgress;
	private boolean closing;

	private Object expandTarget;
	private Object expandVisible;
	private float expandProgress;

	private final Map<Object, Float> hover = new IdentityHashMap<>();
	private final Set<Object> hoverTouched = new HashSet<>();

	void beginOpen() {
		closing = false;
		openProgress = 0.0f;
		lastMs = 0L;
		expandTarget = null;
		expandVisible = null;
		expandProgress = 0.0f;
		hover.clear();
		hoverTouched.clear();
	}

	/** Start close animation; returns false if already closing. */
	boolean beginClose() {
		if (closing) {
			return false;
		}
		closing = true;
		return true;
	}

	boolean isClosing() {
		return closing;
	}

	boolean isFullyClosed() {
		return closing && openProgress <= 0.001f;
	}

	float openEased() {
		return easeOutCubic(openProgress);
	}

	int openSlidePx(int maxPx) {
		return Math.round((1.0f - openEased()) * maxPx);
	}

	int openAlpha(int fullAlpha) {
		return Math.max(0, Math.min(255, Math.round(openEased() * fullAlpha)));
	}

	void setExpandTarget(Object target) {
		if (target == expandTarget) {
			expandTarget = null;
			return;
		}
		expandTarget = target;
		if (target != null) {
			expandVisible = target;
		}
	}

	void clearExpand() {
		expandTarget = null;
	}

	Object expandVisible() {
		return expandVisible;
	}

	Object expandTarget() {
		return expandTarget;
	}

	boolean isExpandVisible(Object key) {
		return key != null && key == expandVisible && expandProgress > 0.001f;
	}

	boolean hasExpand() {
		return expandVisible != null && expandProgress > 0.001f;
	}

	float expandEased() {
		return easeOutCubic(expandProgress);
	}

	/** 0 = browsing modules, 1 = settings panel fully covering. */
	float settingsBlend() {
		return hasExpand() ? expandEased() : 0.0f;
	}

	int expandedPixels(int fullHeight) {
		if (fullHeight <= 0 || expandVisible == null) {
			return 0;
		}
		return Math.max(0, Math.round(fullHeight * expandEased()));
	}

	/** Call once at the start of each GUI frame. */
	void beginFrame() {
		frameDt = deltaSeconds();
		hoverTouched.clear();

		if (closing) {
			openProgress = Math.max(0.0f, openProgress - frameDt * OPEN_SPEED);
		} else {
			openProgress = Math.min(1.0f, openProgress + frameDt * OPEN_SPEED);
		}

		float expandGoal = expandTarget != null ? 1.0f : 0.0f;
		expandProgress = approach(expandProgress, expandGoal, frameDt * EXPAND_SPEED);
		if (expandTarget != null) {
			expandVisible = expandTarget;
		} else if (expandProgress <= 0.001f) {
			expandVisible = null;
			expandProgress = 0.0f;
		}
	}

	/** Soft hover amount in 0..1 for this frame. */
	float hover(Object key, boolean hovered) {
		hoverTouched.add(key);
		float current = hover.getOrDefault(key, 0.0f);
		float next = approach(current, hovered ? 1.0f : 0.0f, frameDt * HOVER_SPEED);
		if (next <= 0.001f) {
			hover.remove(key);
			return 0.0f;
		}
		hover.put(key, next);
		return next;
	}

	/** Decay hover states that were not queried this frame. */
	void endFrame() {
		Iterator<Map.Entry<Object, Float>> it = hover.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Object, Float> entry = it.next();
			if (hoverTouched.contains(entry.getKey())) {
				continue;
			}
			float next = approach(entry.getValue(), 0.0f, frameDt * HOVER_SPEED);
			if (next <= 0.001f) {
				it.remove();
			} else {
				entry.setValue(next);
			}
		}
	}

	private float deltaSeconds() {
		long now = System.currentTimeMillis();
		if (lastMs == 0L) {
			lastMs = now;
			return 1.0f / 60.0f;
		}
		float dt = (now - lastMs) / 1000.0f;
		lastMs = now;
		return Math.min(0.05f, Math.max(0.0f, dt));
	}

	static float approach(float current, float target, float maxDelta) {
		if (current < target) {
			return Math.min(target, current + maxDelta);
		}
		if (current > target) {
			return Math.max(target, current - maxDelta);
		}
		return target;
	}

	static float easeOutCubic(float t) {
		float clamped = Math.max(0.0f, Math.min(1.0f, t));
		float inv = 1.0f - clamped;
		return 1.0f - inv * inv * inv;
	}
}
