package dev.virulent.client.setting;

public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double increment;

	public NumberSetting(String name, double defaultValue, double min, double max, double increment) {
		super(name, clamp(defaultValue, min, max));
		this.min = min;
		this.max = max;
		this.increment = increment;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getIncrement() {
		return increment;
	}

	@Override
	public void setValue(Double value) {
		super.setValue(clamp(value, min, max));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
