package dev.skycraft.magic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Shouts and casts sent to Skyrim and not answered yet (Shouts, Spells): each gets a request number Skyrim answers with
 * (Proto.IN_SHOUT_RESULT, IN_CAST_RESULT), so the answer finds its shout or spell. One unanswered past a few seconds is
 * given up on: an older Skyrim plugin never answers. Pure.
 */
public final class MagicRequests {
	/** What a request asked for. {@code shout}: a shout (else a spell); {@code formId}: which; {@code name}: its name, for the HUD. */
	public record Pending(int id, boolean shout, int formId, String name, double sent) {
	}

	/** Requests kept at most (the oldest go first): a stream of pulses Skyrim never answers can't grow without end. */
	private static final int MOST = 64;

	private final Map<Integer, Pending> open = new LinkedHashMap<>();
	private int next;

	/** Starts numbering somewhere new ({@code seed}), so a late answer to the last run's requests can't be taken for this run's. */
	public MagicRequests(int seed) {
		this.next = seed;
	}

	/** A new request; returns its number (never 0). */
	public int open(boolean shout, int formId, String name, double now) {
		if (++this.next == 0) {
			this.next = 1;
		}
		if (this.open.size() >= MOST) {
			Iterator<Integer> oldest = this.open.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
		this.open.put(this.next, new Pending(this.next, shout, formId, name, now));
		return this.next;
	}

	/** The request Skyrim answered (and forgets it), or null: one given up on, or not ours. */
	public @Nullable Pending answered(int id) {
		return this.open.remove(id);
	}

	/** Takes out the requests sent more than {@code timeout} seconds before {@code now}, oldest first. */
	public List<Pending> expired(double now, double timeout) {
		List<Pending> out = new ArrayList<>();
		for (Iterator<Pending> it = this.open.values().iterator(); it.hasNext();) {
			Pending p = it.next();
			if (now - p.sent() > timeout) {
				out.add(p);
				it.remove();
			}
		}
		return out;
	}

	/** Requests waiting for an answer. */
	public int waiting() {
		return this.open.size();
	}

	/** Forgets every request (Skyrim restarted or the link went down). */
	public void clear() {
		this.open.clear();
	}
}
