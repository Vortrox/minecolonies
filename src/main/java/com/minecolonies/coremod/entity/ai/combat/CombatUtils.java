package com.minecolonies.coremod.entity.ai.combat;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMob;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.coremod.entity.citizen.EntityCitizen;
import com.minecolonies.coremod.items.ItemSpear;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Map;

/**
 * Utility class of combat functions
 */
public class CombatUtils
{
    /**
     * Shooting constants
     */
    private static final double AIM_HEIGHT                     = 2.0D;
    private static final double ARROW_SPEED                    = 1.4D;
    private static final double AIM_SLIGHTLY_HIGHER_MULTIPLIER = 0.18;
    private static final double SPEED_FOR_DIST                 = 35;

    /**
     * Get an arrow entity for the given shooter
     *
     * @param shooter entity
     * @return arrow entity
     */
    public static AbstractArrowEntity createArrowForShooter(final LivingEntity shooter)
    {
        AbstractArrowEntity arrowEntity = ModEntities.MC_NORMAL_ARROW.create(shooter.level);
        arrowEntity.setOwner(shooter);

        final ItemStack rangedWeapon = shooter.getItemInHand(Hand.MAIN_HAND);
        final Item rangedWeaponItem = rangedWeapon.getItem();
        if (rangedWeaponItem instanceof BowItem)
        {
            arrowEntity = ((BowItem) rangedWeaponItem).customArrow(arrowEntity);
        }
        else if (rangedWeaponItem instanceof ItemSpear)
        {
            arrowEntity = ModEntities.SPEAR.create(shooter.level);
        }
        else if (rangedWeaponItem instanceof TridentItem)
        {
            arrowEntity = EntityType.TRIDENT.create(shooter.level);
        }

        arrowEntity.setPos(shooter.getX(), shooter.getY() + 1, shooter.getZ());
        return arrowEntity;
    }

    /**
     * Shoots a given arrow at the given target with a hit chance
     *
     * @param arrow the arrow entity to be shot
     * @param target the target to be shot at
     * @param hitChance the chance the target will be hit
     */
    public static void shootArrow(final AbstractArrowEntity arrow, final LivingEntity target, final float hitChance)
    {
        final double xVector = target.getX() - arrow.getX();
        final double yVector = target.getBoundingBox().minY + target.getBbHeight() / AIM_HEIGHT - arrow.getY();
        final double zVector = target.getZ() - arrow.getZ();
        final double distance = MathHelper.sqrt(xVector * xVector + zVector * zVector);
        final double dist3d = MathHelper.sqrt(yVector * yVector + xVector * xVector + zVector * zVector);
        arrow.shoot(xVector, yVector + distance * AIM_SLIGHTLY_HIGHER_MULTIPLIER, zVector, (float) (ARROW_SPEED * 1 + (dist3d / SPEED_FOR_DIST)), (float) hitChance);
        target.level.addFreshEntity(arrow);
    }

    /**
     * Actions on changing to a new target entity
     */
    public static void notifyGuardsOfTarget(final AbstractEntityCitizen user, final LivingEntity target, final int callRange)
    {
        for (final ICitizenData citizen : user.getCitizenData().getWorkBuilding().getAllAssignedCitizen())
        {
            if (citizen.getEntity().isPresent() && citizen.getEntity().get().getLastHurtByMob() == null)
            {
                ((EntityCitizen) citizen.getEntity().get()).getThreatTable().addThreat(target, 0);
            }
        }

        if (target instanceof AbstractEntityMinecoloniesMob)
        {
            for (final Map.Entry<BlockPos, IBuilding> entry : user.getCitizenColonyHandler().getColony().getBuildingManager().getBuildings().entrySet())
            {
                if (entry.getValue() instanceof AbstractBuildingGuards &&
                      user.blockPosition().distSqr(entry.getKey()) < callRange)
                {
                    final AbstractBuildingGuards building = (AbstractBuildingGuards) entry.getValue();
                    building.setTempNextPatrolPoint(target.blockPosition());
                }
            }
        }
    }
}
