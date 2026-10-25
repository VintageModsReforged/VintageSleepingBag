package dev.vintage.sleepingbag;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import mods.vintage.core.platform.config.ConfigHandler;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;

@Mod(modid = SleepingBag.modId, name = SleepingBag.name, version = SleepingBag.version, acceptedMinecraftVersions = "[1.5.2]")
public class SleepingBag {

    public static final String modId = "SleepingBag";
    public static final String name = "Sleeping Bag";
    public static final String version = "1.5.2-1.0.0";

    public static ItemSleepingBag sleepingBag;
    public static ModConfig CONFIG = new ModConfig();
    public static ConfigHandler HANDLER = new ConfigHandler(modId);

    @Mod.PreInit
    public void preInit(FMLPreInitializationEvent e) {
        HANDLER.initIDs(CONFIG);
        TickRegistry.registerTickHandler(new SleepingTickHandler(), Side.SERVER);
    }

    @Mod.Init
    public void init(FMLInitializationEvent e) {
        HANDLER.confirmIDs(CONFIG);
        sleepingBag = new ItemSleepingBag();
        GameRegistry.registerItem(sleepingBag, "sleepingBag");
    }

    @Mod.PostInit
    public void postInit(FMLPostInitializationEvent e) {
        HANDLER.confirmOwnership(CONFIG);
        GameRegistry.addRecipe(new ItemStack(sleepingBag), "WWW", "SSS", 'W', Block.cloth, 'S', Item.silk);
    }
}
