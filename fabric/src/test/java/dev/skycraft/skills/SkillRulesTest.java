package dev.skycraft.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import dev.skycraft.skills.SkillRules.Absorbed;
import dev.skycraft.skills.SkillRules.Settings;
import dev.skycraft.skills.SkillRules.SoulLedger;
import dev.skycraft.skooma.SkoomaRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillRulesTest {
	private static final Settings S = Settings.DEFAULTS;

	@Test
	void defaultTextParsesToTheDefaults() {
		List<String> problems = new ArrayList<>();
		assertEquals(Settings.DEFAULTS, SkillRules.parse(SkillRules.DEFAULT_TEXT, problems));
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void badLinesKeepTheDefault() {
		List<String> problems = new ArrayList<>();
		Settings s = SkillRules.parse("sneak_at_100 = 0.1\narchery_at_100 = 2 # comment\nsmithing_per_level = 0\nlevels_per_soul = x\nthuum = 3\nnothing\n"
			+ "alchemy_per_level = 0.01\nenchanting_per_bookshelf = 101\n", problems);
		assertEquals(6, problems.size(), problems.toString());
		assertEquals(S.sneakAtTop(), s.sneakAtTop());
		assertEquals(2.0, s.archeryAtTop());
		assertEquals(S.smithingPerLevel(), s.smithingPerLevel());
		assertEquals(S.levelsPerSoul(), s.levelsPerSoul());
		assertEquals(0.01, s.alchemyPerLevel());
		assertEquals(S.enchantingPerBookshelf(), s.enchantingPerBookshelf());
	}

	@Test
	void bonusesRunFromFifteenToAHundred() {
		assertEquals(0, SkillRules.above(15));
		assertEquals(0, SkillRules.above(5));
		assertEquals(85, SkillRules.above(100));
		assertEquals(85, SkillRules.above(180)); // fortified or set in the console: counts as 100
		assertEquals(0.0, SkillRules.progress(15));
		assertEquals(0.5, SkillRules.progress(57) + 0.5 / 85.0, 1e-9);
		assertEquals(1.0, SkillRules.progress(100));
	}

	@Test
	void anUnknownLevelGivesNothing() {
		assertEquals(0.0, SkillRules.progress(0));
		assertEquals(1.0, SkillRules.sneakScale(0, S));
		assertEquals(1.0, SkillRules.archeryMultiplier(0, S));
		assertEquals(12, SkillRules.anvilCost(12, 0, S));
		assertEquals(4, SkillRules.bookshelves(4, 0, S));
		assertEquals(1.0, SkillRules.alchemyScale(0, S));
	}

	@Test
	void sneakMakesACrouchingPlayerHarderToNotice() {
		assertEquals(1.0, SkillRules.sneakScale(15, S), 1e-9);
		assertEquals(0.6, SkillRules.sneakScale(100, S), 1e-9);
		assertEquals(0.8, SkillRules.sneakScale(57, S) - 0.2 / 85.0, 1e-9);
		assertEquals(0.6, SkillRules.sneakScale(150, S), 1e-9);
		assertEquals(1.0, SkillRules.stealth(1.0, 1.0));
		assertEquals(0.6, SkillRules.stealth(1.0, 0.6), 1e-9);
	}

	@Test
	void sneakAddsToSkoomaButNeverUnderItsFloor() {
		assertEquals(SkoomaRules.stealthScale(20), SkillRules.MIN_STEALTH, 1e-9); // the same floor as Skooma's
		double sneak = SkillRules.sneakScale(100, S);
		assertEquals(0.36, SkillRules.stealth(SkoomaRules.stealthScale(1), sneak), 1e-9); // Skooma I
		assertEquals(0.27, SkillRules.stealth(SkoomaRules.stealthScale(2), sneak), 1e-9); // Redwater
		assertEquals(SkillRules.MIN_STEALTH, SkillRules.stealth(SkoomaRules.stealthScale(4), sneak), 1e-9);
		for (int level = 0; level <= 6; level++) {
			for (int skill = 0; skill <= 120; skill += 5) {
				double scale = SkillRules.stealth(SkoomaRules.stealthScale(level), SkillRules.sneakScale(skill, S));
				assertTrue(scale >= SkillRules.MIN_STEALTH && scale <= 1.0, level + " " + skill);
			}
		}
	}

	@Test
	void archeryArrowsHitHarder() {
		assertEquals(1.0, SkillRules.archeryMultiplier(15, S), 1e-9);
		assertEquals(1.5, SkillRules.archeryMultiplier(100, S), 1e-9);
		assertEquals(1.25, SkillRules.archeryMultiplier(57, S) + 0.25 / 85.0, 1e-9);
		assertEquals(2.0, SkillRules.archeryMultiplier(100, new Settings(0.6, 2.0, 25, 6, 5, 0.005)), 1e-9);
	}

	@Test
	void smithingTakesALevelOffForEveryTwentyFive() {
		assertEquals(10, SkillRules.anvilCost(10, 15, S));
		assertEquals(10, SkillRules.anvilCost(10, 39, S));
		assertEquals(9, SkillRules.anvilCost(10, 40, S));
		assertEquals(8, SkillRules.anvilCost(10, 65, S));
		assertEquals(7, SkillRules.anvilCost(10, 100, S));
		assertEquals(7, SkillRules.anvilCost(10, 200, S));
		assertEquals(1, SkillRules.anvilCost(3, 100, S)); // never under 1
		assertEquals(1, SkillRules.anvilCost(2, 100, S));
		assertEquals(1, SkillRules.anvilCost(1, 100, S));
		assertEquals(0, SkillRules.anvilCost(0, 100, S)); // nothing to make
		assertEquals(39, SkillRules.anvilCost(42, 100, S));
	}

	@Test
	void enchantingCountsMoreBookshelvesUpToFifteen() {
		assertEquals(0, SkillRules.bookshelves(0, 15, S));
		assertEquals(0, SkillRules.bookshelves(0, 20, S));
		assertEquals(1, SkillRules.bookshelves(0, 21, S));
		assertEquals(14, SkillRules.bookshelves(0, 100, S));
		assertEquals(15, SkillRules.bookshelves(1, 100, S));
		assertEquals(15, SkillRules.bookshelves(9, 100, S));
		assertEquals(15, SkillRules.bookshelves(15, 100, S));
		assertEquals(20, SkillRules.bookshelves(20, 100, S)); // more than vanilla counts: left alone
		assertEquals(6, SkillRules.bookshelves(3, 33, S));
	}

	@Test
	void alchemyMakesPotionsLastLonger() {
		assertEquals(1.0, SkillRules.alchemyScale(15, S), 1e-9);
		assertEquals(1.425, SkillRules.alchemyScale(100, S), 1e-9);
		assertEquals(1.1, SkillRules.alchemyScale(35, S), 1e-9);
	}

	@Test
	void eachDragonSoulGivesLevels() {
		assertEquals(5, SkillRules.levelsFor(1, S));
		assertEquals(15, SkillRules.levelsFor(3, S));
		assertEquals(0, SkillRules.levelsFor(-2, S));
	}

	@Test
	void onlyASoulAbsorbedInTheSameGamePays() {
		int game = 0x51C0FFEE, other = 0x51C0FFEF;
		Absorbed first = SoulLedger.NONE.see(game, 3);
		assertEquals(0, first.souls()); // the first look only records the count
		assertEquals(new SoulLedger(game, 3), first.ledger());
		Absorbed one = first.ledger().see(game, 4);
		assertEquals(1, one.souls());
		Absorbed again = one.ledger().see(game, 4);
		assertEquals(0, again.souls()); // seen already: a reconnect doesn't pay twice
		Absorbed spent = again.ledger().see(game, 1); // three spent on a shout
		assertEquals(0, spent.souls());
		assertEquals(new SoulLedger(game, 1), spent.ledger());
		assertEquals(2, spent.ledger().see(game, 3).souls()); // two more absorbed
		Absorbed loaded = spent.ledger().see(other, 12); // another save
		assertEquals(0, loaded.souls());
		assertEquals(new SoulLedger(other, 12), loaded.ledger());
		Absorbed back = loaded.ledger().see(game, 20); // and back to the first game: only recorded
		assertEquals(0, back.souls());
	}

	@Test
	void anOlderPluginChangesNothing() {
		SoulLedger ledger = new SoulLedger(7, 2);
		Absorbed none = ledger.see(0, 0);
		assertEquals(0, none.souls());
		assertEquals(ledger, none.ledger());
		assertEquals(0, SoulLedger.NONE.see(0, 5).souls());
	}

	@Test
	void namesFollowTheActorValues() {
		assertEquals(Proto.SKILL_COUNT, SkillRules.NAMES.size());
		assertEquals("One-Handed", SkillRules.name(Proto.SKILL_FIRST));
		assertEquals("Archery", SkillRules.name(Proto.SKILL_ARCHERY));
		assertEquals("Block", SkillRules.name(Proto.SKILL_BLOCK));
		assertEquals("Smithing", SkillRules.name(Proto.SKILL_SMITHING));
		assertEquals("Light Armor", SkillRules.name(Proto.SKILL_LIGHT_ARMOR));
		assertEquals("Sneak", SkillRules.name(Proto.SKILL_SNEAK));
		assertEquals("Alchemy", SkillRules.name(Proto.SKILL_ALCHEMY));
		assertEquals("Speech", SkillRules.name(Proto.SKILL_SPEECH));
		assertEquals("Enchanting", SkillRules.name(Proto.SKILL_ENCHANTING));
		assertEquals(Proto.SKILL_FIRST + Proto.SKILL_COUNT - 1, Proto.SKILL_ENCHANTING);
		assertEquals("skill 40", SkillRules.name(40));
	}

	@Test
	void describeSaysWhatALevelGives() {
		assertEquals("Sneak 100: noticed crouching from 0.60 of the distance", SkillRules.describe(Proto.SKILL_SNEAK, 100, S));
		assertEquals("Archery unknown: arrows hit x1.00", SkillRules.describe(Proto.SKILL_ARCHERY, 0, S));
		assertEquals("Smithing 65: anvil repairs cost 2 levels less (at least 1)", SkillRules.describe(Proto.SKILL_SMITHING, 65, S));
		assertEquals("Enchanting 100: 14 more bookshelves", SkillRules.describe(Proto.SKILL_ENCHANTING, 100, S));
		assertEquals("Alchemy 100: brewed potions last x1.43", SkillRules.describe(Proto.SKILL_ALCHEMY, 100, S));
		assertEquals("Block 40", SkillRules.describe(Proto.SKILL_BLOCK, 40, S));
	}

	@Test
	void skillsFillTheirTwentyFourBytes() {
		assertEquals(0x68, Proto.SS_SKILLS);
		assertEquals(Proto.SS_SKILLS + Proto.SKILL_COUNT, Proto.SS_DRAGON_SOULS);
		assertEquals(Proto.SS_DRAGON_SOULS + 2, Proto.SS_SKILLS_LOAD);
		assertEquals(Proto.SS_SKILLS + 24, Proto.SS_SKILLS_LOAD + 4);
	}

	/** Proto.java and protocol/skycraft_protocol.h agree on the skills in SkyState. */
	@Test
	void matchesTheProtocolHeader() throws IOException {
		Path header = null;
		for (Path dir = Path.of("").toAbsolutePath(); dir != null && header == null; dir = dir.getParent()) {
			Path candidate = dir.resolve("protocol/skycraft_protocol.h");
			if (Files.exists(candidate)) {
				header = candidate;
			}
		}
		assertNotNull(header, "protocol/skycraft_protocol.h not found above " + Path.of("").toAbsolutePath());
		String text = Files.readString(header);
		String[] expected = {
			"kSkillCount = " + Proto.SKILL_COUNT,
			"kSkillFirst = " + Proto.SKILL_FIRST,
			"std::uint8_t  skills[kSkillCount];",
			String.format("offsetof(SkyState, skills) == 0x%X", Proto.SS_SKILLS),
			String.format("offsetof(SkyState, dragonSouls) == 0x%X", Proto.SS_DRAGON_SOULS),
			String.format("offsetof(SkyState, skillsLoad) == 0x%X", Proto.SS_SKILLS_LOAD),
			"offsetof(SkyState, weather) == 0x80",
		};
		for (String line : expected) {
			assertTrue(text.contains(line), "the header should say " + line);
		}
	}
}
