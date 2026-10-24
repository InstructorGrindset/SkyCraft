package dev.skycraft.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestRulesTest {
	private static final QuestRules.Settings S = QuestRules.Settings.DEFAULTS;

	private static QuestTable.Target target(int quest, int ref, int flags, double x, double z, String name) {
		return new QuestTable.Target(quest, ref, 0, flags, 8, x, 64.0, z, name, "");
	}

	private static QuestTable.Target tracked(int quest, double x, double z, String name) {
		return target(quest, quest + 1000, Proto.QUEST_TRACKED, x, z, name);
	}

	@Test
	void theDefaultsParseToTheDefaults() {
		List<String> problems = new ArrayList<>();
		assertEquals(QuestRules.Settings.DEFAULTS, QuestRules.parse(QuestRules.DEFAULT_TEXT, problems));
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void settingsAreReadAndBadOnesReported() {
		List<String> problems = new ArrayList<>();
		QuestRules.Settings s = QuestRules.parse("max_markers = 4\nuntracked = on\nstyle = minecraft:bowtie\nmain_color = FF0000\n"
			+ "tracked_color = 0x00ff00\nlook_angle = 10\nlocator_bar = off\n", problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(new QuestRules.Settings(4, true, "minecraft:bowtie", 0xFF0000, 0x00FF00, S.untrackedColor(), 10.0, false), s);
		s = QuestRules.parse("max_markers = 40\nstyle = Bleak Falls\nmain_color = gold\nlook_angle = 0\ncompass = on\nnonsense\n", problems);
		assertEquals(6, problems.size(), problems.toString());
		assertEquals(S, s);
	}

	@Test
	void onlyTrackedQuestsUnlessUntrackedIsOn() {
		List<QuestTable.Target> targets = List.of(tracked(1, 100, 0, "Dragon Rising"), target(2, 2002, 0, -50, 0, "The Golden Claw"));
		List<QuestRules.Marker> markers = QuestRules.select(targets, S);
		assertEquals(1, markers.size());
		assertEquals("Dragon Rising", markers.getFirst().label());
		assertEquals(S.trackedColor(), markers.getFirst().color());
		QuestRules.Settings all = new QuestRules.Settings(16, true, S.style(), S.mainColor(), S.trackedColor(), S.untrackedColor(), 5.0, true);
		markers = QuestRules.select(targets, all);
		assertEquals(2, markers.size());
		assertEquals(S.untrackedColor(), markers.get(1).color());
	}

	@Test
	void theMainQuestHasItsOwnColour() {
		QuestTable.Target main = target(5, 6, Proto.QUEST_TRACKED | Proto.QUEST_MAIN, 0, 10, "The Way of the Voice");
		assertEquals(S.mainColor(), QuestRules.color(main, S));
	}

	@Test
	void targetsOnOneBlockShareAMarkerAndTheCapHolds() {
		List<QuestTable.Target> targets = new ArrayList<>();
		targets.add(tracked(1, 10.2, 10.7, "First"));
		targets.add(tracked(2, 10.9, 10.1, "Second, same door"));
		for (int i = 3; i < 30; i++) {
			targets.add(tracked(i, i * 10.0, 0, "Quest " + i));
		}
		QuestRules.Settings four = new QuestRules.Settings(4, false, S.style(), S.mainColor(), S.trackedColor(), S.untrackedColor(), 5.0, true);
		List<QuestRules.Marker> markers = QuestRules.select(targets, four);
		assertEquals(4, markers.size());
		assertEquals("First", markers.getFirst().label());
		assertEquals(1, markers.getFirst().also());
		assertEquals("Quest 3", markers.get(1).label());
		assertEquals(10, markers.getFirst().x());
		assertEquals(64, markers.getFirst().y());
	}

	@Test
	void idsStayWithTheQuestAndReference() {
		assertEquals(QuestRules.id(0x2610C, 0x12345), QuestRules.id(0x2610C, 0x12345));
		assertFalse(QuestRules.id(0x2610C, 0x12345).equals(QuestRules.id(0x2610C, 0x12346)));
		assertFalse(QuestRules.id(1, 2).equals(QuestRules.id(2, 1)));
		assertEquals(3, QuestRules.id(1, 2).version());
	}

	@Test
	void labelsFallBackForNamelessAndTaggedTexts() {
		assertEquals("Dragon Rising", QuestRules.label(new QuestTable.Target(1, 1, 0, 1, 1, 0, 0, 0, " Dragon Rising ", "Kill the dragon")));
		assertEquals("Speak to Lucan", QuestRules.label(new QuestTable.Target(1, 1, 0, 1, 6, 0, 0, 0, "", "Speak to Lucan")));
		assertEquals("Miscellaneous", QuestRules.label(new QuestTable.Target(1, 1, 0, 1, 6, 0, 0, 0, "", "Deliver <Alias=Item> to <Alias=Person>")));
		assertEquals("Miscellaneous", QuestRules.label(new QuestTable.Target(1, 1, 0, 1, 8, 0, 0, 0, "", "")));
		assertEquals("Quest", QuestRules.label(new QuestTable.Target(1, 1, 0, 1, 8, 0, 0, 0, "<Alias=Boss>'s Bounty", "")));
		assertEquals("a < b", QuestRules.label(new QuestTable.Target(1, 1, 0, 1, 8, 0, 0, 0, "a < b", "")));
	}

	@Test
	void longTextsAreCutAtAWord() {
		assertEquals("Short", QuestRules.cut("Short", 10));
		assertEquals("Retrieve the...", QuestRules.cut("Retrieve the Dragonstone", 16));
		assertEquals("Abcdefghijklm...", QuestRules.cut("Abcdefghijklmnopqrstuvwxyz", 16));
		assertEquals(16, QuestRules.cut("Abcdefghijklmnopqrstuvwxyz", 16).length());
		assertEquals("A", QuestRules.cut("Abcd", 1));
	}

	@Test
	void onlyWhatChangedIsSent() {
		QuestRules.Marker a = QuestRules.select(List.of(tracked(1, 10, 10, "A")), S).getFirst();
		QuestRules.Marker b = QuestRules.select(List.of(tracked(2, 20, 20, "B")), S).getFirst();
		Map<UUID, QuestRules.Marker> shown = new HashMap<>();
		QuestRules.Changes first = QuestRules.changes(shown, List.of(a, b));
		assertEquals(List.of(a, b), first.show());
		assertTrue(first.remove().isEmpty());
		shown.put(a.id(), a);
		shown.put(b.id(), b);
		assertTrue(QuestRules.changes(shown, List.of(a, b)).none());
		QuestRules.Marker bMoved = QuestRules.select(List.of(tracked(2, 25, 20, "B")), S).getFirst();
		QuestRules.Changes moved = QuestRules.changes(shown, List.of(bMoved));
		assertEquals(List.of(bMoved), moved.show());
		assertEquals(List.of(a.id()), moved.remove());
		QuestRules.Changes gone = QuestRules.changes(shown, List.of());
		assertTrue(gone.show().isEmpty());
		assertEquals(2, gone.remove().size());
	}

	@Test
	void directionsGoRoundTheCompass() {
		// Minecraft's yaw: 0 faces south (+z), 90 west (-x), 180 north, -90 east; turning right raises it.
		assertEquals(0.0, QuestRules.yawToward(0, 5), 1e-9);
		assertEquals(90.0, QuestRules.yawToward(-5, 0), 1e-9);
		assertEquals(-90.0, QuestRules.yawToward(5, 0), 1e-9);
		assertEquals(180.0, Math.abs(QuestRules.yawToward(0, -5)), 1e-9);
		assertEquals(-20.0, QuestRules.relative(10.0, 350.0), 1e-9);
		assertEquals(20.0, QuestRules.relative(350.0, 10.0), 1e-9);
		assertEquals(180.0, QuestRules.relative(0.0, 180.0), 1e-9);
		assertEquals("ahead", QuestRules.direction(10));
		assertEquals("ahead-right", QuestRules.direction(45));
		assertEquals("ahead-left", QuestRules.direction(-45));
		assertEquals("right", QuestRules.direction(90));
		assertEquals("left", QuestRules.direction(-100));
		assertEquals("behind-right", QuestRules.direction(140));
		assertEquals("behind-left", QuestRules.direction(-140));
		assertEquals("behind", QuestRules.direction(180));
		assertEquals("behind", QuestRules.direction(-170));
	}

	@Test
	void lookingTowardAMarkerPicksTheOneNearestTheMiddle() {
		// Facing south from the origin: one marker straight ahead far off, one 3 degrees off to the right, one behind.
		List<QuestRules.Marker> markers = QuestRules.select(List.of(tracked(1, 0, 200, "Ahead"), tracked(2, -5.24, 99.5, "Slightly right"),
			tracked(3, 0, -50, "Behind")), S);
		assertEquals(0, QuestRules.lookedAt(markers, 0.5, 0.5, 0.0F, 5.0));
		assertEquals(1, QuestRules.lookedAt(markers, 0.5, 0.5, 3.0F, 5.0));
		assertEquals(2, QuestRules.lookedAt(markers, 0.5, 0.5, 180.0F, 5.0));
		assertEquals(-1, QuestRules.lookedAt(markers, 0.5, 0.5, 90.0F, 5.0));
		// A marker reached isn't looked toward.
		assertEquals(-1, QuestRules.lookedAt(QuestRules.select(List.of(tracked(1, 1, 1, "Here")), S), 1.5, 2.5, 0.0F, 5.0));
	}

	@Test
	void theLineSaysHowFarAndWhichWay() {
		QuestRules.Marker barrow = QuestRules.select(List.of(tracked(1, -163.0, 162.0, "Bleak Falls Barrow")), S).getFirst();
		// From the origin facing south: the barrow is 45 degrees to the right (west of south), 230 blocks off.
		assertEquals("Bleak Falls Barrow, 230 blocks, ahead-right", QuestRules.line(barrow, 0.5, 64.5, 0.5, 0.0));
		assertEquals("Bleak Falls Barrow, 230 blocks, ahead-left", QuestRules.line(barrow, 0.5, 64.5, 0.5, 90.0));
		QuestTable.Target door = target(2, 3, Proto.QUEST_TRACKED | Proto.QUEST_VIA_DOOR, 0, 40, "The Golden Claw");
		QuestRules.Marker m = QuestRules.select(List.of(door, target(4, 5, Proto.QUEST_TRACKED, 0.5, 40.5, "Another")), S).getFirst();
		assertEquals("The Golden Claw and 1 more, 40 blocks to the door, ahead", QuestRules.line(m, 0.5, 64.5, 0.5, 0.0));
		assertEquals("The Golden Claw and 1 more, at the door", QuestRules.line(m, 0.5, 64.5, 39.5, 0.0));
		QuestRules.Marker one = QuestRules.select(List.of(tracked(6, 0, 1, "Near")), S).getFirst();
		assertEquals("Near, here", QuestRules.line(one, 0.5, 64.5, 0.5, 0.0));
	}

	@Test
	void theNearestActiveQuestsTargetForTheActionBarAlone() {
		QuestRules.Settings all = new QuestRules.Settings(16, true, S.style(), S.mainColor(), S.trackedColor(), S.untrackedColor(), 5.0, false);
		List<QuestRules.Marker> markers = QuestRules.select(List.of(tracked(1, 0, 300, "Far"), target(2, 3, 0, 0, 5, "Untracked, near"),
			tracked(4, 0, 50, "Near")), all);
		assertEquals(2, QuestRules.nearestTracked(markers, 0, 64, 0));
		assertEquals(-1, QuestRules.nearestTracked(List.of(), 0, 64, 0));
	}
}
