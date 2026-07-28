package dev.virulent.client.waypoint;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Share / import helpers for waypoint coordinates.
 */
public final class WaypointCoords {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";
	public static final String DEATH_NAME = "Death";
	public static final int DEATH_COLOR = 0xFFFF6B6B;

	private static final Pattern VIRULENT = Pattern.compile(
		"(?i)^virulent:([^:]+):(-?\\d+(?:\\.\\d+)?):(-?\\d+(?:\\.\\d+)?):(-?\\d+(?:\\.\\d+)?):([^:]+):(-?\\d+)$"
	);
	private static final Pattern NAMED_AT = Pattern.compile(
		"(?i)^(.+?)\\s*@\\s*(-?\\d+(?:\\.\\d+)?)[\\s,]+(-?\\d+(?:\\.\\d+)?)[\\s,]+(-?\\d+(?:\\.\\d+)?)(?:\\s*\\[([^\\]]+)])?$"
	);
	private static final Pattern BARE_COORDS = Pattern.compile(
		"^\\s*(-?\\d+(?:\\.\\d+)?)[\\s,]+(-?\\d+(?:\\.\\d+)?)[\\s,]+(-?\\d+(?:\\.\\d+)?)\\s*$"
	);
	private static final Pattern XAERO = Pattern.compile(
		"(?i)^xaero-waypoint:([^:]*):[^:]*:(-?\\d+):(-?\\d+):(-?\\d+):"
	);

	private WaypointCoords() {
	}

	public static String formatShare(Waypoint waypoint) {
		return String.format(
			Locale.ROOT,
			"virulent:%s:%.1f:%.1f:%.1f:%s:%d",
			sanitizeName(waypoint.getName()),
			waypoint.getX(),
			waypoint.getY(),
			waypoint.getZ(),
			waypoint.getDimension(),
			waypoint.getColor()
		);
	}

	public static String formatReadable(Waypoint waypoint) {
		return String.format(
			Locale.ROOT,
			"%s @ %d %d %d [%s]",
			waypoint.getName(),
			Math.round(waypoint.getX()),
			Math.round(waypoint.getY()),
			Math.round(waypoint.getZ()),
			waypoint.getDimension()
		);
	}

	public static Optional<Waypoint> parse(String raw, String fallbackDimension, int fallbackColor) {
		if (raw == null) {
			return Optional.empty();
		}
		String text = raw.trim();
		if (text.isEmpty()) {
			return Optional.empty();
		}

		// Prefer first line if multi-line paste.
		int nl = text.indexOf('\n');
		if (nl >= 0) {
			text = text.substring(0, nl).trim();
		}

		Matcher virulent = VIRULENT.matcher(text);
		if (virulent.matches()) {
			return Optional.of(new Waypoint(
				sanitizeName(virulent.group(1)),
				Double.parseDouble(virulent.group(2)),
				Double.parseDouble(virulent.group(3)),
				Double.parseDouble(virulent.group(4)),
				normalizeDimension(virulent.group(5)),
				(int) Long.parseLong(virulent.group(6))
			));
		}

		Matcher named = NAMED_AT.matcher(text);
		if (named.matches()) {
			String dim = named.group(5) != null ? normalizeDimension(named.group(5)) : fallbackDimension;
			return Optional.of(new Waypoint(
				sanitizeName(named.group(1)),
				Double.parseDouble(named.group(2)),
				Double.parseDouble(named.group(3)),
				Double.parseDouble(named.group(4)),
				dim,
				fallbackColor
			));
		}

		Matcher xaero = XAERO.matcher(text);
		if (xaero.find()) {
			String name = xaero.group(1).isBlank() ? "Import" : sanitizeName(xaero.group(1));
			return Optional.of(new Waypoint(
				name,
				Double.parseDouble(xaero.group(2)),
				Double.parseDouble(xaero.group(3)),
				Double.parseDouble(xaero.group(4)),
				fallbackDimension,
				fallbackColor
			));
		}

		Matcher bare = BARE_COORDS.matcher(text);
		if (bare.matches()) {
			return Optional.of(new Waypoint(
				"Import",
				Double.parseDouble(bare.group(1)),
				Double.parseDouble(bare.group(2)),
				Double.parseDouble(bare.group(3)),
				fallbackDimension,
				fallbackColor
			));
		}

		return Optional.empty();
	}

	public static Optional<Waypoint> convertOwNether(Waypoint source, int color) {
		String dim = source.getDimension();
		double x = source.getX();
		double y = source.getY();
		double z = source.getZ();
		String name = source.getName();

		if (isOverworld(dim)) {
			return Optional.of(new Waypoint(
				uniqueConvertedName(name, "Nether"),
				x / 8.0,
				y,
				z / 8.0,
				NETHER,
				color
			));
		}
		if (isNether(dim)) {
			return Optional.of(new Waypoint(
				uniqueConvertedName(name, "OW"),
				x * 8.0,
				y,
				z * 8.0,
				OVERWORLD,
				color
			));
		}
		return Optional.empty();
	}

	/** Scaled position of a waypoint as seen from the opposite OW/Nether dimension, or empty. */
	public static Optional<double[]> crossDimPos(Waypoint waypoint, String viewerDimension) {
		if (waypoint.getDimension().equals(viewerDimension)) {
			return Optional.empty();
		}
		if (isOverworld(viewerDimension) && isNether(waypoint.getDimension())) {
			return Optional.of(new double[] {
				waypoint.getX() * 8.0,
				waypoint.getY(),
				waypoint.getZ() * 8.0
			});
		}
		if (isNether(viewerDimension) && isOverworld(waypoint.getDimension())) {
			return Optional.of(new double[] {
				waypoint.getX() / 8.0,
				waypoint.getY(),
				waypoint.getZ() / 8.0
			});
		}
		return Optional.empty();
	}

	public static boolean isOverworld(String dimension) {
		return OVERWORLD.equals(dimension) || "overworld".equalsIgnoreCase(shortDim(dimension));
	}

	public static boolean isNether(String dimension) {
		return NETHER.equals(dimension) || "the_nether".equalsIgnoreCase(shortDim(dimension))
			|| "nether".equalsIgnoreCase(shortDim(dimension));
	}

	public static String normalizeDimension(String raw) {
		String value = raw.trim().toLowerCase(Locale.ROOT);
		return switch (value) {
			case "overworld", "ow", "minecraft:overworld" -> OVERWORLD;
			case "nether", "the_nether", "minecraft:the_nether" -> NETHER;
			case "end", "the_end", "minecraft:the_end" -> END;
			default -> raw.contains(":") ? raw.trim() : "minecraft:" + value;
		};
	}

	public static String shortDim(String dimension) {
		int slash = dimension.indexOf(':');
		return slash >= 0 ? dimension.substring(slash + 1) : dimension;
	}

	private static String uniqueConvertedName(String name, String suffix) {
		String base = name;
		String marker = " (" + suffix + ")";
		if (base.endsWith(marker)) {
			return base;
		}
		if (base.length() + marker.length() > 24) {
			base = base.substring(0, Math.max(1, 24 - marker.length()));
		}
		return base + marker;
	}

	private static String sanitizeName(String name) {
		String cleaned = name.replace(':', ' ').trim();
		if (cleaned.isEmpty()) {
			return "Waypoint";
		}
		return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
	}
}
