package net.superkat.tidal;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.WaveParticleEffect;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.water.DebugHelper;
import net.superkat.tidal.water.SitePos;
import net.superkat.tidal.water.WaterHandler;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The main handler, used for spawning/handling tidal waves, as well as ticking the world's {@link WaterHandler}
 */
public class TidalWaveHandler {
    public final ClientWorld world;
    public WaterHandler waterHandler;

    public boolean nearbyChunksLoaded = false;

    public TidalWaveHandler(ClientWorld world) {
        this.world = world;
        this.waterHandler = new WaterHandler(world, this);
    }

    public void reloadNearbyChunks() {
        this.nearbyChunksLoaded = false;
    }

    /**
     * General tick method for all tick-related things EXCEPT the actual waves.
     */
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player);
        }

        this.waterHandler.tick();
        tidalTick();
        debugTick(client, player);
    }

    /**
     * Tick method for waves
     */
    public void tidalTick() {
        //cursed but should sync up
        //TODO - either find consistent way of finding chunks within config distance that would sync up with
        // other people with the same config distance without destroying performance, or throw out synced waves entirely
        if(this.world.getTime() % 40 == 0) {
            spawnWave();
        }

    }

    public void spawnWave() {
        int distFromShore = TidalConfig.waveDistFromShore;
//        int distFromShore = this.world.getTime() % 80 == 0 ? 5 : TidalConfig.waveDistFromShore;
//        if(this.world.getTime() % 40 == 20) {
//            distFromShore = 5;
//        }
        ChunkPos spawnChunkPos = this.getSyncedRandomChunk();

        if(DebugHelper.clockInHotbar() || DebugHelper.offhandClock()) {
            ChunkPos playerChunk = MinecraftClient.getInstance().player.getChunkPos();
            int radius = 3;
            ChunkPos start = new ChunkPos(playerChunk.x + radius, playerChunk.z + radius);
            ChunkPos end = new ChunkPos(playerChunk.x - radius, playerChunk.z - radius);
            Set<BlockPos> waterBlocks = ChunkPos.stream(start, end).map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore)).filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toSet());
//            for (ChunkPos chunkPos : ChunkPos.stream(start, end).toList()) {
//                Set<BlockPos> waterBlocks = this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore);
//                if(waterBlocks == null) continue;
                if(DebugHelper.clockInHotbar()) {
                    debugWaveParticles(waterBlocks);
                } if(DebugHelper.offhandClock()) {

                    Set<BlockPos> spawnableWaterBlocks = this.getSpawnableWaterBlocks(waterBlocks, 3);
                    for (BlockPos water : spawnableWaterBlocks) {
                        Vec3d pos = water.toCenterPos();
                        this.world.addParticle(ParticleTypes.END_ROD, pos.getX(), pos.getY() + 2.5, pos.getZ(), 0, 0, 0);
                    }

//                    List<BlockPos> checkedBlocks = new ArrayList<>();
//                    float i = 0;
//                    for (BlockPos water : waterBlocks) {
//
//                        if(checkedBlocks.contains(water)) continue;
//                        Vec3d pos = water.toCenterPos();
//                        this.world.addParticle(ParticleTypes.END_ROD, pos.getX(), pos.getY() + 2.5, pos.getZ(), 0, 0, 0);
//
//                        List<BlockPos> line = Lists.newArrayList();
//                        List<BlockPos> neighbours = Lists.newArrayList();
//
//                        for (BlockPos checkPos : BlockPos.iterate(water.add(-1, 0, -1), water.add(1, 0,1))) {
//                            if(!waterBlocks.contains(checkPos)) continue;
//                            if(checkedBlocks.contains(checkPos)) continue;
//                            if(checkPos.getX() == water.getX() && checkPos.getZ() == water.getZ()) continue;
//                            Vec3d checkPos1 = checkPos.toCenterPos();
//                            neighbours.add(new BlockPos(checkPos.getX(), checkPos.getY(), checkPos.getZ()));
//                            this.world.addParticle(ParticleTypes.WAX_ON, checkPos1.getX(), checkPos1.getY() + 1.5, checkPos1.getZ(), 0, world.random.nextGaussian(), 0);
//                        }
//
////                        checkedBlocks.addAll(neighbours);
//                        if(neighbours.isEmpty()) continue;
//                        BlockPos neighbour = neighbours.getFirst();
//                        line.add(neighbour);
//                        int offsetX = water.getX() - neighbour.getX();
//                        int offsetZ = water.getZ() - neighbour.getZ();
//
//                        while(true) {
//                            BlockPos checkPos = neighbour.add(offsetX * line.size(), 0, offsetZ * line.size());
//                            if(waterBlocks.contains(checkPos)) {
//                                line.add(checkPos);
//                            } else {
//                                break;
//                            }
//                        }
//
//                        checkedBlocks.addAll(line);
//                        i += 0.1f;
//                        for (BlockPos linePos : line) {
//                            Vec3d linePos1 = linePos.toCenterPos();
//                            this.world.addParticle(ParticleTypes.WAX_OFF, linePos1.getX(), linePos1.getY() + 2, linePos1.getZ(), 0, i, 0);
//                        }
//
//
//
//
//                        int length = 0;
//                        boolean lengthAdded;
//                        for (Direction direction : Direction.Type.HORIZONTAL) {
//                            do {
//                                lengthAdded = waterBlocks.contains(block.offset(direction, length + 1));
//                                if (lengthAdded) length++;
//                            } while (lengthAdded);
//                            if(length != 0) break;
//                        }
//                        this.world.addParticle(ParticleTypes.WAX_ON, water.getX() + 0.5, water.getY() + 2.5, water.getZ() + 0.5, 0, 0, 0);
//                        if(checkedBlocks.contains(water)) continue;
//                        List<BlockPos> line = Lists.newArrayList();
//                        List<BlockPos> dLine = Lists.newArrayList();
//                        for (BlockPos checkPosD : BlockPos.iterate(water.add(-1, 0, -1), water.add(1, 0, 1))) {
//                            if(!waterBlocks.contains(checkPosD)) continue;
//                            if(checkPosD.getX() == water.getX() && checkPosD.getZ() == water.getZ()) continue;
//                            dLine.add(new BlockPos(checkPosD));
//                        }
//
//                        if(dLine.isEmpty()) continue;
//                        for (BlockPos e : dLine) {
//                            this.world.addParticle(ParticleTypes.WAX_OFF, e.getX() + 0.5, e.getY() + 3 + i, e.getZ() + 0.5, 0, 0, 0);
//                        }
//                        BlockPos neighbour = dLine.getFirst();
//                        BlockPos offsetAmount = water.subtract(neighbour);
//                        line.add(neighbour);
//                        boolean hasLineNeighbour = true;
//                        do {
//                            BlockPos checkPos = new BlockPos(neighbour).add(offsetAmount.multiply(line.size()));
//                            hasLineNeighbour = waterBlocks.contains(checkPos);
//                            if(hasLineNeighbour) {
//                                line.add(checkPos);
//                            }
//                        } while(hasLineNeighbour);
//                        checkedBlocks.addAll(line);
//
//                        int centerX = line.stream().mapToInt(BlockPos::getX).sum() / line.size();
//                        int centerZ = line.stream().mapToInt(BlockPos::getZ).sum() / line.size();
//                        Vec3d block = new BlockPos(centerX, water.getY(), centerZ).toCenterPos();
//                        this.world.addParticle(ParticleTypes.END_ROD, block.getX(), block.getY() + 2.5, block.getZ(), 0, line.size() / 32f, 0);
//                    }
                }
//            }
            return;
        }

        if(DebugHelper.spyglassInHotbar()) spawnChunkPos = MinecraftClient.getInstance().player.getChunkPos();
        Set<BlockPos> waterBlocks = this.waterHandler.getWaterCacheAtDistance(spawnChunkPos, distFromShore);
        if(waterBlocks == null) return; //TODO choose random chunk within radius if null

        debugWaveParticles(waterBlocks);
    }

    private Set<BlockPos> getSpawnableWaterBlocks(Set<BlockPos> waterBlocks, int radius) {
        Set<BlockPos> returnBlocks = Sets.newHashSet();
        Set<BlockPos> checkedBlocks = Sets.newHashSet();

        for (BlockPos water : waterBlocks) {
            if(checkedBlocks.contains(water)) continue;
            for (BlockPos checkPos : BlockPos.iterate(water.add(-radius, 0, -radius), water.add(radius, 0, radius))) {
                if(!waterBlocks.contains(checkPos)) continue;
                checkedBlocks.add(new BlockPos(checkPos));
            }
            returnBlocks.add(water);
        }
        return returnBlocks;
    }

    public void debugWaveParticles(Set<BlockPos> waterBlocks) {
        Color color = Color.WHITE; //activates the movement particle's custom colors
//        Color color = Color.LIGHT_GRAY; //deactivates the movement particle's custom colors
        boolean farParticles = false;

        boolean even = this.world.getTime() % 80 == 0;

        Set<BlockPos> spawnableWaterBlocks = this.getSpawnableWaterBlocks(waterBlocks, 3);
        for (BlockPos water : spawnableWaterBlocks) {
//            Vec3d pos = water.toCenterPos();
//            this.world.addParticle(ParticleTypes.END_ROD, pos.getX(), pos.getY() + 2.5, pos.getZ(), 0, 0, 0);
//        }

//        List<BlockPos> checkedBlocks = new ArrayList<>();

//        for (BlockPos water : waterBlocks) {
            SitePos site = this.waterHandler.getSiteForPos(water);
            if(site == null || !site.yawCalculated) continue;
            if(site.xList.size() < 50) continue;

//            if(checkedBlocks.contains(water)) continue;

//            List<BlockPos> line = Lists.newArrayList();
//            List<BlockPos> neighbours = Lists.newArrayList();
//
//            for (BlockPos checkPos : BlockPos.iterate(water.add(-1, 0, -1), water.add(1, 0,1))) {
//                if(!waterBlocks.contains(checkPos)) continue;
//                if(checkedBlocks.contains(checkPos)) continue;
//                if(checkPos.getX() == water.getX() && checkPos.getZ() == water.getZ()) continue;
//                Vec3d checkPos1 = checkPos.toCenterPos();
//                neighbours.add(new BlockPos(checkPos.getX(), checkPos.getY(), checkPos.getZ()));
//                this.world.addParticle(ParticleTypes.WAX_ON, checkPos1.getX(), checkPos1.getY() + 1.5, checkPos1.getZ(), 0, world.random.nextGaussian(), 0);
//            }

//                        checkedBlocks.addAll(neighbours);
//            if(neighbours.isEmpty()) continue;
//            BlockPos neighbour = neighbours.getFirst();
//            line.add(neighbour);
//            int offsetX = water.getX() - neighbour.getX();
//            int offsetZ = water.getZ() - neighbour.getZ();
//
//            while(true) {
//                BlockPos checkPos = neighbour.add(offsetX * line.size(), 0, offsetZ * line.size());
//                if(waterBlocks.contains(checkPos)) {
//                    line.add(checkPos);
//                } else {
//                    break;
//                }
//            }
//
//            checkedBlocks.addAll(line);
//            Vec3d block = new BlockPos(centerX, water.getY(), centerZ).toCenterPos();



//            int evenCheck = (water.getX() + water.getZ()) % 2;
//
//            if(evenCheck == 0 && !even) continue;
//            else if(evenCheck != 0 && even) continue;

//            float scale = site.xList.size() > 300 ? 1.5f : site.xList.size() / 200f;

//            if(water.getX() % 2 == 0 && !even) continue;
//            else if(water.getX() % 2 != 0 && even) continue;

//            if(site.xList.size() <= 50) continue;

//            float scale = (site.xList.size() > 500) ? ((float) 3f) : ((float) site.xList.size() / 100);

//            int length = 0;
//            boolean lengthAdded;
//            for (Direction direction : Direction.Type.HORIZONTAL) {
//                do {
//                    BlockPos checkPos = water.offset(direction, length + 1);
//                    lengthAdded = waterBlocks.contains(checkPos);
//                    if (lengthAdded) {
//                        length++;
//                        checkedBlocks.add(checkPos);
//                        if(length >= 4) break;
//                    }
//                } while (lengthAdded);
////                if(length != 0) break;
//            }

//            if(length < 2) continue;

//            int centerX = waterBlocks.stream().mapToInt(Vec3i::getX).sum() / waterBlocks.size();
//            int centerZ = waterBlocks.stream().mapToInt(Vec3i::getZ).sum() / waterBlocks.size();
//            Optional<BlockPos> center = waterBlocks.stream().filter(pos -> pos.getX() == centerX || pos.getZ() == centerZ).findAny();
//            if(center.isEmpty()) continue;

            WaveParticleEffect particleEffect = new WaveParticleEffect(
                    site.getYaw(),
                    0.15f,
//                    MathHelper.clamp(site.xList.size() / 200f, 1f, 3f),
                    1f,
                    waterBlocks.stream().toList(),
//                    3,
                    200
        //            0.2f,
//                    2f,
//                    1,
//                    40
            );

//            Vec3d pos = new BlockPos(centerX, water.getY(), centerZ).toCenterPos();
            Vec3d pos = water.toCenterPos();
//            Vec3d pos = center.get().toCenterPos();
            float yRandom = (float) (this.world.random.nextFloat() / 4f);
            this.world.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 0.75 + yRandom, pos.getZ(), 0, 0,0);

//            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
//                    Vec3d.unpackRgb(color.getRGB()).toVector3f(),
//                    1f,
//                    site.getYaw(),
//                    0.3f,
//                    20);
//            this.world.addParticle(particleEffect, farParticles, water.getX(), water.getY() + 2, water.getZ(), 0, 0,0);
        }
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
        //alternative way - takes slightly longer
//        return MinecraftClient.getInstance().worldRenderer.isTerrainRenderComplete();
    }

    //Gets all loaded nearby chunks - created using ClientChunkManager & ClientChunkManager.ClientChunkMap
    //Unused right now, but could be helpful for making the WaterBodyHandler's scanners empty out when a scanner is done
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

    /**
     * @return The tidal wave chunk radius - pulls from either the Tidal config or the server's render distance(whichever one is smaller).
     */
    public int getChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int configRadius = TidalConfig.chunkRadius;
        int serverRadius = client.options.serverViewDistance;

        return Math.min(configRadius, serverRadius);
    }

    /**
     * @return The loaded chunk radius - used for figuring out all loaded chunks on the client.
     */
    public int getLoadedChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int loadRadius = client.options.serverViewDistance;
        return Math.max(2, loadRadius) + 3;
    }

    public ChunkPos getSyncedRandomChunk() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        Random syncedRandom = getSyncedRandom();

        ChunkPos playerPos = player.getChunkPos();
        int playerX = playerPos.x;
        int playerZ = playerPos.z;
        int xExtra = syncedRandom.nextBetween(0, 2);
        int zExtra = syncedRandom.nextBetween(0, 2);

        //rounds to closest 3
        int x = playerX - (playerX % 3);
        int z = playerZ - (playerZ % 3);

        return new ChunkPos(x + xExtra, z + zExtra);
    }

    public void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        //show water direction of water blocks
        if(DebugHelper.holdingCompass() || DebugHelper.offhandCompass()) {
            if(!this.waterHandler.built) return;

            ChunkPos playerChunk = player.getChunkPos();
            if(DebugHelper.offhandCompass()) {
                if(client.world.getTime() % 5 != 0) return;
                int radius = 2;
                ChunkPos start = new ChunkPos(playerChunk.x + radius, playerChunk.z + radius);
                ChunkPos end = new ChunkPos(playerChunk.x - radius, playerChunk.z - radius);
                for (ChunkPos chunkPos : ChunkPos.stream(start, end).toList()) {
                    debugChunkDirectionParticles(chunkPos.toLong(), true);
                }
            } else {
                debugChunkDirectionParticles(playerChunk.toLong(), false);
            }

        }

        //print water direction's yaw
        if(DebugHelper.usingSpyglass()) {
            if(client.world.getTime() % 20 != 0) return;

            BlockPos playerPos = player.getBlockPos();

            List<BlockPos> scannedBlocks = this.waterHandler.waterCache.values().stream().flatMap(map -> map.keySet().stream()).toList();
            if(scannedBlocks.contains(playerPos)) {
                long chunkPosL = new ChunkPos(playerPos).toLong();
                SitePos site = this.waterHandler.waterCache.get(chunkPosL).get(playerPos);
                System.out.println(site.getYawAsF3Angle());
            }
        }
    }

    public void debugChunkDirectionParticles(long chunkPosL, boolean farParticles) {
        Color color = Color.WHITE; //activates the movement particle's custom colors
//        Color color = Color.LIGHT_GRAY; //deactivates the movement particle's custom colors

        Object2ObjectOpenHashMap<BlockPos, SitePos> map = this.waterHandler.waterCache.get(chunkPosL);
        if(map == null) return;

        for (Map.Entry<BlockPos, SitePos> entry : map.entrySet()) {
            BlockPos pos = entry.getKey();
            SitePos sitePos = entry.getValue();
            if(sitePos == null || !sitePos.yawCalculated) continue;
            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                    Vec3d.unpackRgb(color.getRGB()).toVector3f(),
                    1f,
                    sitePos.getYaw(),
                    0.3f,
                    20);
            this.world.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 2, pos.getZ(), 0, 0,0);
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
