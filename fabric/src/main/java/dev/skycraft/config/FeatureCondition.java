package dev.skycraft.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.skycraft.SkyCraft;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import org.jspecify.annotations.Nullable;

/**
 * A data pack file that loads only while one of skycraft-features.txt's switches is on (Fabric's resource conditions):
 * <pre>
 * "fabric:load_conditions": [ { "condition": "skycraft:feature_on", "feature": "skooma_brewing" } ]
 * </pre>
 * The Skooma brewing recipes use it. An unknown feature never loads (and says so in the log).
 */
public record FeatureCondition(String feature) implements ResourceCondition {
	public static final MapCodec<FeatureCondition> CODEC = Codec.STRING.fieldOf("feature").xmap(FeatureCondition::new, FeatureCondition::feature);
	public static final ResourceConditionType<FeatureCondition> TYPE = ResourceConditionType.create(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID,
		"feature_on"), CODEC);

	/** Registers the condition; before any data pack loads (mod initialization). */
	public static void init() {
		ResourceConditions.register(TYPE);
	}

	@Override
	public ResourceConditionType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean test(RegistryOps.@Nullable RegistryInfoLookup registryInfo) {
		for (Features.Feature f : Features.Feature.values()) {
			if (f.key.equals(this.feature)) {
				return Features.on(f);
			}
		}
		SkyCraft.LOG.warn("SkyCraft: a data pack file asks for unknown feature '{}' (skycraft:feature_on); it isn't loaded", this.feature);
		return false;
	}
}
