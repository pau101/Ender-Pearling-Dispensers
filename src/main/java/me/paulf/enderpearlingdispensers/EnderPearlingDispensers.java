package me.paulf.enderpearlingdispensers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.dispenser.IPosition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.item.EnderPearlEntity;
import net.minecraft.entity.projectile.ThrowableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.DispenserTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mod("enderpearlingdispensers")
public class EnderPearlingDispensers {
	private static final String DISPENSED = "dispensed";

	public EnderPearlingDispensers() {
		final IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		bus.addListener(this::onInit);
	}

	private void onInit(final FMLCommonSetupEvent event) {
		DispenserBlock.registerDispenseBehavior(Items.ENDER_PEARL, new DefaultDispenseItemBehavior() {
			private final Method setMarker = ObfuscationReflectionHelper.findMethod(ArmorStandEntity.class, "func_181027_m", boolean.class);

			@Override
			public ItemStack dispenseStack(final IBlockSource source, final ItemStack stack) {
				final World world = source.getWorld();
				final ArmorStandEntity dummy = EntityType.ARMOR_STAND.create(world);
				final EnderPearlEntity pearl = new EnderPearlEntity(world, dummy);
				dummy.moveToBlockPosAndAngles(source.getBlockPos(), 0.0F, 0.0F);
				dummy.setInvisible(true);
				dummy.setNoGravity(true);
				try {
					this.setMarker.invoke(dummy, true);
				} catch (final IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
				final IPosition pearlPos = DispenserBlock.getDispensePosition(source);
				pearl.setPosition(pearlPos.getX(), pearlPos.getY(), pearlPos.getZ());
				final Direction facing = source.getBlockState().get(DispenserBlock.FACING);
				pearl.shoot(facing.getXOffset(), facing.getYOffset() + 0.1D, facing.getZOffset(), 1.5F, 1.0F);
				pearl.addTag(DISPENSED);
				world.func_217376_c(dummy);
				world.func_217376_c(pearl);
				stack.shrink(1);
				return stack;
			}
		});
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (final ProjectileImpactEvent.Throwable e) -> {
			final ThrowableEntity throwable = e.getThrowable();
			final LivingEntity thrower = throwable.getThrower();
			final RayTraceResult trace = e.getRayTraceResult();
			final World world = throwable.world;
			if (!world.isRemote && throwable instanceof EnderPearlEntity && thrower instanceof ArmorStandEntity && throwable.removeTag(DISPENSED)) {
				if (trace.getType() == RayTraceResult.Type.BLOCK) {
					final BlockRayTraceResult blockTrace = (BlockRayTraceResult) trace;
					final BlockPos srcPos = new BlockPos(thrower);
					final BlockState srcState = world.getBlockState(srcPos);
					final TileEntity srcEntity = world.getTileEntity(srcPos);
					if (srcState.getBlock() instanceof DispenserBlock && srcEntity instanceof DispenserTileEntity) {
						final BlockPos dstPos = blockTrace.getPos().offset(blockTrace.getFace());
						if (world.getBlockState(dstPos).getMaterial().isReplaceable()) {
							final CompoundNBT nbt = srcEntity.write(new CompoundNBT());
							final BlockState air = Blocks.AIR.getDefaultState();
							world.removeTileEntity(srcPos);
							if (world.setBlockState(srcPos, air, Constants.BlockFlags./*NO_*/UPDATE_NEIGHBORS)) {
								if (world.setBlockState(dstPos, srcState)) {
									world.markAndNotifyBlock(srcPos, world.getChunk(srcPos), srcState, air, Constants.BlockFlags.DEFAULT);
									final TileEntity dstEntity = world.getTileEntity(dstPos);
									if (dstEntity instanceof DispenserTileEntity) {
										nbt.putInt("x", dstPos.getX());
										nbt.putInt("y", dstPos.getY());
										nbt.putInt("z", dstPos.getZ());
										dstEntity.read(nbt);
									}
								} else {
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
				thrower.remove();
			}
		});
	}
}
