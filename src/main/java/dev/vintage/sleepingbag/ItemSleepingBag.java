package dev.vintage.sleepingbag;

import mods.vintage.core.helpers.BlockHelper;
import mods.vintage.core.helpers.pos.BlockPos;
import mods.vintage.core.platform.config.IItemBlockIDProvider;
import mods.vintage.core.platform.lang.Translator;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EnumStatus;
import net.minecraft.item.EnumArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet17Sleep;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;

import java.util.List;

public class ItemSleepingBag extends ItemArmor implements IItemBlockIDProvider {

    private static final String TAG_SPAWN = "Spawn";
    private static final String TAG_POSITION = "Position";
    private static final String TAG_SLEEPING = "Sleeping";
    private static final String TAG_SLOT = "Slot";

    private static final int CHESTPIECE_TYPE = 1;
    private static final int CHESTPIECE_SLOT = 2;

    public ItemSleepingBag() {
        super(ModConfig.SLEEPING_BAG_ID.get(), EnumArmorMaterial.IRON, 2, CHESTPIECE_TYPE);
        setMaxStackSize(1);
        setUnlocalizedName("sleepingBag");
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IconRegister iconRegister) {
        itemIcon = iconRegister.registerIcon("SleepingBag:sleeping_bag");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            int posX = MathHelper.floor_double(player.posX);
            int posY = MathHelper.floor_double(player.posY);
            int posZ = MathHelper.floor_double(player.posZ);
            ChunkCoordinates coords = new ChunkCoordinates(posX, posY, posZ);

            if (canPlayerSleep(player, world, coords)) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    stack.setTagCompound(tag);
                }

                storeOriginalSpawn(player, tag);
                storeOriginalPosition(player, tag);
                tag.setBoolean(TAG_SLEEPING, true);

                sleepSafe((EntityPlayerMP) player, world, coords);

                // Swap the sleeping bag into armor slot
                int slot = player.inventory.currentItem;
                ItemStack currentArmor = player.getCurrentArmor(CHESTPIECE_SLOT);
                if (currentArmor != null) currentArmor = currentArmor.copy();

                tag.setInteger(TAG_SLOT, slot);
                player.inventory.armorInventory[CHESTPIECE_SLOT] = stack.copy();
                player.inventory.setInventorySlotContents(slot, currentArmor);
            }
        }

        return stack;
    }

    @Override
    public boolean isValidArmor(ItemStack stack, int armorType, Entity entity) {
        return armorType == CHESTPIECE_TYPE;
    }

    // Vanilla wakes a sleeping player when there is no bed block at playerLocation,
    // so the sleeping flag is hidden from the vanilla tick and restored afterwards.
    public static boolean isUsingSleepingBag(EntityPlayer player) {
        ItemStack stack = player.inventory.armorInventory[CHESTPIECE_SLOT];
        if (stack == null || stack.getItem() != SleepingBag.sleepingBag) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(TAG_SLEEPING);
    }

    public static void setSleepingFlag(EntityPlayer player, boolean value) {
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayer.class, player, value, "sleeping", "field_71083_bS");
    }

    public static void finishSleeping(EntityPlayer player) {
        ItemStack stack = player.inventory.armorInventory[CHESTPIECE_SLOT];
        NBTTagCompound tag = stack.getTagCompound();
        restoreOriginalSpawn(player, tag);
        restoreOriginalPosition(player, tag);
        tag.removeTag(TAG_SLEEPING);
        getOutOfSleepingBag(player);
    }

    private static void sleepSafe(EntityPlayerMP player, World world, ChunkCoordinates pos) {
        if (player.isRiding()) player.mountEntity(null);

        setSleepingFlag(player, true);

        player.sleepTimer = 0;
        player.playerLocation = pos;
        player.motionX = player.motionY = player.motionZ = 0;
        world.updateAllPlayersSleepingFlag();

        Packet17Sleep packet = new Packet17Sleep(player, 0, pos.posX, pos.posY, pos.posZ);
        player.getServerForPlayer().getEntityTracker().sendPacketToAllPlayersTrackingEntity(player, packet);
        player.playerNetServerHandler.sendPacketToPlayer(packet);
    }

    private static EnumStatus vanillaCanSleep(EntityPlayer player, World world, BlockPos pos) {
        PlayerSleepInBedEvent event = new PlayerSleepInBedEvent(player, pos.getX(), pos.getY(), pos.getZ());
        MinecraftForge.EVENT_BUS.post(event);

        if (event.result != null) {
            return event.result;
        }

        if (!world.isRemote) {
            if (player.isPlayerSleeping() || !player.isEntityAlive()) return EnumStatus.OTHER_PROBLEM;
            if (!world.provider.isSurfaceWorld()) return EnumStatus.NOT_POSSIBLE_HERE;
            if (world.isDaytime()) return EnumStatus.NOT_POSSIBLE_NOW;

            AxisAlignedBB checkBox = AxisAlignedBB.getBoundingBox(pos.getX() - 8, pos.getY() - 5, pos.getZ() - 8, pos.getX() + 8, pos.getY() + 5, pos.getZ() + 8);
            List<EntityMob> mobs = world.getEntitiesWithinAABB(EntityMob.class, checkBox);
            if (!mobs.isEmpty()) return EnumStatus.NOT_SAFE;
        }

        return EnumStatus.OK;
    }

    private static boolean canPlayerSleep(EntityPlayer player, World world, ChunkCoordinates pos) {
        if (player.isPlayerSleeping() || !player.isEntityAlive()) return false;
        EnumStatus status = vanillaCanSleep(player, world, new BlockPos(pos.posX, pos.posY, pos.posZ));
        if (status == EnumStatus.NOT_POSSIBLE_NOW) {
            player.addChatMessage(Translator.RED.format("tile.bed.noSleep"));
        } else if (status == EnumStatus.NOT_SAFE) {
            player.addChatMessage(Translator.RED.format("tile.bed.notSafe"));
        } else if (status == EnumStatus.NOT_POSSIBLE_HERE) {
            player.addChatMessage(Translator.RED.format("sleepingbag.noDimension"));
        }
        if (status != EnumStatus.OK) return false;

        BlockPos ground = new BlockPos(pos.posX, pos.posY - 1, pos.posZ);
        boolean hasRoom = isNotSuffocating(world, pos) &&
                isNotSuffocating(world, new ChunkCoordinates(pos.posX, pos.posY + 1, pos.posZ)) &&
                isSolidEnough(world, ground);
        if (!hasRoom) {
            player.addChatMessage(Translator.RED.format("sleepingbag.noRoom"));
        }
        return hasRoom;
    }

    private static boolean isNotSuffocating(World world, ChunkCoordinates pos) {
        BlockPos bp = new BlockPos(pos.posX, pos.posY, pos.posZ);
        Block block = BlockHelper.getBlock(world, bp);
        return block == null || block.getCollisionBoundingBoxFromPool(world, bp.getX(), bp.getY(), bp.getZ()) == null;
    }

    private static boolean isSolidEnough(World world, BlockPos pos) {
        Block block = BlockHelper.getBlock(world, pos);
        if (block == null) return false;
        AxisAlignedBB box = block.getCollisionBoundingBoxFromPool(world, pos.getX(), pos.getY(), pos.getZ());
        return box != null && (box.maxY - box.minY) >= 0.5;
    }

    public static void getOutOfSleepingBag(EntityPlayer player) {
        ItemStack stack = player.inventory.armorInventory[CHESTPIECE_SLOT];
        if (stack != null && stack.getItem() == SleepingBag.sleepingBag) {
            if (!tryReturnToSlot(player, stack)) {
                player.inventory.armorInventory[CHESTPIECE_SLOT] = null;
                if (!player.inventory.addItemStackToInventory(stack)) {
                    EntityItem item = new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, stack.copy());
                    player.worldObj.spawnEntityInWorld(item);
                }
            }
        }

        if (player.isPlayerSleeping()) {
            player.wakeUpPlayer(false, true, true);
            player.worldObj.updateAllPlayersSleepingFlag();
        }
    }

    private static boolean tryReturnToSlot(EntityPlayer player, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        int slot = tag.getInteger(TAG_SLOT);
        tag.removeTag(TAG_SLOT);
        if (slot < 0 || slot >= player.inventory.mainInventory.length) return false;

        ItemStack oldArmor = player.inventory.mainInventory[slot];
        if (isChestplate(oldArmor)) {
            player.inventory.armorInventory[CHESTPIECE_SLOT] = oldArmor;
        } else {
            player.inventory.armorInventory[CHESTPIECE_SLOT] = null;
            if (oldArmor != null) return false;
        }

        player.inventory.setInventorySlotContents(slot, stack);
        return true;
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return item instanceof ItemArmor && ((ItemArmor) item).armorType == CHESTPIECE_TYPE;
    }

    private static void storeOriginalSpawn(EntityPlayer player, NBTTagCompound tag) {
        ChunkCoordinates spawn = player.getBedLocation();
        if (spawn != null) {
            tag.setInteger("xPos", spawn.posX);
            tag.setInteger("yPos", spawn.posY);
            tag.setInteger("zPos", spawn.posZ);
            tag.setBoolean(TAG_SPAWN, true);
        }
    }

    private static void restoreOriginalSpawn(EntityPlayer player, NBTTagCompound tag) {
        if (tag.getBoolean(TAG_SPAWN)) {
            int x = tag.getInteger("xPos");
            int y = tag.getInteger("yPos");
            int z = tag.getInteger("zPos");
            player.setSpawnChunk(new ChunkCoordinates(x, y, z), false);
            tag.removeTag(TAG_SPAWN);
        }
    }

    private static void storeOriginalPosition(EntityPlayer player, NBTTagCompound tag) {
        NBTTagCompound posTag = new NBTTagCompound();
        posTag.setDouble("x", player.posX);
        posTag.setDouble("y", player.posY);
        posTag.setDouble("z", player.posZ);
        tag.setTag(TAG_POSITION, posTag);
    }

    private static void restoreOriginalPosition(EntityPlayer player, NBTTagCompound tag) {
        if (tag.hasKey(TAG_POSITION)) {
            NBTTagCompound posTag = tag.getCompoundTag(TAG_POSITION);
            player.setPosition(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
            tag.removeTag(TAG_POSITION);
        }
    }
}
