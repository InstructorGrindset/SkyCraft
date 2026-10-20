package dev.skycraft.weather;

import static dev.skycraft.link.Proto.WEATHER_CLEAR;
import static dev.skycraft.link.Proto.WEATHER_CLOUDY;
import static dev.skycraft.link.Proto.WEATHER_NONE;
import static dev.skycraft.link.Proto.WEATHER_RAIN;
import static dev.skycraft.link.Proto.WEATHER_REFUSED;
import static dev.skycraft.link.Proto.WEATHER_SNOW;
import static dev.skycraft.link.Proto.WEATHER_STORM;

import java.util.List;
import java.util.Locale;

/**
 * Weather sync's rules (switch {@code weather_sync}), pure Java and unit tested without Minecraft; SkyWeather runs them on
 * the server.
 *
 * <ul>
 * <li>Skyrim to Minecraft: Skyrim's weather kind where its player is (Proto.WEATHER_*) picks Minecraft's weather:
 * clear and cloudy skies are clear, rain and snow are rain (Minecraft's rain is snow where its biome is cold), a
 * thunderstorm is thunder. Indoors Skyrim says nothing and Minecraft's weather stays as it is.
 * <li>Minecraft to Skyrim: a change of Minecraft's weather that SkyCraft didn't make (a player's /weather, a mod), once
 * it has held for {@link Watch#STABLE}, is a request: Skyrim switches to the nearest weather of that kind its region has
 * (Weather.cpp) and acknowledges it. Until then Minecraft keeps the player's weather; after, it follows Skyrim's again.
 * Only Minecraft ever asks and only for changes it didn't make itself, and Skyrim answers each request once, so the two
 * never take turns changing each other's weather.
 * <li>Snow settles: while snow falls in Skyrim, snow layers slowly settle on the tops of Minecraft blocks open to the
 * sky near the player in the mirror, at most one layer a block each pass ({@link #nextLayers}); in clear weather they
 * melt again. Skyrim's rain waters farmland open to the sky. Numbers in {@code skycraft-weather.txt} ({@link Settings}).
 * </ul>
 */
public final class WeatherRules {
	/** Minecraft's three weathers. */
	public static final int MC_CLEAR = 0, MC_RAIN = 1, MC_THUNDER = 2;
	/** No weather known. */
	public static final int UNKNOWN = -1;

	private WeatherRules() {
	}

	/** Minecraft's weather for a Skyrim weather kind, or {@link #UNKNOWN} for WEATHER_NONE (leave Minecraft's as it is). */
	public static int minecraftFor(int skyrimKind) {
		return switch (skyrimKind) {
			case WEATHER_CLEAR, WEATHER_CLOUDY -> MC_CLEAR;
			case WEATHER_RAIN, WEATHER_SNOW -> MC_RAIN;
			case WEATHER_STORM -> MC_THUNDER;
			default -> UNKNOWN;
		};
	}

	/**
	 * The Skyrim weather kind to ask for when Minecraft's weather turned to {@code mcWeather}. Skyrim takes the nearest its
	 * region has: rain is snow in a region that only snows, a storm rain or snow where there are no storms.
	 */
	public static int skyrimFor(int mcWeather) {
		return switch (mcWeather) {
			case MC_CLEAR -> WEATHER_CLEAR;
			case MC_RAIN -> WEATHER_RAIN;
			case MC_THUNDER -> WEATHER_STORM;
			default -> WEATHER_NONE;
		};
	}

	/** Minecraft's weather as /weather names it. */
	public static String command(int mcWeather) {
		return switch (mcWeather) {
			case MC_RAIN -> "rain";
			case MC_THUNDER -> "thunder";
			default -> "clear";
		};
	}

	/** A Skyrim weather kind in words, for messages and the log. */
	public static String kindName(int skyrimKind) {
		return switch (skyrimKind) {
			case WEATHER_CLEAR -> "clear skies";
			case WEATHER_CLOUDY -> "clouds";
			case WEATHER_RAIN -> "rain";
			case WEATHER_SNOW -> "snow";
			case WEATHER_STORM -> "thunderstorms";
			default -> "no weather";
		};
	}

	/**
	 * Notices Minecraft's weather turning to one SkyCraft didn't set. After SkyCraft sets it, rain and thunder fade in and
	 * out over several seconds, so its own change counts as settled only after {@link #GRACE}; anyone else's has to hold
	 * for {@link #STABLE} (the fade from clear to thunder passes through rain).
	 */
	public static final class Watch {
		/** Ticks after SkyCraft set the weather when what Minecraft shows is still its own change fading in. */
		public static final long GRACE = 200;
		/** Ticks someone else's weather must hold before Skyrim is asked for it. */
		public static final long STABLE = 100;

		private int expected = UNKNOWN;
		private long graceUntil = Long.MIN_VALUE;
		private int seen = UNKNOWN;
		private long seenSince;

		/** The weather SkyCraft last set or last took as Minecraft's, {@link #UNKNOWN} before the first. */
		public int expected() {
			return this.expected;
		}

		/** SkyCraft set Minecraft's weather to {@code mcWeather} at tick {@code now}. */
		public void set(int mcWeather, long now) {
			this.expected = mcWeather;
			this.graceUntil = now + GRACE;
			this.seen = UNKNOWN;
		}

		/**
		 * Minecraft's weather is {@code observed} at tick {@code now}. Returns the weather someone else turned it to, once,
		 * when it has held for {@link #STABLE} (it's then the expected weather), else {@link #UNKNOWN}.
		 */
		public int observe(int observed, long now) {
			if (this.expected == UNKNOWN || observed == this.expected || now < this.graceUntil) {
				this.seen = UNKNOWN;
				return UNKNOWN;
			}
			if (observed != this.seen) {
				this.seen = observed;
				this.seenSince = now;
				return UNKNOWN;
			}
			if (now - this.seenSince < STABLE) {
				return UNKNOWN;
			}
			this.expected = observed;
			this.seen = UNKNOWN;
			return observed;
		}

		public void reset() {
			this.expected = UNKNOWN;
			this.graceUntil = Long.MIN_VALUE;
			this.seen = UNKNOWN;
		}
	}

	/**
	 * Minecraft's weather requests to Skyrim (McState weatherSeq and weatherWant): at most one open at a time, answered when
	 * SkyState's weatherAck names it, given up after {@link #TIMEOUT}.
	 */
	public static final class Asks {
		/** Ticks to wait for Skyrim's answer (an older plugin never answers). */
		public static final long TIMEOUT = 300;
		/** What {@link #check} found. */
		public static final int IDLE = 0, WAITING = 1, ANSWERED = 2, REFUSED = 3, TIMED_OUT = 4;

		private int seq;
		private int want = WEATHER_NONE;
		private boolean open;
		private long since;

		/** {@code firstSeq}: where the numbers start (a fresh one each run, so Skyrim can't take one for its last answer). */
		public Asks(int firstSeq) {
			this.seq = firstSeq;
		}

		/** Asks Skyrim for weather kind {@code want} (Proto.WEATHER_*) at tick {@code now}; returns the request's number. */
		public int open(int want, long now) {
			this.seq++;
			if (this.seq == 0) {
				this.seq = 1;
			}
			this.want = want;
			this.open = true;
			this.since = now;
			return this.seq;
		}

		/** A request is open: Minecraft keeps the weather it was given until Skyrim answers. */
		public boolean waiting() {
			return this.open;
		}

		/** The kind the open (or last) request asked for. */
		public int want() {
			return this.want;
		}

		/** SkyState's {@code ack} and weather flags at tick {@code now}: IDLE, WAITING, or what became of the open request. */
		public int check(int ack, int flags, long now) {
			if (!this.open) {
				return IDLE;
			}
			if (ack == this.seq) {
				this.open = false;
				return (flags & WEATHER_REFUSED) != 0 ? REFUSED : ANSWERED;
			}
			if (now - this.since > TIMEOUT) {
				this.open = false;
				return TIMED_OUT;
			}
			return WAITING;
		}

		/** For McState: the request's number in the low 32 bits, the kind asked for above them (WEATHER_NONE once closed). */
		public long packed() {
			return (this.seq & 0xFFFFFFFFL) | ((long) (this.open ? this.want : WEATHER_NONE) << 32);
		}

		public void reset() {
			this.open = false;
			this.want = WEATHER_NONE;
		}
	}

	// ---- snow settling --------------------------------------------------------------------------

	/** Settings from {@code config/skycraft-weather.txt}. */
	public record Settings(int settleEvery, int columns, int radius, double settleChance, int maxLayers, double meltChance, boolean rainWaters) {
		public static final Settings DEFAULTS = new Settings(40, 64, 16, 0.25, 2, 0.1, true);
	}

	/** Snow layers a block can hold (Minecraft's). */
	public static final int MAX_SNOW_LAYERS = 8;

	public static final String DEFAULT_TEXT = """
		# SkyCraft weather: Skyrim's weather is Minecraft's and the other way round. The switch is weather_sync in
		# skycraft-features.txt. Restart Minecraft after editing.

		# While snow falls in Skyrim, snow settles on the tops of Minecraft blocks open to the sky near you in the
		# mirror (never on Skyrim's own ground, and at most one layer a block each pass). Every settle_every ticks
		# (20 a second), settle_columns random columns within settle_radius blocks of you are looked at, and each
		# block top there gains a layer with settle_chance, up to max_layers (1 to 8).
		settle_every = 40
		settle_columns = 64
		settle_radius = 16
		settle_chance = 0.25
		max_layers = 2

		# In clear weather settled snow melts again: a layer a pass with this chance (0: it never melts).
		melt_chance = 0.1

		# Skyrim's rain waters farmland open to the sky the same way (on or off).
		rain_waters_farmland = on
		""";

	/**
	 * A block top's snow layers after one pass: {@code layers} now (0 for none), {@code roll} a random number in [0, 1).
	 * While snow falls a layer settles with settle_chance, up to max_layers; in clear weather one melts with melt_chance.
	 * Never more than one layer either way.
	 */
	public static int nextLayers(int layers, boolean snowing, boolean clear, double roll, Settings s) {
		if (snowing) {
			return layers < Math.min(s.maxLayers(), MAX_SNOW_LAYERS) && roll < s.settleChance() ? layers + 1 : layers;
		}
		if (clear && layers > 0 && roll < s.meltChance()) {
			return layers - 1;
		}
		return layers;
	}

	/** Parses {@code key = value} lines; bad ones keep the default and go to {@code problems}. */
	public static Settings parse(String text, List<String> problems) {
		Settings d = Settings.DEFAULTS;
		int every = d.settleEvery(), columns = d.columns(), radius = d.radius(), maxLayers = d.maxLayers();
		double chance = d.settleChance(), melt = d.meltChance();
		boolean waters = d.rainWaters();
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			int hash = line.indexOf('#');
			if (hash >= 0) {
				line = line.substring(0, hash).strip();
			}
			if (line.isEmpty()) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				problems.add("line " + n + ": expected 'key = value'");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip();
			try {
				switch (key) {
					case "settle_every" -> every = whole(value, 1, 1200);
					case "settle_columns" -> columns = whole(value, 0, 1024);
					case "settle_radius" -> radius = whole(value, 1, 64);
					case "settle_chance" -> chance = fraction(value);
					case "max_layers" -> maxLayers = whole(value, 1, MAX_SNOW_LAYERS);
					case "melt_chance" -> melt = fraction(value);
					case "rain_waters_farmland" -> waters = onOff(value);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad value '" + value + "' for " + key);
			}
		}
		return new Settings(every, columns, radius, chance, maxLayers, melt, waters);
	}

	private static int whole(String value, int min, int max) {
		int v = Integer.parseInt(value);
		if (v < min || v > max) {
			throw new NumberFormatException(value);
		}
		return v;
	}

	private static double fraction(String value) {
		double f = Double.parseDouble(value);
		if (!(f >= 0.0 && f <= 1.0)) {
			throw new NumberFormatException(value);
		}
		return f;
	}

	private static boolean onOff(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "on", "true", "yes" -> true;
			case "off", "false", "no" -> false;
			default -> throw new NumberFormatException(value);
		};
	}
}
