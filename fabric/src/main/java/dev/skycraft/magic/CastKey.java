package dev.skycraft.magic;

/**
 * The Cast spell key (Spells): a press casts the selected spell once; a concentration spell is cast again every
 * {@code pulse_seconds} while the key stays down, each pulse paying for its stretch of the spell, until the key comes up
 * (with {@code concentration_pulses} off, once each press like the others). A key held down repeats its press, so a new cast
 * needs the key to have come up first. Pure: the client feeds it the key each tick.
 */
public final class CastKey {
	/** Nothing to send this tick. */
	public static final int NONE = 0;
	/** The press's cast (a fire-and-forget spell, or a concentration spell's first pulse). */
	public static final int CAST = 1;
	/** A concentration spell's next pulse while the key is held. */
	public static final int PULSE = 2;

	private boolean holding;
	private boolean armed = true;
	private double nextPulse;

	/**
	 * One tick of the key. {@code pressed}: a press arrived since the last tick; {@code down}: it's held now; {@code now} in
	 * seconds; {@code concentration}: the selected spell is a concentration spell. Returns NONE, CAST or PULSE.
	 */
	public int update(boolean pressed, boolean down, double now, boolean concentration, MagicRules.Settings s) {
		if (pressed && !this.holding && this.armed) {
			this.armed = false;
			this.holding = down && concentration && s.concentrationPulses();
			this.nextPulse = now + s.pulseSeconds();
			if (!down) {
				this.armed = true; // a tap between two ticks
			}
			return CAST;
		}
		if (!down) {
			this.holding = false;
			this.armed = true;
			return NONE;
		}
		if (this.holding && concentration && now >= this.nextPulse) {
			// The next pulse is due from the last one's due time, so pulses keep their rhythm with ticks that come late; after a
			// stall it starts over from now rather than catching up in a burst.
			this.nextPulse += s.pulseSeconds();
			if (this.nextPulse <= now) {
				this.nextPulse = now + s.pulseSeconds();
			}
			return PULSE;
		}
		return NONE;
	}

	/** The key is held on a concentration spell, pulsing. */
	public boolean pulsing() {
		return this.holding;
	}

	/** Forgets the key (a screen opened, Skyrim took the player). */
	public void reset() {
		this.holding = false;
		this.armed = true;
	}
}
