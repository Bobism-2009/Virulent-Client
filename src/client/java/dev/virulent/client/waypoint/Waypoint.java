package dev.virulent.client.waypoint;

import com.google.gson.JsonObject;

public final class Waypoint {
	private String name;
	private double x;
	private double y;
	private double z;
	private String dimension;
	private int color;

	public Waypoint(String name, double x, double y, double z, String dimension, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.color = color;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public void setPos(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public String getDimension() {
		return dimension;
	}

	public void setDimension(String dimension) {
		this.dimension = dimension;
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("name", name);
		object.addProperty("x", x);
		object.addProperty("y", y);
		object.addProperty("z", z);
		object.addProperty("dimension", dimension);
		object.addProperty("color", color);
		return object;
	}

	public static Waypoint fromJson(JsonObject object) {
		String name = object.has("name") ? object.get("name").getAsString() : "Waypoint";
		double x = object.has("x") ? object.get("x").getAsDouble() : 0.0;
		double y = object.has("y") ? object.get("y").getAsDouble() : 0.0;
		double z = object.has("z") ? object.get("z").getAsDouble() : 0.0;
		String dimension = object.has("dimension") ? object.get("dimension").getAsString() : "minecraft:overworld";
		int color = object.has("color") ? object.get("color").getAsInt() : 0xFF4CFF66;
		return new Waypoint(name, x, y, z, dimension, color);
	}
}
