package dev.skycraft.creatures;

import dev.skycraft.deep.DeepRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Night monsters: when and where Skyrim creatures come out at night near the player, outdoors, away from towns and in
 * the dark, and which ones (draugr and skeletons near a Nordic barrow's entrance, skeevers and skeletons elsewhere).
 *
 * <p>Plain text in config/skycraft-night.txt, one setting per line ({@code key = value}). A creature list is
 * {@code skeever 3, skeleton 1}: one of them at random, each as likely as its weight (1 when left out). Pure Java on
 * purpose (no Minecraft classes), so it's unit tested without Minecraft. The creatures come through the deep creatures'
 * spawn (EV_SPAWN_CREATURE), which caps them all together.
 */
public final class NightRules {
	/** A creature (DeepRules.SKEEVER, SKELETON, DRAUGR) and how likely it is among its list's. */
	public record Weighted(int kind, int weight) {
	}

	/** Shipped defaults, written to the config folder on first run. */
	public static final String DEFAULT_TEXT = """
		# SkyCraft night monsters: at night, outdoors, away from towns and in the dark, Skyrim creatures now and then come out
		# near you, as Minecraft's monsters come out at night. Never in interiors.
		# Needs night_monsters = on in skycraft-features.txt (it's off at first). Restart Minecraft after editing.
		# Skyrim's night, game hours (wraps past midnight).
		night = 20-5
		# Seconds between tries (a random time in this range), and the chance a try brings one out.
		cooldown = 60-120
		chance = 0.4
		# Most of them alive at once. Night monsters and the deep creatures count together, and never more than
		# skycraft-deep.txt's max_alive.
		max_alive = 2
		# How far from you (blocks) one may come out; never where anyone can see it.
		distance = 16-32
		# Further away than this (blocks), it's taken away again.
		despawn_distance = 72
		# Only where Minecraft's block light is at most this (0: no torch, lantern or campfire lights the spot).
		max_block_light = 0
		# Who comes out: skeever, skeleton, draugr, each with a weight (several: one at random).
		creatures = skeever 3, skeleton 2
		# Within this many blocks of a Nordic barrow's, crypt's or ruin's entrance, these instead.
		barrow_distance = 64
		barrow_creatures = draugr 3, skeleton 1
		""";

	private final float nightFrom;
	private final float nightTo;
	private final double cooldownMin;
	private final double cooldownMax;
	private final double chance;
	private final int maxAlive;
	private final double minDistance;
	private final double maxDistance;
	private final double despawnDistance;
	private final int maxBlockLight;
	private final List<Weighted> creatures;
	private final double barrowDistance;
	private final List<Weighted> barrowCreatures;

	private NightRules(float nightFrom, float nightTo, double cooldownMin, double cooldownMax, double chance, int maxAlive, double minDistance,
		double maxDistance, double despawnDistance, int maxBlockLight, List<Weighted> creatures, double barrowDistance, List<Weighted> barrowCreatures) {
		this.nightFrom = nightFrom;
		this.nightTo = nightTo;
		this.cooldownMin = cooldownMin;
		this.cooldownMax = cooldownMax;
		this.chance = chance;
		this.maxAlive = maxAlive;
		this.minDistance = minDistance;
		this.maxDistance = maxDistance;
		this.despawnDistance = despawnDistance;
		this.maxBlockLight = maxBlockLight;
		this.creatures = creatures;
		this.barrowDistance = barrowDistance;
		this.barrowCreatures = barrowCreatures;
	}

	public float nightFrom() {
		return this.nightFrom;
	}

	public float nightTo() {
		return this.nightTo;
	}

	public double chance() {
		return this.chance;
	}

	public int maxAlive() {
		return this.maxAlive;
	}

	public double minDistance() {
		return this.minDistance;
	}

	public double maxDistance() {
		return this.maxDistance;
	}

	public double despawnDistance() {
		return this.despawnDistance;
	}

	public int maxBlockLight() {
		return this.maxBlockLight;
	}

	public double barrowDistance() {
		return this.barrowDistance;
	}

	public List<Weighted> creatures() {
		return this.creatures;
	}

	public List<Weighted> barrowCreatures() {
		return this.barrowCreatures;
	}

	/** Anything can come out at all. */
	public boolean enabled() {
		return this.maxAlive > 0 && this.chance > 0.0 && !this.creatures.isEmpty();
	}

	/** Parses settings text. Bad lines are skipped (the default stays) and reported to {@code problems}. */
	public static NightRules parse(String text, List<String> problems) {
		float nightFrom = 20.0F, nightTo = 5.0F;
		double cooldownMin = 60.0, cooldownMax = 120.0;
		double chance = 0.4;
		int maxAlive = 2;
		double minDistance = 16.0, maxDistance = 32.0;
		double despawnDistance = 72.0;
		int maxBlockLight = 0;
		List<Weighted> creatures = List.of(new Weighted(DeepRules.SKEEVER, 3), new Weighted(DeepRules.SKELETON, 2));
		double barrowDistance = 64.0;
		List<Weighted> barrowCreatures = List.of(new Weighted(DeepRules.DRAUGR, 3), new Weighted(DeepRules.SKELETON, 1));
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				problems.add("line " + n + ": no '='");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip().toLowerCase(Locale.ROOT);
			try {
				switch (key) {
					case "night" -> {
						int dash = value.replace(" ", "").indexOf('-', 1);
						if (dash < 0) {
							throw new IllegalArgumentException("night needs from-to, like 20-5");
						}
						String v = value.replace(" ", "");
						nightFrom = (float) number(v.substring(0, dash), 0.0, 24.0);
						nightTo = (float) number(v.substring(dash + 1), 0.0, 24.0);
					}
					case "cooldown" -> {
						double[] r = range(value);
						if (r[0] < 1.0) {
							throw new IllegalArgumentException("cooldown under 1 second");
						}
						cooldownMin = r[0];
						cooldownMax = r[1];
					}
					case "chance" -> chance = number(value, 0.0, 1.0);
					case "max_alive" -> maxAlive = (int) number(value, 0, 16);
					case "distance" -> {
						double[] r = range(value);
						if (r[0] < 8.0 || r[1] > 48.0) {
							throw new IllegalArgumentException("distance must be within 8-48 (Skyrim's ground is only known that far)");
						}
						minDistance = r[0];
						maxDistance = r[1];
					}
					case "despawn_distance" -> despawnDistance = number(value, 16.0, 256.0);
					case "max_block_light" -> maxBlockLight = (int) number(value, 0, 15);
					case "creatures" -> creatures = weighted(value);
					case "barrow_distance" -> barrowDistance = number(value, 0.0, 256.0);
					case "barrow_creatures" -> barrowCreatures = weighted(value);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (IllegalArgumentException e) {
				problems.add("line " + n + ": " + e.getMessage());
			}
		}
		if (despawnDistance < maxDistance + 8.0) {
			problems.add("despawn_distance " + despawnDistance + " is too close to the spawn distance; using " + (maxDistance + 8.0));
			despawnDistance = maxDistance + 8.0;
		}
		return new NightRules(nightFrom, nightTo, cooldownMin, cooldownMax, chance, maxAlive, minDistance, maxDistance, despawnDistance, maxBlockLight,
			creatures, barrowDistance, barrowCreatures);
	}

	// ---- decisions --------------------------------------------------------------------------

	/** Skyrim's game hour (0-24) is in the night, which may wrap past midnight (20-5). */
	public boolean isNight(float hour) {
		if (this.nightFrom == this.nightTo) {
			return false;
		}
		return this.nightFrom < this.nightTo ? hour >= this.nightFrom && hour < this.nightTo : hour >= this.nightFrom || hour < this.nightTo;
	}

	/** Most spawned creatures alive at once: night monsters and deep creatures count together, never past the deep creatures' cap. */
	public int cap(int deepMaxAlive) {
		return Math.max(0, Math.min(this.maxAlive, deepMaxAlive));
	}

	/** Ticks to wait between tries; {@code unit} is a random number in [0, 1). */
	public long cooldownTicks(double unit) {
		double u = Math.clamp(unit, 0.0, 1.0);
		return Math.round((this.cooldownMin + (this.cooldownMax - this.cooldownMin) * u) * DeepRules.TICKS_PER_SECOND);
	}

	/** A spot this far (squared blocks) from the player is near enough, and far enough, to come out at. */
	public boolean inSpawnRange(double distanceSq) {
		return distanceSq >= this.minDistance * this.minDistance && distanceSq <= this.maxDistance * this.maxDistance;
	}

	/** Dark enough: Minecraft's block light at the spot (0-15). */
	public boolean darkEnough(int blockLight) {
		return blockLight <= this.maxBlockLight;
	}

	/**
	 * Which creature comes out, by weight: from the barrow list when a Nordic dungeon's entrance is within barrow_distance
	 * ({@code barrowDistance} blocks, negative: none known), else the open country's. {@code pick} is any non-negative
	 * random number. DeepRules.NONE when the list is empty.
	 */
	public int pick(double barrowDistance, int pick) {
		boolean barrow = barrowDistance >= 0.0 && barrowDistance <= this.barrowDistance && !this.barrowCreatures.isEmpty();
		List<Weighted> list = barrow ? this.barrowCreatures : this.creatures;
		int total = 0;
		for (Weighted w : list) {
			total += w.weight();
		}
		if (total <= 0) {
			return DeepRules.NONE;
		}
		int at = Math.floorMod(pick, total);
		for (Weighted w : list) {
			at -= w.weight();
			if (at < 0) {
				return w.kind();
			}
		}
		return list.getLast().kind();
	}

	// ---- parsing helpers -------------------------------------------------------------------

	/** "skeever 3, skeleton 1" (a weight of 1 when left out). */
	private static List<Weighted> weighted(String v) {
		List<Weighted> out = new ArrayList<>();
		for (String part : v.split(",")) {
			String word = part.strip();
			if (word.isEmpty()) {
				continue;
			}
			String[] bits = word.split("\\s+");
			int kind = DeepRules.kindNamed(bits[0]);
			if (kind == DeepRules.NONE) {
				throw new IllegalArgumentException("unknown creature '" + bits[0] + "' (skeever, skeleton or draugr)");
			}
			int weight = bits.length > 1 ? (int) number(bits[1], 1, 100) : 1;
			if (bits.length > 2) {
				throw new IllegalArgumentException("'" + word + "' should be a creature and a weight");
			}
			out.add(new Weighted(kind, weight));
		}
		return List.copyOf(out);
	}

	private static double number(String v, double min, double max) {
		double d;
		try {
			d = Double.parseDouble(v);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("'" + v + "' isn't a number");
		}
		if (!(d >= min && d <= max)) {
			throw new IllegalArgumentException(v + " is outside " + fmt(min) + "-" + fmt(max));
		}
		return d;
	}

	/** "a-b" or "a". */
	private static double[] range(String v) {
		String s = v.replace(" ", "");
		int dash = s.indexOf('-', 1);
		double a = number(dash < 0 ? s : s.substring(0, dash), 0.0, 10000.0);
		double b = dash < 0 ? a : number(s.substring(dash + 1), 0.0, 10000.0);
		if (b < a) {
			throw new IllegalArgumentException("bad range '" + v + "'");
		}
		return new double[] { a, b };
	}

	private static String fmt(double d) {
		return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
	}
}
