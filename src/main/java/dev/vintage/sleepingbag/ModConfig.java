package dev.vintage.sleepingbag;

import mods.vintage.core.helpers.ConfigHelper;
import mods.vintage.core.platform.config.ItemBlockID;
import mods.vintage.core.platform.lang.LocalizationProvider;
import net.minecraftforge.common.Configuration;

@LocalizationProvider
public class ModConfig extends Configuration {

    @LocalizationProvider.List(modId = SleepingBag.modId)
    public static String[] langs;

    public static ItemBlockID SLEEPING_BAG_ID;
    public static ItemBlockID DUMMY_ID;

    public ModConfig() {
        super(ConfigHelper.getConfigFileFor(SleepingBag.modId));
        load();

        SLEEPING_BAG_ID = ItemBlockID.ofItem("sleepingBag", 1000);
        DUMMY_ID = ItemBlockID.ofItem("dummy", 1001);
        langs = ConfigHelper.getLocalizations(this, new String[]{"en_US"}, SleepingBag.modId);
    }
}
