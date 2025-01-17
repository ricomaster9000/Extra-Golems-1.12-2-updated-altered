package com.golems.entity;

import java.util.List;

import com.golems.blocks.ContainerPortableWorkbench;
import com.golems.main.ExtraGolems;
import com.golems.util.GolemNames;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

public final class EntityCraftingGolem extends GolemBase {

  public static final String ALLOW_SPECIAL = "Allow Special: Crafting";

  public EntityCraftingGolem(final World world) {
    super(world);
    this.setLootTableLoc(GolemNames.CRAFTING_GOLEM);
    this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.29D);
    this.addHealItem(new ItemStack(Blocks.PLANKS, 1, OreDictionary.WILDCARD_VALUE), 0.25D);
  }

  @Override
  protected ResourceLocation applyTexture() {
    return makeTexture(ExtraGolems.MODID, GolemNames.CRAFTING_GOLEM);
  }

  @Override
  protected boolean processInteract(final EntityPlayer player, final EnumHand hand) {
    final ItemStack itemstack = player.getHeldItem(hand);
    if (!player.world.isRemote && itemstack.isEmpty() && !player.isSneaking()) {
      // display crafting grid for player
      player.displayGui(new EntityCraftingGolem.InterfaceCraftingGrid(player.world, player.bedLocation));
      player.addStat(StatList.CRAFTING_TABLE_INTERACTION);
      player.swingArm(hand);
    }

    return super.processInteract(player, hand);
  }

  @Override
  public SoundEvent getGolemSound() {
    return SoundEvents.BLOCK_WOOD_STEP;
  }

  @Override
  public List<String> addSpecialDesc(final List<String> list) {
    if (getConfig(this).getBoolean(ALLOW_SPECIAL)) {
      list.add(TextFormatting.BLUE + trans("entitytip.click_open_crafting"));
    }
    return list;
  }

  public static class InterfaceCraftingGrid extends net.minecraft.block.BlockWorkbench.InterfaceCraftingTable {

    private final World world2;
    private final BlockPos position2;

    public InterfaceCraftingGrid(final World worldIn, final BlockPos pos) {
      super(worldIn, pos);
      this.world2 = worldIn;
      this.position2 = pos;
    }

    @Override
    public Container createContainer(final InventoryPlayer playerInventory, final EntityPlayer playerIn) {
      return new ContainerPortableWorkbench(playerInventory, this.world2, this.position2);
    }
  }
}
