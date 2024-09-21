package com.golems.entity;

import com.golems.main.ExtraGolems;
import com.golems.util.GolemNames;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.List;

public final class EntityCobblestoneGolem extends GolemColorizedMultiTextured {
  public static final String PREFIX = "cobblestone";
  public static final int[] COLOR_ARRAY = ItemDye.DYE_COLORS;

  private static final ResourceLocation TEXTURE_BASE = GolemBase.makeTexture(ExtraGolems.MODID,
      GolemNames.COBBLESTONE_GOLEM + "_base");
  private static final ResourceLocation TEXTURE_OVERLAY = GolemBase.makeTexture(ExtraGolems.MODID,
      GolemNames.COBBLESTONE_GOLEM);

  public EntityCobblestoneGolem(final World world) {
    super(world, TEXTURE_BASE, TEXTURE_OVERLAY, COLOR_ARRAY);
    this.addHealItem(new ItemStack(Blocks.COBBLESTONE, 1, OreDictionary.WILDCARD_VALUE), 0.75D);
    this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.22D);
  }

  @Override
  public SoundEvent getGolemSound() {
    return SoundEvents.BLOCK_STONE_STEP;
  }

  @Override
  protected void damageEntity(DamageSource source, float amount) {
    super.damageEntity(source, amount);
  }

  @Override
  public void onBuilt(IBlockState body, IBlockState legs, IBlockState arm1, IBlockState arm2) {
    // use block metadata to give this golem the right texture (defaults to last
    // item in color array)
    final int meta = body.getBlock().getMetaFromState(body) % this.getColorArray().length;
    this.setTextureNum((byte) (this.getColorArray().length - meta - 1));
  }

  @Override
  public List<String> addSpecialDesc(final List<String> list) {
    return list;
  }
}
