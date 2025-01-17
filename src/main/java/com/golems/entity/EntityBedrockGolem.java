package com.golems.entity;

import java.util.List;

import com.golems.items.ItemBedrockGolem;
import com.golems.main.ExtraGolems;
import com.golems.main.GolemItems;
import com.golems.util.GolemNames;

import net.minecraft.block.Block;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class EntityBedrockGolem extends GolemBase {

  public EntityBedrockGolem(final World world) {
    super(world);
    this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.24D);
  }

  @Override
  protected ResourceLocation applyTexture() {
    return makeTexture(ExtraGolems.MODID, GolemNames.BEDROCK_GOLEM);
  }

  @Override
  public boolean isEntityInvulnerable(final DamageSource src) {
    return true;
  }

  @SideOnly(Side.CLIENT)
  @Override
  public boolean canRenderOnFire() {
    return false;
  }

  @Override
  protected boolean processInteract(final EntityPlayer player, final EnumHand hand) {
    // creative players can "despawn" by using spawnBedrockGolem on this entity
    final ItemStack itemstack = player.getHeldItem(hand);
    if (player.capabilities.isCreativeMode && !itemstack.isEmpty()
        && itemstack.getItem() == GolemItems.spawnBedrockGolem) {
      player.swingArm(hand);
      if (!this.world.isRemote) {
        this.setDead();
      } else {
        ItemBedrockGolem.spawnParticles(this.world, this.posX, this.posY + 0.1D, this.posZ, 0.1D);
      }
    }

    return super.processInteract(player, hand);
  }

  @Override
  protected void damageEntity(final DamageSource source, final float amount) {
    //
  }

  @Override
  public SoundEvent getGolemSound() {
    return SoundEvents.BLOCK_STONE_STEP;
  }

  @Override
  public ItemStack getPickedResult(final RayTraceResult target) {
    return new ItemStack(GolemItems.spawnBedrockGolem);
  }

  @Override
  public List<String> addSpecialDesc(final List<String> list) {
    list.add(TextFormatting.WHITE + "" + TextFormatting.BOLD + trans("entitytip.indestructible"));
    list.add(TextFormatting.DARK_RED + trans("tooltip.creative_only_item"));
    return list;
  }
}
