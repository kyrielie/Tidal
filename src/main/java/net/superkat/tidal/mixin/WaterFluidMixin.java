package net.superkat.tidal.mixin;

import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.superkat.tidal.TidalWaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaterFluid.class)
public class WaterFluidMixin {

//	@Inject(method = "randomDisplayTick", at = @At("TAIL"))
//	public void spawnWaterWaves(World world, BlockPos pos, FluidState state, Random random, CallbackInfo ci) {
//		TidalWaveHandler.tickWaterBlock(world, pos, state, random);
//	}

}