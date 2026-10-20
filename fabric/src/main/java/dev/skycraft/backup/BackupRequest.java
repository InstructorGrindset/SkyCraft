package dev.skycraft.backup;

import dev.skycraft.link.Proto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim's paired backup request (Proto.IN_PAIRED_BACKUP) put back together from its input events: a first event
 * (code 0: the request id and the text's length in bytes), then the text 12 bytes at a time (code 1, 2, ...). The
 * text is {@code "<stamp>|<save name>"}: the stamp names both halves' backups, the save name is the Skyrim save
 * Skyrim copied. Pure; one instance per input ring, fed in order.
 */
public final class BackupRequest {
	/** A whole request: Skyrim's id for it, the shared stamp ({@link BackupNames#STAMP}) and the Skyrim save's name. */
	public record Request(int id, String stamp, String saveName) {
	}

	private int id;
	private byte @Nullable [] text;
	private int nextPart;

	/**
	 * Takes the next IN_PAIRED_BACKUP event. Returns the request once its last part is in, else null. A part out of turn
	 * (one went missing in a full ring) drops the request it belonged to; a malformed text is dropped too.
	 */
	public @Nullable Request accept(int code, int a, int b, int c) {
		if (code == 0) {
			if (b <= 0 || b > Proto.PAIRED_BACKUP_TEXT_MAX) {
				this.text = null;
				return null;
			}
			this.id = a;
			this.text = new byte[b];
			this.nextPart = 1;
			return null;
		}
		byte[] bytes = this.text;
		if (bytes == null || code != this.nextPart) {
			this.text = null;
			return null;
		}
		int at = (code - 1) * 12;
		int[] words = { a, b, c };
		for (int i = 0; i < 12 && at + i < bytes.length; i++) {
			bytes[at + i] = (byte) (words[i / 4] >>> (8 * (i % 4)));
		}
		this.nextPart++;
		if (at + 12 < bytes.length) {
			return null;
		}
		this.text = null;
		return parse(this.id, new String(bytes, StandardCharsets.UTF_8));
	}

	/** {@code "<stamp>|<save name>"} as a request, or null if the stamp isn't one. */
	static @Nullable Request parse(int id, String text) {
		int bar = text.indexOf('|');
		if (bar < 0 || !BackupNames.isStamp(text.substring(0, bar))) {
			return null;
		}
		return new Request(id, text.substring(0, bar), text.substring(bar + 1));
	}

	/**
	 * The input events Skyrim sends for a request, as {code, a, b, c} (what skse/src/Backups.cpp writes; for tests). A text
	 * longer than {@link Proto#PAIRED_BACKUP_TEXT_MAX} bytes is cut there.
	 */
	public static List<int[]> encode(int id, String stamp, String saveName) {
		byte[] bytes = (stamp + "|" + saveName).getBytes(StandardCharsets.UTF_8);
		int length = Math.min(bytes.length, Proto.PAIRED_BACKUP_TEXT_MAX);
		List<int[]> events = new ArrayList<>();
		events.add(new int[] { 0, id, length, 0 });
		for (int part = 1, at = 0; at < length; part++, at += 12) {
			int[] words = new int[3];
			for (int i = 0; i < 12 && at + i < length; i++) {
				words[i / 4] |= (bytes[at + i] & 0xFF) << (8 * (i % 4));
			}
			events.add(new int[] { part, words[0], words[1], words[2] });
		}
		return events;
	}
}
