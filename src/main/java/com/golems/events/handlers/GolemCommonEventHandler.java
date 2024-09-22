package com.golems.events.handlers;

import com.golems.blocks.BlockGolemHead;
import com.golems.entity.*;
import com.golems.items.ItemBedrockGolem;
import com.golems.main.Config;
import com.golems.main.ExtraGolems;
import com.golems.util.ReflectionUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.world.biome.*;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles events added specifically from this mod.
 **/
public class GolemCommonEventHandler {

  public static final Logger LOGGER = LogManager.getFormatterLogger(ExtraGolems.MODID);
  private static List<String> entityAINearestAttackableTargetTargetClassFieldNames = new ArrayList<>();

  @SubscribeEvent
  public void onPopulateChunk(PopulateChunkEvent.Post event) {
    ////// Spawn some basic golems in villages //////
    // percent chance that each chunk will contain a golem
    final int GOLEM_CHANCE = Config.getVillageGolemSpawnChance();
    final int DIMID = event.getWorld().provider.getDimension();
    if (DIMID == 0 && GOLEM_CHANCE > 0 && event.isHasVillageGenerated()
        && event.getRand().nextInt(100) < GOLEM_CHANCE) {
      // Make sure this is near a village and try to spawn a Golem
      BlockPos pos = new BlockPos(event.getChunkX() * 16, 100, event.getChunkZ() * 16);
      Village village = event.getWorld().villageCollection.getNearestVillage(pos, 32);
      if (village != null) {
        // spawn a golem based on the village biome
        Biome biome = event.getWorld().getBiome(pos);
        Class<? extends GolemBase> golemClazz = getGolemForBiome(biome, event.getRand());
        if (golemClazz != null) {
          GolemBase golemInstance = (GolemBase) EntityList.newEntity(golemClazz, event.getWorld());
          BlockPos spawn = getSafeSpawnPos(golemInstance, pos.add(8, 0, 8));
          if (spawn != null) {
            // spawn the golem
            golemInstance.setPosition(spawn.getX(), spawn.getY(), spawn.getZ());
            golemInstance.setPlayerCreated(false);
            event.getWorld().spawnEntity(golemInstance);
            // randomize texture if applicable
            if (golemInstance instanceof GolemMultiTextured) {
              byte texture = (byte) event.getRand().nextInt(((GolemMultiTextured) golemInstance).getNumTextures());
              ((GolemMultiTextured) golemInstance).setTextureNum(texture);
            } else if (golemInstance instanceof GolemColorizedMultiTextured) {
              byte texture = (byte) event.getRand()
                  .nextInt(((GolemColorizedMultiTextured) golemInstance).getColorArray().length);
              ((GolemColorizedMultiTextured) golemInstance).setTextureNum(texture);
            }
          }
        }
      }
    }
  }

  private static BlockPos getSafeSpawnPos(final EntityLivingBase entity, final BlockPos near) {
    final int radius = 6;
    final int maxTries = 24;
    BlockPos testing;
    for (int i = 0; i < maxTries; i++) {
      // get a random position near the passed BlockPos
      int x = near.getX() + entity.getEntityWorld().rand.nextInt(radius * 2) - radius;
      int z = near.getZ() + entity.getEntityWorld().rand.nextInt(radius * 2) - radius;
      int y = entity.getEntityWorld().getHeight(x, z) + 16;
      testing = new BlockPos(x, y, z);
      // make sure to end up with a solid block
      while (entity.getEntityWorld().isAirBlock(testing) && testing.getY() > 0) {
        testing = testing.down(1);
      }
      // check if golem can spawn there
      IBlockState iblockstate = entity.getEntityWorld().getBlockState(testing);
      if (iblockstate.canEntitySpawn(entity)) {
        return testing.up(1);
      }
    }

    return null;
  }

  /**
   * This method makes a list of golems to pick from based on the biome passed,
   * then returns a random member of that list.
   * 
   * @param biome The biome that this golem is in.
   * @param rand  the random number generator.
   * @return a Golem Class based on the biome and random chance. May be null.
   */
  @Nullable
  private static Class<? extends GolemBase> getGolemForBiome(final Biome biome, final Random rand) {
    List<Class<? extends GolemBase>> options = new ArrayList();

    // the following will be added to the options in certain biomes:
    if (biome instanceof BiomeDesert) {
      // use the config to get desert-type golems
      options.addAll(Config.getDesertGolems());
    } else if (biome instanceof BiomePlains || biome instanceof BiomeSavanna || biome instanceof BiomeTaiga) {
      // use the config to get plains-type golems
      options.addAll(Config.getPlainsGolems());
      options.add(EntityCobblestoneGolem.class);
    } else if (biome instanceof BiomeMesa) {
      // mesa-type golems
      options.add(EntityHardenedClayGolem.class);
      options.add(EntityStainedClayGolem.class);
    } else if (biome instanceof BiomeJungle) {
      // jungle-type golems
      options.add(EntityWoodenGolem.class);
      options.add(EntityLeafGolem.class);
    } else if (biome instanceof BiomeSnow) {
      // snow-type golems
      options.add(EntityIceGolem.class);
      options.add(EntityWoolGolem.class);
      options.add(EntityQuartzGolem.class);
    } else if (biome instanceof BiomeSwamp) {
      // swamp-type golems
      options.add(EntityWoodenGolem.class);
      options.add(EntitySlimeGolem.class);
      options.add(EntityLeafGolem.class);
      options.add(EntityClayGolem.class);
    }
    // add some rare and semi-rare golems
    final int clay = 3, crafting = 3, obsidian = 6, glowstone = 5, books = 4;
    if (rand.nextInt(clay) == 0) {
      options.add(EntityClayGolem.class);
    }
    if (rand.nextInt(crafting) == 0) {
      options.add(EntityCraftingGolem.class);
    }
    if (rand.nextInt(obsidian) == 0) {
      options.add(EntityObsidianGolem.class);
    }
    if (rand.nextInt(glowstone) == 0) {
      options.add(EntityGlowstoneGolem.class);
    }
    if (rand.nextInt(books) == 0) {
      options.add(EntityBookshelfGolem.class);
    }
    // choose a random golem from the list, or null
    return options.isEmpty() ? null : options.get(rand.nextInt(options.size()));
  }

  /**
   * Basically, this handler allows pumpkins to be placed anywhere (as long as
   * it's done by a player). Then upon placement, we try to spawn a golem based on
   * the blocks the pumpkin is on.
   * 
   * Note: This seems to be called twice on client and twice on server. May have
   * problems with dedicated server, or it might be fine.
   */
  @SubscribeEvent
  public void onPlayerPlaceBlock(PlayerInteractEvent.RightClickBlock event) {
    ItemStack stack = event.getItemStack();
    // check qualifications for running this event...
    if (Config.doesPumpkinBuildGolem() && !event.isCanceled() && !stack.isEmpty() && stack.getItem() instanceof ItemBlock) {
      Block heldBlock = ((ItemBlock) stack.getItem()).getBlock();
      // if player is holding pumpkin or lit pumpkin, try to place the block
      if (heldBlock instanceof BlockPumpkin) {
        // update the location to place block
        BlockPos pumpkinPos = event.getPos();
        Block clicked = event.getWorld().getBlockState(pumpkinPos).getBlock();
        if (!clicked.isReplaceable(event.getWorld(), pumpkinPos)) {
          pumpkinPos = pumpkinPos.offset(event.getFace());
        }
        // now we're ready to place the block
        if (event.getEntityPlayer().canPlayerEdit(pumpkinPos, event.getFace(), stack)) {
          IBlockState pumpkin = heldBlock
                  .getDefaultState()
                  .withProperty(BlockHorizontal.FACING, event.getEntityPlayer().getHorizontalFacing().getOpposite());
          // set block and trigger golem-checking
          if (event.getWorld().setBlockState(pumpkinPos, pumpkin)) {
            event.setCanceled(true);
            BlockGolemHead.trySpawnGolem(event.getWorld(), pumpkinPos);
            // reduce itemstack
            if (!event.getEntityPlayer().isCreative()) {
              event.getItemStack().shrink(1);
            }
          }
        }
      }
    }
  }

  /**
   * Allow healing of Iron Golems
   **/
  @SubscribeEvent
  public void onEntityInteract(final PlayerInteractEvent.EntityInteractSpecific event) {
    if (Config.enableHealGolems() && event.getTarget() instanceof EntityIronGolem
        && new ItemStack(Blocks.IRON_BLOCK).isItemEqual(event.getItemStack())) {
      // heal the golem and reduce the itemstack
      final EntityIronGolem golem = (EntityIronGolem) event.getTarget();
      if (golem.getHealth() < golem.getMaxHealth()) {
        golem.heal(golem.getMaxHealth() * 0.25F);
        event.getItemStack().shrink(1);
        // if currently attacking this player, stop
        if (golem.getAttackTarget() == event.getEntityPlayer()) {
          golem.setRevengeTarget(null);
          golem.setAttackTarget(null);
        }
        // spawn particles and play sound
        ItemBedrockGolem.spawnParticles(golem.getEntityWorld(), golem.posX, golem.posY + golem.height / 2.0D,
            golem.posZ, 0.12D, EnumParticleTypes.VILLAGER_HAPPY, 20);
        golem.playSound(SoundEvents.BLOCK_STONE_PLACE, 0.85F, 1.1F + golem.getRNG().nextFloat() * 0.2F);
      }
    }
  }

  /**
   * Prevents mobs from targeting inert Furnace Golems
   **/
  @SubscribeEvent
  public void onTargetEvent(final LivingSetAttackTargetEvent event) {
    if (event.getEntityLiving() instanceof EntityLiving && event.getTarget() instanceof EntityFurnaceGolem
        && !((EntityFurnaceGolem) event.getTarget()).hasFuel()) {
      // clear the attack target
      ((EntityLiving) event.getEntityLiving()).setAttackTarget(null);
    }
//				else if(event.getEntityLiving() instanceof EntityFurnaceGolem
//					&& !((EntityFurnaceGolem)event.getEntityLiving()).hasFuel()) {
//				((EntityFurnaceGolem)event.getEntityLiving()).setAttackTarget(null);
//			}
//		}
  }

  private Field fetchTargetClassFieldFromClass(Object aiTask) {
    Class<?> clazz = aiTask.getClass();
    clazz = !clazz.getSimpleName().equals("EntityAINearestAttackableTarget") ? aiTask.getClass().getSuperclass() : clazz;
    clazz = !clazz.getSimpleName().equals("EntityAINearestAttackableTarget") ? clazz.getSuperclass() : clazz;
    Field[] entityAINearestAttackableTargetFields = ReflectionUtil.getClassFields(clazz);
    //LOGGER.info("fields found: " + entityAINearestAttackableTargetFields.length);
    for (Field field : entityAINearestAttackableTargetFields) {
      //LOGGER.info("field name: " + field.getName());
      //LOGGER.info("field type: " + field.getType());
      Object fieldValue = ReflectionUtil.getFieldValueNoException(field.getName(), aiTask);
      if (field.getType().equals(Class.class) && fieldValue.equals(EntityPlayer.class)) {
        //LOGGER.info("found entityAINearestAttackableTargetTargetClassFieldName field");
        return field;
      }
    }
    return null;
  }

  private boolean doesAiTaskTargetPlayer(EntityAINearestAttackableTarget<?> aiTask) {
    boolean result = false;
    //LOGGER.info("AITask class name: " + aiTask.getClass().getSimpleName(), ", superclass = " + aiTask.getClass().getSuperclass().getSimpleName());
    Object fetchedFieldValue = null;
    for (String possibleFieldName : entityAINearestAttackableTargetTargetClassFieldNames) {
      fetchedFieldValue = ReflectionUtil.getFieldValueNoException(possibleFieldName, aiTask);
      if (fetchedFieldValue != null) {
        break;
      }
    }
    if (fetchedFieldValue == null || entityAINearestAttackableTargetTargetClassFieldNames.isEmpty()) {
      Field fetchedField = fetchTargetClassFieldFromClass(aiTask);
      if (fetchedField != null) {
        entityAINearestAttackableTargetTargetClassFieldNames.add(fetchedField.getName());
        fetchedFieldValue = ReflectionUtil.getFieldValueNoException(fetchedField.getName(), aiTask);
      }
    }
    return fetchedFieldValue != null && fetchedFieldValue.equals(EntityPlayer.class);
  }

  @SubscribeEvent
  public void onEntitySpawn(EntityJoinWorldEvent event) {
    Entity entity = event.getEntity();
    // Check if the entity is a zombie or skeleton
    if (entity instanceof EntityCreature) {
      EntityCreature livingEntity = (EntityCreature) entity;
      List<EntityAITasks.EntityAITaskEntry> toRemove = new ArrayList<>();
      boolean foundPlayerTargetingTask = false;

      for (EntityAITasks.EntityAITaskEntry entityAITaskEntry : livingEntity.targetTasks.taskEntries) {
        if(entityAITaskEntry.action instanceof EntityAINearestAttackableTarget) {
            if (doesAiTaskTargetPlayer((EntityAINearestAttackableTarget) entityAITaskEntry.action)) {
            toRemove.add(entityAITaskEntry);
            foundPlayerTargetingTask = true;
          }
        }
      }

      if(foundPlayerTargetingTask) {
        LOGGER.info("adjusting player target priority for " + entity.getName());
        for (EntityAITasks.EntityAITaskEntry taskEntry : toRemove) {
          livingEntity.targetTasks.removeTask(taskEntry.action);
        }
        livingEntity.targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(livingEntity, EntityPlayer.class, true));
      }
    }
  }
}
