package dev.skycraft.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A world folder into one zip, the way Minecraft's own world backup lays it out: every file under a folder named like
 * the world, except {@code session.lock} (the running game's lock, which can't be read). The zip is written beside the
 * target as {@code <target>.part} and only takes the target's name once it's whole, so a half written backup never looks
 * like one. Files are read through NIO, which lets the running game replace them meanwhile (level.dat is saved by
 * renaming a new file over it). Pure; no Minecraft types.
 */
public final class WorldZip {
	/** What went into the zip: its size in bytes, the files in it, and files that vanished or couldn't be read. */
	public record Result(long bytes, int files, int skipped) {
	}

	private WorldZip() {
	}

	/** Zips {@code world} into {@code target}, replacing any zip already there; {@code comment} is the zip's comment. */
	public static Result zip(Path world, Path target, String comment) throws IOException {
		Path root = world.toAbsolutePath().normalize();
		String top = root.getFileName() == null ? "world" : root.getFileName().toString();
		Path part = target.resolveSibling(target.getFileName() + BackupNames.PART);
		int[] counts = new int[2];
		try (OutputStream file = Files.newOutputStream(part); ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(file))) {
			zip.setComment(comment);
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
					if (!attributes.isRegularFile() || path.getFileName().toString().equals("session.lock")) {
						return FileVisitResult.CONTINUE;
					}
					String name = top + "/" + root.relativize(path).toString().replace('\\', '/');
					InputStream opened;
					try {
						opened = Files.newInputStream(path);
					} catch (FileSystemException e) {
						counts[1]++; // gone since the folder was listed (a temporary file the game just renamed), or locked
						return FileVisitResult.CONTINUE;
					}
					try (InputStream in = opened) {
						ZipEntry entry = new ZipEntry(name);
						entry.setLastModifiedTime(attributes.lastModifiedTime());
						zip.putNextEntry(entry);
						in.transferTo(zip);
						zip.closeEntry();
						counts[0]++;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path path, IOException e) {
					counts[1]++;
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(part);
			throw e;
		}
		try {
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return new Result(Files.size(target), counts[0], counts[1]);
	}
}
