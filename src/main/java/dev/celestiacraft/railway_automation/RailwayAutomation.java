package dev.celestiacraft.railway_automation;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RailwayAutomation.MODID)
public class RailwayAutomation {
	public static final String MODID = "railway_automation";
	public static final String NAME = "railway_automation";
	public static final Logger LOGGER = LogManager.getLogger(NAME);
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

	public static ResourceLocation loadResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public RailwayAutomation(FMLJavaModLoadingContext context){
		IEventBus bus = context.getModEventBus();

		REGISTRATE.registerEventListeners(bus);
	}
}