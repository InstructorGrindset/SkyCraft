package dev.skycraft.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.config.Features.Feature;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeatureAreasTest {
	@Test
	void everyFeatureIsInExactlyOneArea() {
		Set<Feature> seen = EnumSet.noneOf(Feature.class);
		Set<String> names = new HashSet<>();
		for (FeatureAreas.Area area : FeatureAreas.areas()) {
			assertTrue(names.add(area.name()), "two areas named " + area.name());
			assertFalse(area.features().isEmpty(), area.name() + " is empty");
			for (Feature f : area.features()) {
				assertTrue(seen.add(f), f.key + " is in two areas");
			}
		}
		assertEquals(Feature.values().length, seen.size());
	}

	@Test
	void everyFeatureHasAnAreaOfItsOwnNotMore() {
		// A new switch shows under "More" until it's listed in FeatureAreas.AREAS: list it there.
		for (FeatureAreas.Area area : FeatureAreas.areas()) {
			assertFalse(area.name().equals(FeatureAreas.MORE), "not in an area yet: " + area.features());
		}
	}

	@Test
	void namesReadLikeWords() {
		assertEquals("Kill drops", FeatureAreas.title(Feature.KILL_DROPS));
		assertEquals("Kill XP", FeatureAreas.title(Feature.KILL_XP));
		assertEquals("Minecraft view", FeatureAreas.title(Feature.MINECRAFT_VIEW));
		assertEquals("Mobs fight NPCs", FeatureAreas.title(Feature.MOBS_FIGHT_NPCS));
		assertEquals("Wolves fight Skyrim", FeatureAreas.title(Feature.WOLVES_FIGHT_SKYRIM));
		assertEquals("NPC doors and ladders", FeatureAreas.title(Feature.NPC_DOORS_LADDERS));
		assertEquals("Skooma addiction", FeatureAreas.title(Feature.SKOOMA_ADDICTION));
		for (Feature f : Feature.values()) {
			String title = FeatureAreas.title(f);
			assertFalse(title.isBlank(), f.key);
			// The button reads "<name>: Off (after restart)" on one 310 wide row.
			assertTrue(title.length() <= 26, f.key + "'s name is too long for its button: " + title);
		}
	}

	@Test
	void descriptionsAreSentences() {
		assertEquals("Skyrim kills drop Minecraft XP orbs.", FeatureAreas.description(Feature.KILL_XP));
		for (Feature f : Feature.values()) {
			String d = FeatureAreas.description(f);
			assertTrue(Character.isUpperCase(d.charAt(0)), f.key + ": " + d);
			assertTrue(d.endsWith("."), f.key + ": " + d);
		}
	}

	@Test
	void areasKeepTheirOrder() {
		List<FeatureAreas.Area> areas = FeatureAreas.areas();
		assertEquals("Loot and items", areas.getFirst().name());
		assertEquals(Feature.ITEM_CONVERSION, areas.getFirst().features().getFirst());
	}
}
