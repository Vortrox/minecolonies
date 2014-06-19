package com.minecolonies.entity.ai;

import com.minecolonies.MineColonies;
import com.minecolonies.blocks.BlockHut;
import com.minecolonies.configuration.Configurations;
import com.minecolonies.entity.EntityBuilder;
import com.minecolonies.tileentities.TileEntityBuildable;
import com.minecolonies.tileentities.TileEntityHut;
import com.minecolonies.util.LanguageHandler;
import com.minecolonies.util.Schematic;
import com.minecolonies.util.Utils;
import net.minecraft.block.*;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Map;

/**
 * Performs builder work
 * Created: May 25, 2014
 *
 * @author Colton
 */
public class EntityAIWorkBuilder extends EntityAIBase
{
    private EntityBuilder builder;
    private World         world;

    public EntityAIWorkBuilder(EntityBuilder builder)
    {
        setMutexBits(3);
        this.builder = builder;
        this.world = builder.worldObj;
    }

    @Override
    public boolean shouldExecute()
    {
        return builder.isWorkTime() && (builder.hasSchematic() || builder.isBuilderNeeded());
    }

    @Override
    public void startExecuting()
    {
        if(!builder.hasSchematic())
        {
            loadSchematic();
        }
        Vec3 buildPos = builder.getSchematic().getPosition();
        builder.getNavigator().tryMoveToXYZ(buildPos.xCoord, buildPos.yCoord, buildPos.zCoord, 1.0F);

        LanguageHandler.sendPlayersLocalizedMessage(Utils.getPlayersFromUUID(world, builder.getTownHall().getOwners()), "entity.builder.messageBuildStart", builder.getSchematic().getName());
    }

    @Override
    public void updateTask()
    {
        //TODO: Need to do more in range and pathfind fail checks
        if(!builder.getNavigator().noPath())//traveling
        {
            if(builder.getNavigator().getPath().getFinalPathPoint().distanceToSquared(new PathPoint((int) builder.posX, (int) builder.posY, (int) builder.posZ)) < 4)//within 2 blocks
            {
                builder.getNavigator().clearPathEntity();
            }
            return;
        }

        if(builder.getOffsetTicks() % builder.getWorkInterval() == 0)
        {
            if(!builder.getSchematic().findNextBlock())//method returns false if there is no next block (schematic finished)
            {
                completeBuild();
                return;
            }

            Block block = builder.getSchematic().getBlock();
            if(block == null)//should never happen
            {
                MineColonies.logger.error("Schematic has null block");
                return;
            }
            int metadata = builder.getSchematic().getMetadata();
            int[] pos = Utils.vecToInt(builder.getSchematic().getBlockPosition());

            Block worldBlock = world.getBlock(pos[0], pos[1], pos[2]);
            if(worldBlock instanceof BlockHut || worldBlock == Blocks.bedrock) return;//don't overwrite huts or bedrock

            if(!Configurations.builderInfiniteResources)//We need to deal with materials
            {
                int slotID = builder.getInventory().containsItemStack(new ItemStack(block, 1, metadata));
                if(slotID == -1)
                {
                    ItemStack material = new ItemStack(block, 1, metadata);

                    int amount = -1;
                    for(ItemStack item : builder.getSchematic().getMaterials())
                    {
                        if(item.isItemEqual(material))
                        {
                            amount = item.stackSize;
                            break;
                        }
                    }

                    int chestSlotID = builder.getWorkHut().containsItemStack(material);
                    if(chestSlotID != -1)
                    {
                        if(builder.getWorkHut().getDistanceFrom(builder.posX, builder.posY, builder.posZ) < 64) //Square Distance
                        {
                            builder.getWorkHut().takeItem(builder.getInventory(), chestSlotID, amount);
                        }
                        else
                        {
                            builder.getNavigator().tryMoveToXYZ(builder.getWorkHut().xCoord, builder.getWorkHut().yCoord, builder.getWorkHut().zCoord, 1.0D);
                        }
                    }
                    else if(false)//TODO canCraft()
                    {
                        //TODO craft item
                    }
                    else
                    {
                        LanguageHandler.sendPlayersLocalizedMessage(Utils.getPlayersFromUUID(world, builder.getTownHall().getOwners()), "entity.builder.messageNeedMaterial", material.getDisplayName(), amount);
                        //TODO request material - deliveryman
                    }
                    return;
                }
                builder.getSchematic().useMaterial(builder.getInventory().getStackInSlot(slotID));
                builder.getInventory().decrStackSize(slotID, 1);

                ItemStack stack = worldBlock.getPickBlock(null, world, pos[0], pos[1], pos[2]);
                builder.getInventory().setStackInInventory(stack);
                //TODO unload full inventory
            }

            if(block == Blocks.air)
            {
                builder.swingItem();//TODO doesn't work, may need item in hand
                world.setBlockToAir(pos[0], pos[1], pos[2]);
            }
            else
            {
                if(!block.getMaterial().isSolid())
                {
                    //TODO create proper system, this works for torches
                    if(block == Blocks.torch)
                    {
                        if(metadata == 1 && world.getBlock(pos[0] - 1, pos[1], pos[2]) == Blocks.air)
                        {
                            world.setBlock(pos[0] - 1, pos[1], pos[2], Blocks.dirt);
                        }
                        else if(metadata == 2 && world.getBlock(pos[0] + 1, pos[1], pos[2]) == Blocks.air)
                        {
                            world.setBlock(pos[0] + 1, pos[1], pos[2], Blocks.dirt);
                        }
                        else if(metadata == 3 && world.getBlock(pos[0], pos[1], pos[2] - 1) == Blocks.air)
                        {
                            world.setBlock(pos[0], pos[1], pos[2] - 1, Blocks.dirt);
                        }
                        else if(metadata == 4 && world.getBlock(pos[0], pos[1], pos[2] + 1) == Blocks.air)
                        {
                            world.setBlock(pos[0], pos[1], pos[2] + 1, Blocks.dirt);
                        }
                    }
                }
                builder.swingItem();
                world.setBlock(pos[0], pos[1], pos[2], block, metadata, 0x02);

                TileEntity tileEntity = builder.getSchematic().getTileEntity();//TODO do we need to load TileEntities when building?
                if(tileEntity != null && !(world.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityHut))
                {
                    world.setTileEntity(pos[0], pos[1], pos[2], tileEntity);
                }
            }
        }
    }

    @Override
    public boolean continueExecuting()
    {
        return builder.isWorkTime() && builder.hasSchematic() && (builder.hasMaterials() || Configurations.builderInfiniteResources);
    }

    private void loadSchematic()
    {
        Map.Entry<int[], String> entry = builder.getTownHall().getBuilderRequired().entrySet().iterator().next();
        int[] pos = entry.getKey();
        String name = entry.getValue();

        builder.setSchematic(Schematic.loadSchematic(world, name));

        if(builder.getSchematic() == null)
        {
            MineColonies.logger.warn(LanguageHandler.format("entity.builder.ai.schematicLoadFailure", name));
            builder.getTownHall().removeHutForUpgrade(pos);
            return;
        }
        builder.getSchematic().setPosition(Vec3.createVectorHelper(pos[0], pos[1], pos[2]));
    }

    private void completeBuild()
    {
        String schematicName = builder.getSchematic().getName();
        LanguageHandler.sendPlayersLocalizedMessage(Utils.getPlayersFromUUID(world, builder.getTownHall().getOwners()), "entity.builder.messageBuildComplete", schematicName);
        int[] pos = Utils.vecToInt(builder.getSchematic().getPosition());

        if(world.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityBuildable)
        {
            int schematicLevel = Integer.parseInt(schematicName.substring(schematicName.length() - 1));

            TileEntityBuildable hut = (TileEntityBuildable) world.getTileEntity(pos[0], pos[1], pos[2]);
            hut.setBuildingLevel(schematicLevel);
        }

        builder.getTownHall().removeHutForUpgrade(pos);
        builder.setSchematic(null);
    }
}