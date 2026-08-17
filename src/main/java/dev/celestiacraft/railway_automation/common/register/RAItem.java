package dev.celestiacraft.railway_automation.common.register;

import com.tterrag.registrate.util.entry.ItemEntry;
import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.celestiacraft.railway_automation.common.item.map_locator.MapLocatorItem;

public class RAItem {
	public static final ItemEntry<MapLocatorItem> MAP_LOCATOR;

	static {
		MAP_LOCATOR = RailwayAutomation.REGISTRATE.item("map_locator", MapLocatorItem::new)
				.model((context, provider) -> {
					provider.handheld(context::getEntry, provider.modLoc("item/map_locator"));
				})
				.register();
	}

	public static void register() {
	}
}