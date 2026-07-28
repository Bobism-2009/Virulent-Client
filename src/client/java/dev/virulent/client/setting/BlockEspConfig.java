package dev.virulent.client.setting;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Objects;

public final class BlockEspConfig {
	public enum ShapeMode {
		LINES,
		SIDES,
		BOTH;

		public String label() {
			return switch (this) {
				case LINES -> "Lines";
				case SIDES -> "Sides";
				case BOTH -> "Both";
			};
		}

		public static ShapeMode fromLabel(String label) {
			return switch (label.toLowerCase(Locale.ROOT)) {
				case "sides" -> SIDES;
				case "both" -> BOTH;
				default -> LINES;
			};
		}

		public ShapeMode next() {
			ShapeMode[] values = values();
			return values[(ordinal() + 1) % values.length];
		}
	}

	private ShapeMode shapeMode;
	private int lineColor;
	private int sideColor;
	private boolean tracer;
	private int tracerColor;

	public BlockEspConfig(ShapeMode shapeMode, int lineColor, int sideColor, boolean tracer, int tracerColor) {
		this.shapeMode = shapeMode;
		this.lineColor = lineColor;
		this.sideColor = sideColor;
		this.tracer = tracer;
		this.tracerColor = tracerColor;
	}

	public static BlockEspConfig defaults() {
		return new BlockEspConfig(
			ShapeMode.LINES,
			0xFF00FFC8,
			0x1900FFC8,
			true,
			0x7D00FFC8
		);
	}

	public BlockEspConfig copy() {
		return new BlockEspConfig(shapeMode, lineColor, sideColor, tracer, tracerColor);
	}

	public ShapeMode getShapeMode() {
		return shapeMode;
	}

	public void setShapeMode(ShapeMode shapeMode) {
		this.shapeMode = shapeMode;
	}

	public int getLineColor() {
		return lineColor;
	}

	public void setLineColor(int lineColor) {
		this.lineColor = lineColor;
	}

	public int getSideColor() {
		return sideColor;
	}

	public void setSideColor(int sideColor) {
		this.sideColor = sideColor;
	}

	public boolean isTracer() {
		return tracer;
	}

	public void setTracer(boolean tracer) {
		this.tracer = tracer;
	}

	public int getTracerColor() {
		return tracerColor;
	}

	public void setTracerColor(int tracerColor) {
		this.tracerColor = tracerColor;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("shapeMode", shapeMode.name());
		json.addProperty("lineColor", lineColor);
		json.addProperty("sideColor", sideColor);
		json.addProperty("tracer", tracer);
		json.addProperty("tracerColor", tracerColor);
		return json;
	}

	public static BlockEspConfig fromJson(JsonObject json) {
		BlockEspConfig config = defaults();
		if (json == null) {
			return config;
		}
		if (json.has("shapeMode")) {
			try {
				config.shapeMode = ShapeMode.valueOf(json.get("shapeMode").getAsString());
			} catch (IllegalArgumentException ignored) {
			}
		}
		if (json.has("lineColor")) {
			config.lineColor = json.get("lineColor").getAsInt();
		}
		if (json.has("sideColor")) {
			config.sideColor = json.get("sideColor").getAsInt();
		}
		if (json.has("tracer")) {
			config.tracer = json.get("tracer").getAsBoolean();
		}
		if (json.has("tracerColor")) {
			config.tracerColor = json.get("tracerColor").getAsInt();
		}
		return config;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof BlockEspConfig other)) {
			return false;
		}
		return shapeMode == other.shapeMode
			&& lineColor == other.lineColor
			&& sideColor == other.sideColor
			&& tracer == other.tracer
			&& tracerColor == other.tracerColor;
	}

	@Override
	public int hashCode() {
		return Objects.hash(shapeMode, lineColor, sideColor, tracer, tracerColor);
	}
}
