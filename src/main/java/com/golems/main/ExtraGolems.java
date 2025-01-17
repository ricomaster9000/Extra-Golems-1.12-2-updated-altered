package com.golems.main;

import com.golems.integration.ModIds;
import com.golems.proxies.CommonProxy;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = ExtraGolems.MODID, name = ExtraGolems.NAME, version = ExtraGolems.VERSION)
public class ExtraGolems {

  // TODO - re-add all other gradle tasks for sonarqube, curseforge, etc. if need be
  // TODO - adjust textures used for Cobbletone golem
  // TODO - complete/fix localization for covvlestone golem
  // TODO - fix golem names when used in console output to screen in game not being correct
  // TODO - tune down passive regeneration for all golemns
  // TODO - tune down the strength of cobblestone golems a bit

  public static final String MODID = "golems";
  protected static final String NAME = "Extra Golems";
  protected static final String VERSION = "7.2.2";

  @SidedProxy(clientSide = "com." + MODID + ".proxies.ClientProxy", serverSide = "com." + MODID
      + ".proxies.CommonProxy")
  public static CommonProxy proxy;

  @Mod.Instance(ExtraGolems.MODID)
  public static ExtraGolems instance;

  public static final Logger LOGGER = LogManager.getFormatterLogger(ExtraGolems.MODID);

  @Mod.EventHandler
  public static void preInit(final FMLPreInitializationEvent event) {
    Config.mainRegistry(new Configuration(event.getSuggestedConfigurationFile()));
  }

  @Mod.EventHandler
  public static void init(final FMLInitializationEvent event) {

    proxy.registerEvents();

    if (Loader.isModLoaded(ModIds.WAILA)) {
      FMLInterModComms.sendMessage(ModIds.WAILA, "register",
          "com.golems.integration.waila.WailaExtraGolems.callbackRegister");
    }
    if (Loader.isModLoaded(ModIds.TOP)) {
      FMLInterModComms.sendFunctionMessage(ModIds.TOP, "getTheOneProbe",
          "com.golems.integration.theoneprobe.TOPExtraGolems$GetTheOneProbe");
    }
    // Trial-run these methods to give the user feedback if there's errors
    Config.getPlainsGolems();
    Config.getDesertGolems();
  }
}
