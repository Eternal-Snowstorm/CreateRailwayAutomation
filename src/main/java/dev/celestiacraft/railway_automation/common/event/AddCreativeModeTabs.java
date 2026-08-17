package dev.celestiacraft.railway_automation.common.event;

import com.simibubi.create.AllCreativeModeTabs;
import dev.celestiacraft.railway_automation.common.register.RABlock;
import dev.celestiacraft.railway_automation.common.register.RAItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class AddCreativeModeTabs {
	@SubscribeEvent
	public static void buildContents(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())) {
			event.accept(RABlock.TRACK_PLACER.get().asItem());
			event.accept(RAItem.MAP_LOCATOR.get());
		}
	}
}