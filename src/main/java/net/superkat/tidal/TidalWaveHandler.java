package net.superkat.tidal;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.water.Shoreline;
import net.superkat.tidal.water.WaterBody;
import net.superkat.tidal.water.WaterBodyHandler;

import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used for handling when tidal waves should spawn, as well as ticking the {@link WaterBodyHandler}
 */
public class TidalWaveHandler {
    public final ClientWorld world;
    public WaterBodyHandler waterBodyHandler;

    public boolean nearbyChunksLoaded = false;
    public int waveTicks = 0;

    public TidalWaveHandler(ClientWorld world) {
        this.world = world;
        this.waterBodyHandler = new WaterBodyHandler(world, this);
    }

    public void reloadNearbyChunks() {
        this.nearbyChunksLoaded = false;
    }

    public boolean nearbyChunksLoaded(ClientPlayerEntity player, int chunkRadius) {
        if(nearbyChunksLoaded) return true;
        int radius = chunkRadius * 8;

        //using WorldChunk instead of chunk because it has "isEmpty" method
        //could use chunk instanceof EmptyChunk instead, but this felt better
        BlockPos playerPos = player.getBlockPos();
        List<WorldChunk> checkChunks = List.of(
                world.getWorldChunk(playerPos.add(radius, 0, radius)),
                world.getWorldChunk(playerPos.add(-radius, 0, radius)),
                world.getWorldChunk(playerPos.add(-radius, 0, -radius)),
                world.getWorldChunk(playerPos.add(radius, 0, -radius))
        );
        return checkChunks.stream().noneMatch(WorldChunk::isEmpty);
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player, TidalConfig.chunkRadius);
        }

        waterBodyHandler.tick();
        debugTick(client, player);
    }

    public void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if(shouldDebugTick()) {
            waveTicks++;
            Set<WaterBody> waterBodies = waterBodyHandler.waterBodies.stream().filter(waterBody -> waterBody.getBlocks().size() >= 10).collect(Collectors.toSet());
            if(waveTicks >= 20 && !waterBodies.isEmpty() && !waterBodyHandler.shorelines.isEmpty()) {
                for (WaterBody waterBody : waterBodies) {
                    BlockPos spawnPos = waterBody.randomPos().add(0, 2, 0);
//                    BlockPos spawnPos = waterBody.centerPos();
                    Shoreline targetShoreline = waterBodyHandler.getClosestShoreline(spawnPos);
                    if(targetShoreline == null) return;
//                    Shoreline targetShoreline = waterBodyHandler.shorelines.stream().toList().get(getRandom().nextInt(waterBodyHandler.shorelines.size()));
                    if(targetShoreline.getBlocks().isEmpty()) return;
                    BlockPos targetPos = targetShoreline.randomPos().add(0, 2, 0);
//                    BlockPos targetPos = targetShoreline.center();
//                    BlockPos targetPos = player.getBlockPos();
                    int x = spawnPos.getX() - targetPos.getX();
                    int y = spawnPos.getY() - targetPos.getY();
                    int z = spawnPos.getZ() - targetPos.getZ();

                    Color color = randomDebugColor();
                    ParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                    this.world.addParticle(particleEffect, true, targetPos.getX() + 0.5, targetPos.getY() + 3, targetPos.getZ() + 0.5, x, y, z);

                    color = Color.red;
                    particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                    this.world.addParticle(particleEffect, true, spawnPos.getX() + 0.5, spawnPos.getY() + 3, spawnPos.getZ() + 0.5, 0, 0, 0);

                    color = Color.orange;
                    particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                    this.world.addParticle(particleEffect, true, targetPos.getX(), targetPos.getY() + 3, targetPos.getZ(), 0, 0, 0);
                }
            }
        }
    }

    private Color randomDebugColor() {
        Random random = getRandom();
        int rgbIncrease = random.nextBetween(1, 3);
        int red = rgbIncrease == 1 ? random.nextBetween(150, 255) : 255;
        int green = rgbIncrease == 2 ? random.nextBetween(150, 255) : 255;
        int blue = rgbIncrease == 3 ? random.nextBetween(150, 255) : 255;
        return new Color(red, green, blue);
    }

    public static boolean shouldDebugTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if(player.getActiveItem().isOf(Items.SPYGLASS) && player.getItemUseTime() >= 10) {
            if(player.getItemUseTime() == 10) {
                player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            }
            return true;
        }
        return false;
    }

    public static boolean altShouldDebugTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
       if(player.getActiveItem().isOf(Items.SHIELD) && (player.getItemUseTime() == 1 || player.isSneaking())) {
            player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            return true;
        }
        return false;
    }

    /**
     * @return A random Random with a randomly generated random RandomSeed seed.
     */
    public static Random getRandom() {
        return Random.create();
    }

    /**
     * @return A random with a seed that will, most likely, be synced between clients despite being client side. Lag may cause a small issue, but should be rare.
     */
    public static Random getSyncedRandom() {
        long time = MinecraftClient.getInstance().world.getTime();
        long random = 5L * Math.round(time / 5f); //math.ceil instead?
        return Random.create(random);
    }

    public static BlockPos topOfWater(ClientWorld world, BlockPos pos) {
        BlockPos.Mutable mutable = pos.mutableCopy().move(Direction.UP);

        while(posIsWater(world, mutable)) {
            mutable.move(Direction.UP);
        }
        return mutable.move(Direction.DOWN);
    }

    /**
     * Check if a BlockPos is water or is waterlogged
     *
     * @param world World to check in
     * @param pos BlockPos to check
     * @return If the BlockPos is water or waterlogged
     */
    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        FluidState state = world.getFluidState(pos);
        return state.isIn(FluidTags.WATER);
    }

    public static boolean stateIsWater(BlockState state) {
        return state.getFluidState().isIn(FluidTags.WATER);
    }

}
