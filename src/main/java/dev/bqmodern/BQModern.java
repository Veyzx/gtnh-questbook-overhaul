package dev.bqmodern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = "bqmodern", name = "BetterQuesting Modern UI", version = "0.1.0",
        dependencies = "required-after:betterquesting", acceptableRemoteVersions = "*")
public class BQModern {
    public static final Logger LOG = LogManager.getLogger("bqmodern");

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (cpw.mods.fml.common.FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new Hooks());
        }
    }
}
