#include "WorldSync.h"

// Skyrim farming (switch skyrim_farming). Minecraft's hoe tills Skyrim's soil into Minecraft farmland (fabric
// farming.SkyFarming); Skyrim's own crops are harvested as before, by activating them (G, or a right click Minecraft has no
// use for). So that a hoe pointed at a cabbage harvests it instead of tilling the ground under it, Minecraft is told
// when the crosshair is on something Skyrim can harvest (SkyState::aim).
namespace skycraft::Farming
{
	std::uint8_t Aim()
	{
		auto* pick = RE::CrosshairPickData::GetSingleton();
		if (!pick) {
			return 0;
		}
		const auto target = pick->GetActiveTarget().get();
		auto*      base = target ? target->GetBaseObject() : nullptr;
		if (!base || (!base->Is(RE::FormType::Flora) && !base->Is(RE::FormType::Tree))) {
			return 0;
		}
		auto* produce = base->As<RE::TESProduceForm>();
		if (!produce || !produce->produceItem || (target->formFlags & RE::TESObjectREFR::RecordFlags::kHarvested) != 0) {
			return 0;  // nothing to pick, or picked already
		}
		return proto::kAimHarvest;
	}
}
