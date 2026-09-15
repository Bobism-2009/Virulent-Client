package dev.virulent.client.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe writes shared by every JSON store under {@code .minecraft/virulent}.
 *
 * <p>Writing straight over a live config means a crash mid-write leaves a truncated file that the
 * next launch cannot parse, which used to take the whole client down before any in-game recovery
 * was reachable. Every writer here stages the new contents next to the target and renames it into
 * place instead.
 */
public final class ConfigFiles {
	/** Kept after the {@code .json} extension so {@code *.json} directory globs skip temp files. */
	private static final String TMP_SUFFIX = ".tmp";

	private ConfigFiles() {
	}

	/**
	 * Writes to {@code <target>.tmp} and renames it over {@code target}, so a crash mid-write
	 * leaves the previous file intact instead of a truncated one.
	 */
	public static void writeAtomically(Path target, String contents) throws IOException {
		Path tmp = tempFor(target);
		try {
			Files.writeString(tmp, contents);
			moveIntoPlace(tmp, target);
		} finally {
			deleteQuietly(tmp);
		}
	}

	/** Same guarantee as {@link #writeAtomically} for file-to-file copies. */
	public static void copyAtomically(Path source, Path target) throws IOException {
		Path tmp = tempFor(target);
		try {
			Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
			moveIntoPlace(tmp, target);
		} finally {
			deleteQuietly(tmp);
		}
	}

	private static Path tempFor(Path target) {
		return target.resolveSibling(target.getFileName() + TMP_SUFFIX);
	}

	private static void moveIntoPlace(Path tmp, Path target) throws IOException {
		try {
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteQuietly(Path tmp) {
		try {
			Files.deleteIfExists(tmp);
		} catch (IOException ignored) {
			// Leftover temp file is harmless; the next save overwrites it.
		}
	}
}
