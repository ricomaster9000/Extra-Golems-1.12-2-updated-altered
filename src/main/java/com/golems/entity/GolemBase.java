package com.golems.entity;

import com.golems.items.ItemBedrockGolem;
import com.golems.main.Config;
import com.golems.main.ExtraGolems;
import com.golems.util.GolemConfigSet;
import com.golems.util.GolemLookup;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Base class for all golems in this mod.
 **/
public abstract class GolemBase extends EntityIronGolem {

  private static final DataParameter<Boolean> CHILD = EntityDataManager.<Boolean>createKey(GolemBase.class,
      DataSerializers.BOOLEAN);

  public static final Logger LOGGER = LogManager.getFormatterLogger(ExtraGolems.MODID);

  private static final String KEY_CHILD = "isChild";
  public static final int WANDER_DISTANCE = 64;

  /** Map to customize healing items **/
  protected Map<ItemStack, Double> healItemMap = new HashMap<>();

  protected ResourceLocation textureLoc;
  protected ResourceLocation lootTableLoc;

  // customizable variables with default values //
  protected double knockbackY = 0.4000000059604645D;
  /** Amount by which to multiply damage if it's a critical. **/
  protected float criticalModifier = 2.25F;
  /** Percent chance to multiply damage [0, 100]. **/
  protected int criticalChance = 5;
  protected boolean takesFallDamage = false;
  protected boolean canDrown = false;

  private int attackTimer = 0;

  // swimming AI
  protected EntityAIBase swimmingAI = new EntityAISwimming(this);
  protected EntityAIBase wanderAvoidWater = null;
  protected EntityAIBase wander = null;

  /////////////// CONSTRUCTOR /////////////////

  /**
   * Initializes this golem with the given World. Also sets the following: <br>
   * {@code SharedMonsterAttributes.ATTACK_DAMAGE} using the config <br>
   * {@code SharedMonsterAttributes.MAX_HEALTH} using the config <br>
   * {@code takesFallDamage} to false <br>
   * {@code canSwim} to false. <br>
   * {@code healItemMap} using the golem building blocks
   * 
   * @param world the entity world
   **/
  public GolemBase(final World world) {
    super(world);
    this.setSize(1.4F, 2.9F);
    this.setCanTakeFallDamage(false);
    this.setCanSwim(false);
    GolemConfigSet cfg = getConfig(this);
    this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(cfg.getBaseAttack());
    this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(cfg.getMaxHealth());
    this.experienceValue = 4 + rand.nextInt((int) 8);
    // map healing items based on building blocks
    for (final Block b : getBuildingBlocks(this)) {
      Item i = Item.getItemFromBlock(b);
      if (i != Items.AIR) {
        healItemMap.put(new ItemStack(i), 0.75D);
      }
    }
  }

  ////////////// BEHAVIOR OVERRIDES //////////////////

  @Override
  public int getAttackTimer() {
    return this.attackTimer;
  }

  @Override
  protected void entityInit() {
    super.entityInit();
    this.setTextureType(this.applyTexture());
    this.getDataManager().register(CHILD, Boolean.valueOf(false));
  }

  @Override
  public void onLivingUpdate() {
    super.onLivingUpdate();
    if (this.attackTimer > 0)
    {
      --this.attackTimer;
    }

    if (this.getActivePotionEffect(MobEffects.REGENERATION) == null
            && rand.nextInt(40) == 0) {
      this.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 200 + 20 * (1 + rand.nextInt(8)), 1));
    }
  }

  @Override
  protected void applyEntityAttributes() {
    super.applyEntityAttributes();
    GolemConfigSet cfg = getConfig(this);
    this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(cfg.getBaseAttack());
    this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(cfg.getMaxHealth());
    this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.22D);
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void handleStatusUpdate(byte id)
  {
    if (id == 4)
    {
      this.attackTimer = 5;
      this.playSound(SoundEvents.ENTITY_IRONGOLEM_ATTACK, 1.0F, 1.0F);
    } else {
      super.handleStatusUpdate(id);
    }
  }

  /**
   * Returns true if this entity can attack entities of the specified class.
   */
  @Override
  public boolean canAttackClass(final Class<? extends EntityLivingBase> cls) {

    if (this.isPlayerCreated() && EntityPlayer.class.isAssignableFrom(cls)) {
      return Config.enableFriendlyFire();
    }
    if (EntityVillager.class.isAssignableFrom(cls) || GolemBase.class.isAssignableFrom(cls)) {
      return false;
    }
    return super.canAttackClass(cls);
  }

  @Override
  public boolean attackEntityAsMob(final Entity entity) {
    // calculate damage based on current attack damage and variance
    final float currentAttack = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE)
        .getAttributeValue();
    float damage = currentAttack + (float) (rand.nextDouble() - 0.5D) * 0.75F * currentAttack;
    // try to increase damage if random critical chance succeeds
    if (rand.nextInt(100) < this.criticalChance) {
      damage *= this.criticalModifier;
    }
    // use reflection to reset 'attackTimer' field
    this.attackTimer = 5;
    this.world.setEntityState(this, (byte) 4);
    final boolean flag = entity.attackEntityFrom(DamageSource.causeMobDamage(this), damage);

    if (flag) {
      entity.motionY += knockbackY;
      this.applyEnchantments(this, entity);
    }

    this.playSound(this.getThrowSound(), 1.0F, 0.9F + rand.nextFloat() * 0.2F);
    return flag;
  }

  /** Called when the mob is falling. Calculates and applies fall damage **/
  @Override
  public void fall(float distance, float damageMultiplier) {
    if (!this.canTakeFallDamage()) {
      return;
    }
    float[] ret = net.minecraftforge.common.ForgeHooks.onLivingFall(this, distance, damageMultiplier);
    if (ret == null) {
      return;
    }
    distance = ret[0];
    damageMultiplier = ret[1];
    super.fall(distance, damageMultiplier);
    PotionEffect potioneffect = this.getActivePotionEffect(MobEffects.JUMP_BOOST);
    float f = potioneffect == null ? 0.0F : (float) (potioneffect.getAmplifier() + 1);
    int i = MathHelper.ceil((distance - 3.0F - f) * damageMultiplier);

    if (i > 0) {
      this.playSound(this.getFallSound(i), 1.0F, 1.0F);
      this.attackEntityFrom(DamageSource.FALL, (float) i);
      int j = MathHelper.floor(this.posX);
      int k = MathHelper.floor(this.posY - 0.20000000298023224D);
      int l = MathHelper.floor(this.posZ);
      IBlockState iblockstate = this.world.getBlockState(new BlockPos(j, k, l));

      if (iblockstate.getMaterial() != Material.AIR) {
        SoundType soundtype = iblockstate.getBlock().getSoundType(iblockstate, world, new BlockPos(j, k, l), this);
        this.playSound(soundtype.getFallSound(), soundtype.getVolume() * 0.5F, soundtype.getPitch() * 0.75F);
      }
    }
  }

  @Override
  public int getMaxFallHeight() {
    return this.canTakeFallDamage() ? super.getMaxFallHeight() : 64;
  }

  /** Plays sound of golem walking **/
  @Override
  protected void playStepSound(final BlockPos pos, final Block block) {
    this.playSound(this.getWalkingSound(), 0.76F, 0.9F + rand.nextFloat() * 0.2F);
  }

  /**
   * Called when a user uses the creative pick block button on this entity.
   *
   * @param target The full target the player is looking at
   * @return A ItemStack to add to the player's inventory, Null if nothing should
   *         be added.
   */
  @Override
  public ItemStack getPickedResult(final RayTraceResult target) {
    Block pickBlock = GolemLookup.getFirstBuildingBlock(this.getClass());
    return pickBlock != null ? new ItemStack(pickBlock) : ItemStack.EMPTY;
  }

  @Override
  public void writeEntityToNBT(NBTTagCompound compound) {
    super.writeEntityToNBT(compound);
    compound.setBoolean(KEY_CHILD, this.isChild());
  }

  @Override
  public void readEntityFromNBT(NBTTagCompound compound) {
    super.readEntityFromNBT(compound);
    this.setChild(compound.getBoolean(KEY_CHILD));
  }

  @Override
  protected ResourceLocation getLootTable() {
    return this.lootTableLoc;
  }

  @Override
  protected boolean processInteract(final EntityPlayer player, final EnumHand hand) {
    final ItemStack stack = player.getHeldItem(hand);
    final float addHealth = Config.enableHealGolems() ? getHealAmount(stack) : 0;
    if (addHealth > 0 && this.getHealth() < this.getMaxHealth() && !player.isSneaking()) {
      heal(addHealth);
      stack.shrink(1);
      // if currently attacking this player, stop
      if (this.getAttackTarget() == player) {
        this.setRevengeTarget(null);
        this.setAttackTarget(null);
      }
      // spawn particles and play sound
      if (this.world.isRemote) {
        ItemBedrockGolem.spawnParticles(this.world, this.posX, this.posY + this.height / 2.0D, this.posZ, 0.12D,
            EnumParticleTypes.VILLAGER_HAPPY, 20);
      }
      this.playSound(SoundEvents.BLOCK_STONE_PLACE, 0.85F, 1.1F + rand.nextFloat() * 0.2F);
      return true;
    } else {
      return super.processInteract(player, hand);
    }
  }

  /////////////// OTHER SETTERS AND GETTERS /////////////////

  /**
   * Called after golem has been spawned. Parameters are the exact IBlockStates
   * used to make this golem (especially used with multi-textured golems)
   **/
  public void onBuilt(IBlockState body, IBlockState legs, IBlockState arm1, IBlockState arm2) {
  }

  public void setLootTableLoc(final ResourceLocation lootTable) {
    this.lootTableLoc = lootTable;
  }

  public void setLootTableLoc(String modid, final String name) {
    this.lootTableLoc = new ResourceLocation(modid, "entities/" + name);
  }

  public void setLootTableLoc(final String name) {
    this.setLootTableLoc(ExtraGolems.MODID, name);
  }

  public void setTextureType(final ResourceLocation texturelocation) {
    this.textureLoc = texturelocation;
  }

  public ResourceLocation getTextureType() {
    return this.textureLoc;
  }

  public void setChild(boolean isChild) {
    this.getDataManager().set(CHILD, isChild);
  }

  @Override
  public boolean isChild() {
    return this.getDataManager().get(CHILD).booleanValue();
  }

  /**
   * @param toSet true if the golem should take fall damage
   **/
  public void setCanTakeFallDamage(final boolean toSet) {
    this.takesFallDamage = toSet;
  }

  public boolean canTakeFallDamage() {
    return this.takesFallDamage;
  }

  /**
   * @param canSwim true if golem can swim, false if golem sinks
   **/
  public void setCanSwim(final boolean canSwim) {
    ((PathNavigateGround) this.getNavigator()).setCanSwim(canSwim);
    if (null == wander) {
      wander = new EntityAIWander(this, 0.8D);
    }
    if (null == wanderAvoidWater) {
      wanderAvoidWater = new EntityAIWanderAvoidWater(this, 0.8);
    }

    if (canSwim) {
      this.tasks.addTask(0, swimmingAI);
      this.tasks.addTask(5, wander);
      this.tasks.removeTask(wanderAvoidWater);
    } else {
      this.tasks.removeTask(swimmingAI);
      this.tasks.removeTask(wander);
      this.tasks.addTask(5, wanderAvoidWater);
    }
  }

  /**
   * @param toSet whether golem is immune to fire
   **/
  public void setImmuneToFire(final boolean toSet) {
    this.isImmuneToFire = toSet;
  }

  /**
   * Registers an item that can be used to heal the golem
   * 
   * @param s          an ItemStack containing the item
   * @param multiplier the percentage of health that should be added (typically
   *                   0.25 or 0.5)
   **/
  public GolemBase addHealItem(final ItemStack s, final double multiplier) {
    healItemMap.put(s, multiplier);
    return this;
  }

  /**
   * @return a Set of ItemStacks that are valid healing items
   **/
  public Set<ItemStack> getHealItems() {
    return healItemMap.keySet();
  }

  /**
   * Whether right-clicking on this entity triggers a texture change.
   *
   * @return True if this is a {@link GolemMultiTextured} or a
   *         {@link GolemColorizedMultiTextured} AND the config option is enabled.
   **/
  public boolean doesInteractChangeTexture() {
    return Config.interactChangesTexture() && (GolemMultiTextured.class.isAssignableFrom(this.getClass())
        || GolemColorizedMultiTextured.class.isAssignableFrom(this.getClass()));
  }

  /**
   * Does not change behavior, but is required when the utility block checks for
   * valid golems
   * @return true if the entity is currently providing light
   **/
  public boolean isProvidingLight() {
    return false;
  }

  /**
   * Does not change behavior, but is required when the utility block checks for
   * valid golems
   * @return true if the entity is currently providing redstone power
   **/
  public boolean isProvidingPower() {
    return false;
  }

  /** @return The Blocks used to build this golem, or null if there is none **/
  @Nullable
  public static Block[] getBuildingBlocks(GolemBase golem) {
    return GolemLookup.getBuildingBlocks(golem.getClass());
  }

  /**
   * @param golem the golem
   * @return The GolemConfigSet associated with this golem, or the empty GCS if there is
   * none
   **/
  @Nonnull
  public static GolemConfigSet getConfig(GolemBase golem) {
    return golem != null && GolemLookup.hasConfig(golem.getClass()) ? GolemLookup.getConfig(golem.getClass())
        : GolemConfigSet.EMPTY;
  }

  /**
   * @param i the ItemStack being used to heal the golem
   * @return the amount by which this item should heal the golem, in half-hearts.
   *         Defaults to 25% of max health or 32.0, whichever is smaller
   **/
  public float getHealAmount(final ItemStack i) {
    if (i != null && !i.isEmpty()) {
      // check each entry in the map for matches
      for (final Entry<ItemStack, Double> e : healItemMap.entrySet()) {
        // make sure item and metadata are the same (or WILDCARD)
        boolean itemMatches = e.getKey().getItem() == i.getItem();
        boolean metaMatches = e.getKey().getMetadata() == OreDictionary.WILDCARD_VALUE
            || e.getKey().getMetadata() == i.getMetadata();
        // if it's a match, use the mapped percentage to calculate health to restore
        if (itemMatches && metaMatches) {
          double h = this.getMaxHealth() * e.getValue();
          if (this.isChild()) {
            h *= 1.75D;
          }
          // maximum heal amount is 32, for no reason at all
          return Math.min((float) h, 32.0F);
        }
      }
    }
    return 0;
  }

  /**
   * Helper method for translating text into local language using {@code I18n}
   * @return the translated string
   * @see addSpecialDesc
   **/
  protected static String trans(final String s, final Object... strings) {
    return new TextComponentTranslation(s, strings).getFormattedText();
  }

  /////////////// TEXTURE HELPERS //////////////////

  /**
   * Makes a ResourceLocation using the passed mod id and part of the texture
   * name. Texture should be at 'assets/[MODID]/textures/entity/[TEXTURE].png'
   * @return a new ResourceLocation as specified
   **/
  public static ResourceLocation makeTexture(final String MODID, final String TEXTURE) {
    return new ResourceLocation(MODID + ":textures/entity/" + TEXTURE + ".png");
  }

  ///////////////////// SOUND OVERRIDES ////////////////////

  @Override
  /** @return the sound this mob makes intermittently **/
  protected SoundEvent getAmbientSound() {
    return getGolemSound();
  }

  /** @return the sound this mob makes when it walks. **/
  protected SoundEvent getWalkingSound() {
    return getGolemSound();
  }

  /** @return the sound this mob makes when it attacks. **/
  public SoundEvent getThrowSound() {
    return getGolemSound();
  }

  @Override
  /** @return the sound this mob makes when it is hurt **/
  protected SoundEvent getHurtSound(final DamageSource ignored) {
    return getGolemSound();
  }

  @Override
  /** @return the sound this mob makes when it dies **/
  protected SoundEvent getDeathSound() {
    return getGolemSound();
  }

  ////////////////////////////////////////////////////////////
  // Override ALL OF THE FOLLOWING FUNCTIONS FOR EACH GOLEM //
  ////////////////////////////////////////////////////////////

  /**
   * Allows each golem to add special information to in-game info (eg, Waila,
   * Hwyla, TOP, etc.). Typically checks if the Config allows this golem's special
   * ability (if it has one) and adds a formatted String to the passed list.
   *
   * @param list The list to which the golem adds description strings (separate
   *             entries are separate lines)
   * @return the passed list with or without this golem's added description
   **/
  public List<String> addSpecialDesc(final List<String> list) {
    return list;
  }

  /**
   * Called from {@link #entityInit()} and used to set the texture type
   * <b>before</b> the entity is fully constructed or rendered. Example
   * implementation: texture is at 'assets/golems/textures/entity/golem_clay.png'
   *
   * <pre>
   * protected ResourceLocation applyTexture() {
   * 	return this.makeGolemTexture("golems", "clay");
   * }
   * </pre>
   *
   * @return a ResourceLocation for this golem's texture
   * 
   * @see #makeTexture(String, String)
   **/
  protected abstract ResourceLocation applyTexture();

  /**
   * @return A SoundEvent to play when the golem is attacking, walking, hurt, and
   *         on death
   **/
  public abstract SoundEvent getGolemSound();
}
