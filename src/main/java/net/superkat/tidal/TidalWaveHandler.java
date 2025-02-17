package net.superkat.tidal;

import com.google.common.collect.Sets;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.water.DebugHelper;
import net.superkat.tidal.water.SitePos;
import net.superkat.tidal.water.WaterBodyHandler;

import java.util.List;
import java.util.Set;

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

    //This isn't perfect, but its close enough I suppose
    public boolean nearbyChunksLoaded(ClientPlayerEntity player) {
        if(nearbyChunksLoaded) return true;
        int chunkRadius = getChunkRadius();

        //using WorldChunk instead of chunk because it has "isEmpty" method
        //could use chunk instanceof EmptyChunk instead, but this felt better
        int chunkX = player.getChunkPos().x;
        int chunkZ = player.getChunkPos().z;
        int chunkRadiusReduced = chunkRadius - (chunkRadius / 3);

        List<WorldChunk> checkChunks = List.of(
                world.getChunk(chunkX + chunkRadius, chunkZ),
                world.getChunk(chunkX - chunkRadius, chunkZ),
                world.getChunk(chunkX, chunkZ + chunkRadius),
                world.getChunk(chunkX, chunkZ - chunkRadius),
                world.getChunk(chunkX + (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                world.getChunk(chunkX - (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                world.getChunk(chunkX - (chunkRadiusReduced), chunkZ - (chunkRadiusReduced)),
                world.getChunk(chunkX + (chunkRadiusReduced), chunkZ - (chunkRadiusReduced))
        );
        return checkChunks.stream().noneMatch(WorldChunk::isEmpty);
//        return MinecraftClient.getInstance().worldRenderer.isTerrainRenderComplete();
    }

    //Gets all loaded nearby chunks - created using ClientChunkManager & ClientChunkManager.ClientChunkMap
    public Set<ChunkPos> getNearbyChunkPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ChunkPos playerPos = player.getChunkPos();
        int playerX = playerPos.x;
        int playerZ = playerPos.z;

        int radius = getLoadedChunkRadius();
        ChunkPos start = new ChunkPos(playerX + radius, playerZ + radius);
        ChunkPos end = new ChunkPos(playerX - radius, playerZ - radius);

        Set<ChunkPos> loadedChunks = Sets.newHashSet();
        for (ChunkPos chunkPos : ChunkPos.stream(start, end).toList()) {
            WorldChunk chunk = this.world.getChunk(chunkPos.x, chunkPos.z);
            if(chunk.isEmpty()) continue;
            loadedChunks.add(chunkPos);
        }

        return loadedChunks;
    }

    public int getChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int configRadius = TidalConfig.chunkRadius;
        int serverRadius = client.options.serverViewDistance;

        return Math.min(configRadius, serverRadius);
    }

    public int getLoadedChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int loadRadius = client.options.serverViewDistance;
        return Math.max(2, loadRadius) + 3;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        getNearbyChunkPos();

        if(!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player);
        }

        waterBodyHandler.tick();
        debugTick(client, player);
    }

    public void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if(DebugHelper.usingSpyglass()) {
            waveTicks++;

            BlockPos playerPos = player.getBlockPos();

            List<BlockPos> scannedBlocks = this.waterBodyHandler.siteCache.values().stream().flatMap(map -> map.keySet().stream()).toList();
            if(scannedBlocks.contains(playerPos)) {
                long chunkPosL = new ChunkPos(playerPos).toLong();
                SitePos site = this.waterBodyHandler.siteCache.get(chunkPosL).get(playerPos);
                if(client.world.getTime() % 20 == 0) {
                    System.out.println(site.getYawAsF3Angle());
                }
            }

//            Set<WaterBody> waterBodies = waterBodyHandler.waterBodies.stream().filter(waterBody -> waterBody.getBlocks().size() >= 10).collect(Collectors.toSet());
//            Set<WaterBody> waterBodies = waterBodyHandler.waterBodies;
//            if(waveTicks >= 20 && !waterBodies.isEmpty() && !waterBodyHandler.shorelines.isEmpty()) {
//            if(waveTicks >= 20 && !waterBodies.isEmpty()) {
//                for (WaterBody waterBody : waterBodies) {
//                    BlockPos spawnPos = waterBody.randomPos().add(0, 2, 0);
//                    BlockPos spawnPos = waterBody.centerPos();
//                    Optional<BlockPos> aPos = waterBody.chunkedBlocks.get(player.getChunkPos().toLong()).getBlocks().stream().filter(pos -> pos.getX() == player.getX() && pos.getZ() == player.getZ()).findAny();
//                    if(aPos.isEmpty()) continue;
//                    BlockPos spawnPos = aPos.get();
//                    Shoreline targetShoreline = waterBodyHandler.getClosestShoreline(spawnPos);
//                    if(targetShoreline == null) return;
//                    Shoreline targetShoreline = waterBodyHandler.shorelines.stream().toList().get(getRandom().nextInt(waterBodyHandler.shorelines.size()));
//                    if(targetShoreline.getBlocks().isEmpty()) return;

//                    long chunkPosL = player.getChunkPos().toLong();
//                    if(!waterBody.chunkedBlocks.containsKey(chunkPosL)) continue;
//                    BlockPos spawnPos = waterBody.chunkedBlocks.get(chunkPosL).getBlocks().get(player.getBlockPos());
//                    if(spawnPos == null) continue;
//
//                    BlockPos targetPos = waterBodyHandler.getSiteForPos(spawnPos).add(0, 2, 0);
////                    BlockPos targetPos = targetShoreline.center();
////                    BlockPos targetPos = player.getBlockPos();
//                    int x = spawnPos.getX() - targetPos.getX();
//                    int y = spawnPos.getY() - targetPos.getY();
//                    int z = spawnPos.getZ() - targetPos.getZ();
//
//                    Color color = randomDebugColor();
//                    ParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
//                    this.world.addParticle(particleEffect, true, targetPos.getX() + 0.5, targetPos.getY() + 3, targetPos.getZ() + 0.5, x, y, z);
//
//                    color = Color.red;
//                    particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
//                    this.world.addParticle(particleEffect, true, spawnPos.getX() + 0.5, spawnPos.getY() + 3, spawnPos.getZ() + 0.5, 0, 0, 0);
//
//                    color = Color.orange;
//                    particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
//                    this.world.addParticle(particleEffect, true, targetPos.getX() + 0.5, targetPos.getY() + 3, targetPos.getZ() + 0.5, 0, 0, 0);
//                }

//                Color color = Color.LIGHT_GRAY;
//                ParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
//                for (BlockPos pos : this.waterBodyHandler.closestShore.values()) {
//                    this.world.addParticle(particleEffect, true, pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, 0, 0, 0);
//                }
//            }
        }
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
