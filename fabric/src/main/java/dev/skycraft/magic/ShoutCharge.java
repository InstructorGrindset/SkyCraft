package dev.skycraft.magic;

/**
 * The Shout key as Skyrim's shout key works (Shouts): pressed, it starts gathering words; let go, it shouts as many as it was
 * held for ({@link MagicRules#words}); held to the last word the player has unlocked, it shouts at once, and nothing more
 * until the key has come up (a key held down repeats its press). Pure: the client feeds it the key each tick.
 */
public final class ShoutCharge {
	private boolean holding;
	/** The key has been seen up since the last shout began: a press may start the next. */
	private boolean armed = true;
	private double since;

	/**
	 * One tick of the key. {@code pressed}: a press arrived since the last tick (one that may already have come up again, a
	 * tap); {@code down}: it's held now; {@code now} in seconds; {@code unlocked}: the words of the shout to use (0: none).
	 * Returns the words to shout now, or 0.
	 */
	public int update(boolean pressed, boolean down, double now, int unlocked, MagicRules.Settings s) {
		if (pressed && !this.holding && this.armed) {
			this.holding = true;
			this.armed = false;
			this.since = now;
		}
		if (!this.holding) {
			if (!down) {
				this.armed = true;
			}
			return 0;
		}
		double held = now - this.since;
		if (!down) {
			this.holding = false;
			this.armed = true;
			return MagicRules.words(held, unlocked, s);
		}
		if (MagicRules.fullyCharged(held, unlocked, s)) {
			this.holding = false; // armed again once the key is up
			return MagicRules.words(held, unlocked, s);
		}
		return 0;
	}

	/** The key is held and gathering words. */
	public boolean holding() {
		return this.holding;
	}

	/** Seconds the key has been held (0 when it isn't gathering words). */
	public double held(double now) {
		return this.holding ? now - this.since : 0.0;
	}

	/** Forgets the key (a screen opened, Skyrim took the player). */
	public void reset() {
		this.holding = false;
		this.armed = true;
	}
}
