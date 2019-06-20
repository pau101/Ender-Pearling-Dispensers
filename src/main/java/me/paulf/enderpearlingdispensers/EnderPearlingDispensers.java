package me.paulf.enderpearlingdispensers;

import net.minecraft.block.BlockDispenser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.dispenser.IPosition;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mod(modid = "enderpearlingdispensers", useMetadata = true, acceptableRemoteVersions = "*")
public class EnderPearlingDispensers {
	private static final String DISPENSED = "dispensed";

	@Mod.EventHandler
	public void onInit(final FMLPreInitializationEvent event) {
		BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(Items.ENDER_PEARL, new BehaviorDefaultDispenseItem() {
			private final Method setMarker = ObfuscationReflectionHelper.findMethod(EntityArmorStand.class, "func_181027_m", void.class, boolean.class);

			@Override
			public ItemStack dispenseStack(final IBlockSource source, final ItemStack stack) {
				final World world = source.getWorld();
				final EntityArmorStand dummy = new EntityArmorStand(world);
				final EntityEnderPearl pearl = new EntityEnderPearl(world, dummy);
				dummy.moveToBlockPosAndAngles(source.getBlockPos(), 0.0F, 0.0F);
				dummy.setInvisible(true);
				dummy.setNoGravity(true);
				try {
					this.setMarker.invoke(dummy, true);
				} catch (final IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
				final IPosition pearlPos = BlockDispenser.getDispensePosition(source);
				pearl.setPosition(pearlPos.getX(), pearlPos.getY(), pearlPos.getZ());
				final EnumFacing facing = source.getBlockState().getValue(BlockDispenser.FACING);
				pearl.shoot(facing.getXOffset(), facing.getYOffset() + 0.1D, facing.getZOffset(), 1.5F, 1.0F);
				pearl.addTag(DISPENSED);
				world.spawnEntity(dummy);
				world.spawnEntity(pearl);
				stack.shrink(1);
				return stack;
			}
		});
		MinecraftForge.EVENT_BUS.register(new Object() {
			@SubscribeEvent(priority = EventPriority.LOWEST)
			public void onProjectileImpact(final ProjectileImpactEvent.Throwable event) {
				final EntityThrowable throwable = event.getThrowable();
				final EntityLivingBase thrower = throwable.getThrower();
				final RayTraceResult trace = event.getRayTraceResult();
				final World world = throwable.world;
				if (!world.isRemote && throwable instanceof EntityEnderPearl && thrower instanceof EntityArmorStand && throwable.removeTag(DISPENSED)) {
					if (trace.typeOfHit == RayTraceResult.Type.BLOCK) {
						final BlockPos srcPos = new BlockPos(thrower);
						final IBlockState srcState = world.getBlockState(srcPos);
						final TileEntity srcEntity = world.getTileEntity(srcPos);
						if (srcState.getBlock() instanceof BlockDispenser && srcEntity instanceof TileEntityDispenser) {
							final BlockPos dstPos = trace.getBlockPos().offset(trace.sideHit);
							if (world.getBlockState(dstPos).getBlock().isReplaceable(world, dstPos)) {
								final NBTTagCompound nbt = srcEntity.writeToNBT(new NBTTagCompound());
								world.removeTileEntity(srcPos);
								final IBlockState air = Blocks.AIR.getDefaultState();
								if (world.setBlockState(srcPos, air, 16)) {
									if (world.setBlockState(dstPos, srcState)) {
										world.markAndNotifyBlock(srcPos, world.getChunk(srcPos), srcState, air, 3);
										final TileEntity dstEntity = world.getTileEntity(dstPos);
										if (dstEntity instanceof TileEntityDispenser) {
											nbt.setInteger("x", dstPos.getX());
											nbt.setInteger("y", dstPos.getY());
											nbt.setInteger("z", dstPos.getZ());
											dstEntity.readFromNBT(nbt);
										}
									} else {
										// Initial setBlockState will not guarantee the specified state is set as Block#onBlockAdded may change facing
										world.setBlockState(srcPos, srcState);
										// Now that block is set the specific state can be changed with full control
										world.setBlockState(srcPos, srcState);
										srcEntity.validate();
										world.setTileEntity(srcPos, srcEntity);
									}
								} else {
									srcEntity.validate();
									world.setTileEntity(srcPos, srcEntity);
								}
							}
						}
					}
					thrower.setDead();
				}
			}
		});
	}
}
