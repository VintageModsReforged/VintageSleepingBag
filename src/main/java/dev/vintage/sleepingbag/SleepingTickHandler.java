package dev.vintage.sleepingbag;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.entity.player.EntityPlayer;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class SleepingTickHandler implements ITickHandler {

    private final Map<String, Integer> suspended = new HashMap<String, Integer>();

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
        EntityPlayer player = (EntityPlayer) tickData[0];
        if (player.worldObj.isRemote || !ItemSleepingBag.isUsingSleepingBag(player)) return;

        if (player.isPlayerSleeping()) {
            suspended.put(player.username, player.sleepTimer);
            ItemSleepingBag.setSleepingFlag(player, false);
        } else {
            ItemSleepingBag.finishSleeping(player);
        }
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        EntityPlayer player = (EntityPlayer) tickData[0];
        Integer timer = suspended.remove(player.username);
        if (timer == null) return;

        ItemSleepingBag.setSleepingFlag(player, true);
        player.sleepTimer = Math.min(timer + 1, 100);
        if (player.worldObj.isDaytime()) {
            player.wakeUpPlayer(false, true, true);
        }
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.PLAYER);
    }

    @Override
    public String getLabel() {
        return "SleepingBagTicks";
    }
}
