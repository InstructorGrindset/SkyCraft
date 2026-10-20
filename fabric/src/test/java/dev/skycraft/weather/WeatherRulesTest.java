package dev.skycraft.weather;

import static dev.skycraft.link.Proto.WEATHER_CLEAR;
import static dev.skycraft.link.Proto.WEATHER_CLOUDY;
import static dev.skycraft.link.Proto.WEATHER_NONE;
import static dev.skycraft.link.Proto.WEATHER_RAIN;
import static dev.skycraft.link.Proto.WEATHER_REFUSED;
import static dev.skycraft.link.Proto.WEATHER_SNOW;
import static dev.skycraft.link.Proto.WEATHER_STORM;
import static dev.skycraft.weather.WeatherRules.MC_CLEAR;
import static dev.skycraft.weather.WeatherRules.MC_RAIN;
import static dev.skycraft.weather.WeatherRules.MC_THUNDER;
import static dev.skycraft.weather.WeatherRules.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherRulesTest {
	@Test
	void skyrimsWeatherPicksMinecrafts() {
		assertEquals(MC_CLEAR, WeatherRules.minecraftFor(WEATHER_CLEAR));
		assertEquals(MC_CLEAR, WeatherRules.minecraftFor(WEATHER_CLOUDY));
		assertEquals(MC_RAIN, WeatherRules.minecraftFor(WEATHER_RAIN));
		assertEquals(MC_RAIN, WeatherRules.minecraftFor(WEATHER_SNOW)); // Minecraft's rain is snow where it's cold
		assertEquals(MC_THUNDER, WeatherRules.minecraftFor(WEATHER_STORM));
		assertEquals(UNKNOWN, WeatherRules.minecraftFor(WEATHER_NONE)); // indoors: Minecraft's stays
		assertEquals(UNKNOWN, WeatherRules.minecraftFor(99));
	}

	@Test
	void minecraftsWeatherAsksForSkyrims() {
		assertEquals(WEATHER_CLEAR, WeatherRules.skyrimFor(MC_CLEAR));
		assertEquals(WEATHER_RAIN, WeatherRules.skyrimFor(MC_RAIN));
		assertEquals(WEATHER_STORM, WeatherRules.skyrimFor(MC_THUNDER));
		assertEquals(WEATHER_NONE, WeatherRules.skyrimFor(UNKNOWN));
		// Asking and following come back to the same Minecraft weather.
		for (int mc : new int[] { MC_CLEAR, MC_RAIN, MC_THUNDER }) {
			assertEquals(mc, WeatherRules.minecraftFor(WeatherRules.skyrimFor(mc)));
		}
		assertEquals("thunder", WeatherRules.command(MC_THUNDER));
		assertEquals("clear", WeatherRules.command(MC_CLEAR));
	}

	@Test
	void ownChangesFadingInAreNotSomeoneElses() {
		WeatherRules.Watch watch = new WeatherRules.Watch();
		assertEquals(UNKNOWN, watch.observe(MC_RAIN, 0)); // nothing set yet: nothing to compare with
		watch.set(MC_THUNDER, 1000);
		// Clear to thunder passes through rain for a few seconds: within the grace, it's SkyCraft's own change.
		for (long t = 1000; t < 1000 + WeatherRules.Watch.GRACE; t += 20) {
			assertEquals(UNKNOWN, watch.observe(t < 1060 ? MC_CLEAR : MC_RAIN, t));
		}
		assertEquals(UNKNOWN, watch.observe(MC_THUNDER, 1200));
		assertEquals(UNKNOWN, watch.observe(MC_THUNDER, 5000));
	}

	@Test
	void someoneElsesChangeIsTakenOnceItHolds() {
		WeatherRules.Watch watch = new WeatherRules.Watch();
		watch.set(MC_CLEAR, 0);
		long t = WeatherRules.Watch.GRACE;
		assertEquals(UNKNOWN, watch.observe(MC_CLEAR, t));
		assertEquals(UNKNOWN, watch.observe(MC_RAIN, t + 20)); // /weather thunder: rain first
		assertEquals(UNKNOWN, watch.observe(MC_THUNDER, t + 80)); // then thunder; it has to hold
		assertEquals(UNKNOWN, watch.observe(MC_THUNDER, t + 80 + WeatherRules.Watch.STABLE - 20));
		assertEquals(MC_THUNDER, watch.observe(MC_THUNDER, t + 80 + WeatherRules.Watch.STABLE));
		assertEquals(MC_THUNDER, watch.expected());
		assertEquals(UNKNOWN, watch.observe(MC_THUNDER, t + 1000)); // said once
	}

	@Test
	void asksAreAnsweredOnceAndNeverPingPong() {
		WeatherRules.Watch watch = new WeatherRules.Watch();
		WeatherRules.Asks asks = new WeatherRules.Asks(41);
		// Skyrim is clear; Minecraft follows.
		int skyrim = WEATHER_CLEAR;
		watch.set(WeatherRules.minecraftFor(skyrim), 0);
		assertEquals(41L, asks.packed()); // nothing asked: the number, no kind
		// A player's /weather rain holds: one request.
		long t = 400;
		watch.observe(MC_RAIN, t);
		int got = watch.observe(MC_RAIN, t + WeatherRules.Watch.STABLE);
		assertEquals(MC_RAIN, got);
		int seq = asks.open(WeatherRules.skyrimFor(got), t + WeatherRules.Watch.STABLE);
		assertEquals(42, seq);
		assertEquals(42L | ((long) WEATHER_RAIN << 32), asks.packed());
		assertTrue(asks.waiting());
		assertEquals(WeatherRules.Asks.WAITING, asks.check(41, 0, t + 120));
		// Skyrim switches to rain and acknowledges: answered once, and the request is closed.
		skyrim = WEATHER_RAIN;
		assertEquals(WeatherRules.Asks.ANSWERED, asks.check(42, 0, t + 140));
		assertEquals(WeatherRules.Asks.IDLE, asks.check(42, 0, t + 160));
		assertEquals(42L, asks.packed());
		// Following Skyrim now changes nothing, so nothing new is asked, however long it goes on.
		assertEquals(watch.expected(), WeatherRules.minecraftFor(skyrim));
		for (long u = t + 200; u < t + 4000; u += 20) {
			assertEquals(UNKNOWN, watch.observe(MC_RAIN, u));
		}
	}

	@Test
	void aRefusalPutsSkyrimsWeatherBackWithoutAnotherAsk() {
		WeatherRules.Watch watch = new WeatherRules.Watch();
		WeatherRules.Asks asks = new WeatherRules.Asks(-2);
		watch.set(MC_CLEAR, 0);
		watch.observe(MC_THUNDER, 300);
		assertEquals(MC_THUNDER, watch.observe(MC_THUNDER, 300 + WeatherRules.Watch.STABLE));
		int seq = asks.open(WEATHER_STORM, 400);
		assertEquals(-1, seq);
		assertEquals(WeatherRules.Asks.REFUSED, asks.check(seq, WEATHER_REFUSED, 420));
		// Skyrim stayed clear: Minecraft sets clear (SkyCraft's own change), which is never asked about.
		watch.set(WeatherRules.minecraftFor(WEATHER_CLEAR), 440);
		for (long u = 440; u < 2000; u += 20) {
			assertEquals(UNKNOWN, watch.observe(u < 520 ? MC_THUNDER : MC_CLEAR, u));
		}
	}

	@Test
	void numbersSkipZeroAndOldPluginsTimeOut() {
		WeatherRules.Asks asks = new WeatherRules.Asks(-1);
		assertEquals(1, asks.open(WEATHER_RAIN, 0)); // 0 is "never asked"
		assertEquals(WeatherRules.Asks.WAITING, asks.check(0, 0, WeatherRules.Asks.TIMEOUT));
		assertEquals(WeatherRules.Asks.TIMED_OUT, asks.check(0, 0, WeatherRules.Asks.TIMEOUT + 1));
		assertFalse(asks.waiting());
	}

	@Test
	void snowSettlesALayerAtATime() {
		WeatherRules.Settings s = WeatherRules.Settings.DEFAULTS;
		assertEquals(1, WeatherRules.nextLayers(0, true, false, 0.0, s));
		assertEquals(0, WeatherRules.nextLayers(0, true, false, s.settleChance(), s)); // the roll missed
		assertEquals(2, WeatherRules.nextLayers(1, true, false, 0.1, s));
		assertEquals(s.maxLayers(), WeatherRules.nextLayers(s.maxLayers(), true, false, 0.0, s)); // full
		// However the rolls go, never more than one layer a pass and never past max_layers.
		WeatherRules.Settings deep = new WeatherRules.Settings(40, 64, 16, 1.0, 8, 1.0, true);
		for (int layers = 0; layers <= 8; layers++) {
			for (double roll = 0.0; roll < 1.0; roll += 0.05) {
				int next = WeatherRules.nextLayers(layers, true, false, roll, deep);
				assertTrue(next == layers || next == layers + 1);
				assertTrue(next <= 8);
				int melted = WeatherRules.nextLayers(layers, false, true, roll, deep);
				assertTrue(melted == layers || melted == layers - 1);
				assertTrue(melted >= 0);
			}
		}
	}

	@Test
	void snowMeltsOnlyInClearWeather() {
		WeatherRules.Settings s = WeatherRules.Settings.DEFAULTS;
		assertEquals(1, WeatherRules.nextLayers(2, false, true, 0.0, s));
		assertEquals(0, WeatherRules.nextLayers(1, false, true, 0.0, s));
		assertEquals(2, WeatherRules.nextLayers(2, false, true, s.meltChance(), s)); // the roll missed
		assertEquals(2, WeatherRules.nextLayers(2, false, false, 0.0, s)); // cloudy, rain: it stays
		assertEquals(0, WeatherRules.nextLayers(0, false, true, 0.0, s)); // nothing to melt
		WeatherRules.Settings never = new WeatherRules.Settings(40, 64, 16, 0.25, 2, 0.0, true);
		assertEquals(2, WeatherRules.nextLayers(2, false, true, 0.0, never));
	}

	@Test
	void settingsParseWithDefaultsAndProblems() {
		List<String> problems = new ArrayList<>();
		assertEquals(WeatherRules.Settings.DEFAULTS, WeatherRules.parse(WeatherRules.DEFAULT_TEXT, problems));
		assertTrue(problems.isEmpty(), problems.toString());
		WeatherRules.Settings s = WeatherRules.parse("settle_chance = 0.5\nmax_layers = 8\nrain_waters_farmland = off # no\nsettle_every = 20\n", problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(0.5, s.settleChance(), 1e-9);
		assertEquals(8, s.maxLayers());
		assertFalse(s.rainWaters());
		assertEquals(20, s.settleEvery());
		assertEquals(WeatherRules.Settings.DEFAULTS.columns(), s.columns());
		s = WeatherRules.parse("max_layers = 9\nmelt_chance = 2\nsettle_every = 0\nsnow = deep\nnonsense\n", problems);
		assertEquals(5, problems.size(), problems.toString());
		assertEquals(WeatherRules.Settings.DEFAULTS, s);
	}
}
