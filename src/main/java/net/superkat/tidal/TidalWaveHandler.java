package net.superkat.tidal;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.renderer.WaveRenderer;
import net.superkat.tidal.renderer.WaveSegment;
import net.superkat.tidal.water.SitePos;
import net.superkat.tidal.water.WaterHandler;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The main handler, used for spawning/handling tidal waves, as well as ticking the world's {@link WaterHandler}
 */
public class TidalWaveHandler {
    public final ClientWorld world;
    public WaterHandler waterHandler;
    public WaveRenderer renderer;

//    public ObjectArrayList<Wave> waves = new ObjectArrayList<>();
    public ObjectArrayList<WaveSegment> waves = new ObjectArrayList<>();

    public boolean nearbyChunksLoaded = false;

    public TidalWaveHandler(ClientWorld world) {
        this.world = world;
        this.waterHandler = new WaterHandler(this, world);
        this.renderer = new WaveRenderer(this, world);
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

    public void render(WorldRenderContext context) {
        this.renderer.render(context);
    }

    /**
     * Tick method for waves
     */
    public void tidalTick() {
        if(!this.world.getTickManager().shouldTick()) return;
        //cursed but should sync up
        //TODO - either find consistent way of finding chunks within config distance that would sync up with
        // other people with the same config distance without destroying performance, or throw out synced waves entirely
        if(this.world.getTime() % 40 == 0) {
            spawnAllWaves();
        }




        //TODO - optimize wave merge check here (this hurts my soul)
        //TODO - Create algro to sort a wave's segments in a line for rendering(choose random, check 3x3, for each block found check its 3x3, etc. - then ask Hama or Echo to optimize it ¯\_(ツ)_/¯)

        //tick waves
//        for (ObjectListIterator<Wave> iterator = this.getWaves().iterator(); iterator.hasNext();) {
//            Wave wave = iterator.next();
//            wave.tick();
//
//            if(wave.isDead()) {
//                iterator.remove();
//                continue;
//            }
//
//            double minDist = 4;
//            boolean shortCircuit = false;
//            for (Wave checkWave : this.getWaves()) {
//                for (WaveSegment checkSegment : checkWave.getWaveSegments()) {
//                    for (WaveSegment thisSegment : wave.getWaveSegments()) {
//                        double dist = thisSegment.getBlockPos().getSquaredDistance(checkSegment.getBlockPos());
//                        if(dist <= minDist) {
//                            if(checkWave == wave) continue;
//                            wave.merge(checkWave);
//                            shortCircuit = true;
//                            break;
//                        }
//                    }
//                    if(shortCircuit) break;
//                }
//                if(shortCircuit) break;
//            }
//
////            WaveSegment min = mins.get(wave);
////            WaveSegment max = maxs.get(wave);
////            if(min != null && max != null) {
////                boolean shortCircuit = false;
////                double minDist = 4;
////
////                for (Map.Entry<Wave, WaveSegment> check : mins.entrySet()) {
////                    Wave checkWave = check.getKey();
////                    WaveSegment checkSegment = check.getValue();
////                    double dist = checkSegment.getBlockPos().getSquaredDistance(min.getBlockPos());
////                    if(dist <= minDist) {
////                        if(checkWave == wave) continue;
////                        wave.merge(checkWave);
////                        shortCircuit = true;
////                        break;
////                    }
////                }
////
////                if(shortCircuit) continue;
////
////                for (Map.Entry<Wave, WaveSegment> check : maxs.entrySet()) {
////                    Wave checkWave = check.getKey();
////                    WaveSegment checkSegment = check.getValue();
////                    double dist = checkSegment.getBlockPos().getSquaredDistance(max.getBlockPos());
////                    if(dist <= minDist) {
////                        if(checkWave == wave) continue;
////                        wave.merge(checkWave);
////                        break;
////                    }
////                }
////            }
//
//        }

    }

//    public ObjectArrayList<Wave> getWaves() {
//        return this.waves;
//    }

    public ObjectArrayList<WaveSegment> getWaves() {
        return this.waves;
    }

    public void spawnAllWaves() {
        if(!DebugHelper.clockInHotbar() && !DebugHelper.offhandClock()) return;
        int distFromShore = TidalConfig.waveDistFromShore;
        int chunkRadius = 3;
        int spawnBlockRadius = 1;

        ChunkPos playerChunk = MinecraftClient.getInstance().player.getChunkPos();
        ChunkPos start = new ChunkPos(playerChunk.x + chunkRadius, playerChunk.z + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x - chunkRadius, playerChunk.z - chunkRadius);
//        Set<BlockPos> waterBlocks = ChunkPos.stream(start, end).map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore)).filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toSet());
//        if(waterBlocks.isEmpty()) return;
        for (ChunkPos chunkPos : ChunkPos.stream(start, end).toList()) {
            Set<BlockPos> waterBlocks = this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore);
            if(waterBlocks == null) continue;
    //        Set<BlockPos> spawnableWaterBlocks = this.reduceWaterBlocks(waterBlocks, spawnBlockRadius);
            spawnWave(waterBlocks);

            if(DebugHelper.holdingSpyglass()) debugWaveParticles(waterBlocks);
    //        if(DebugHelper.offhandSpyglass()) debugWaveParticles(spawnableWaterBlocks);
            if(DebugHelper.offhandClock()) {
                for (BlockPos water : waterBlocks) {
                    Vec3d pos = water.toCenterPos();
                    this.world.addParticle(ParticleTypes.END_ROD, pos.getX(), pos.getY() + 2.5, pos.getZ(), 0, 0, 0);
                }
            }

        }
    }

    public void spawnWave(Set<BlockPos> waterBlocks) {
        for (BlockPos water : waterBlocks) {
            SitePos site = this.waterHandler.getSiteForPos(water);
            if(site == null || !site.yawCalculated) continue;
            if(site.xList.size() < 50) continue;

            float yaw = site.getYaw();
            WaveSegment waveSegment = new WaveSegment(water, yaw);
            this.waves.add(waveSegment);
        }
    }

//    public void spawnWave(Set<BlockPos> allWaterBlocks) {
//        Set<BlockPos> waterBlocks = new HashSet<>(allWaterBlocks);
//        while(!waterBlocks.isEmpty()) {
//            List<BlockPos> sortedBlocks = sortBlocks(waterBlocks);
//            Wave wave = new Wave(this, this.world);
//
//            for (BlockPos water : sortedBlocks) {
//                SitePos site = this.waterHandler.getSiteForPos(water);
//                if(site == null || !site.yawCalculated || site.xList.size() < 50) continue;
//
//                float yaw = site.getYaw();
//                wave.add(new WaveSegment(water, yaw));
//            }
//
//            this.waves.add(wave);
//        }
//
////        List<BlockPos> sortedBlocks = sortBlocks(waterBlocks);
////        Wave wave = new Wave(this, this.world);
////
////        for (BlockPos water : sortedBlocks) {
////            SitePos site = this.waterHandler.getSiteForPos(water);
////            if(site == null || !site.yawCalculated || site.xList.size() < 50) continue;
////
////            float yaw = site.getYaw();
////            wave.add(new WaveSegment(water, yaw));
////        }
////
////        this.waves.add(wave);
//
//
////        double maxDist = 10 * 10; //squared because dist calc is squared for now
//
////        List<BlockPos> sortedWaters = waterBlocks.stream().sorted(Comparator.comparingInt(pos -> (pos.getX() + pos.getZ()))).toList();
//
////        int j = 0;
////        for (BlockPos block : waterBlocks) {
////            Color color = DebugHelper.debugTransitionColor(j, waterBlocks.size());
////            j++;
////            ParticleEffect particleEffect = new DebugWaterParticle.DebugWaterParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
////            this.world.addParticle(particleEffect, true, block.getX(), block.getY() + 5, block.getZ(), 0, 0, 0);
////        }
//
////        for (BlockPos water : waterBlocks) {
////            SitePos site = this.waterHandler.getSiteForPos(water);
////            if(site == null || !site.yawCalculated || site.xList.size() < 50) continue;
////
////            float yaw = site.getYaw();
////            Wave wave = new Wave(this, this.world, water, yaw);
////            this.waves.add(wave);
////        }
//
//
////        Wave wave = new Wave(this, this.world);
////
////        for (int i = 0; i < sortedWaters.size() - 1; i++) {
////            BlockPos water = sortedWaters.get(i);
////            BlockPos nextWater = sortedWaters.get(i + 1);
////            double dist = water.getSquaredDistance(nextWater);
////            if(dist <= maxDist) {
////                SitePos site = this.waterHandler.getSiteForPos(water);
////                if(site == null || !site.yawCalculated || site.xList.size() < 50) continue;
////
////                float yaw = site.getYaw();
////                WaveSegment waveSegment = new WaveSegment(water, yaw);
////                wave.add(waveSegment);
////            } else {
////                if(!wave.getWaveSegments().isEmpty()) {
////                    this.waves.add(wave);
////                    wave.sortSegments();
////                }
////                wave = new Wave(this, this.world);
////            }
////        }
////
////        if(!this.waves.contains(wave)) {
////            this.waves.add(wave);
////            wave.sortSegments();
////        }
//
////        boolean farParticles = false;
////        WaveParticleEffect particleEffect = createWave(waterBlocks.stream().toList(), 0.15f, 1f, 200);
////
////        Vec3d pos = chunkPos.getCenterAtY((int) MinecraftClient.getInstance().player.getY()).toCenterPos();
////        float yRandom = (float) (this.world.random.nextFloat() / 4f);
////        this.world.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 0.75 + yRandom, pos.getZ(), 0, 0,0);
//    }
//
//    //TODO make this work with loops
//    public List<BlockPos> sortBlocks(Set<BlockPos> waterBlocks) {
//        List<BlockPos> blocks = waterBlocks.stream().toList();
//
//        //find last block
//        List<BlockPos> visited = Lists.newArrayList();
//
//        Queue<BlockPos> queue = Queues.newArrayDeque();
//        BlockPos start = blocks.getFirst();
//        queue.add(start);
//
//        while(!queue.isEmpty()) {
//            BlockPos pos = queue.poll();
//            visited.add(pos);
//            for (BlockPos neighbour : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
//                if(!blocks.contains(neighbour)) continue;
//                if(visited.contains(neighbour)) continue;
//                queue.add(new BlockPos(neighbour));
//            }
//        }
//
//        visited.forEach(waterBlocks::remove);
//
//        BlockPos end = visited.getLast();
//        this.world.addParticle(ParticleTypes.WITCH, end.getX(), end.getY() + 5, end.getZ(), 0, 0, 0);
//
//        //go from last block to other end
//        List<BlockPos> result = Lists.newArrayList();
//        queue.clear();
//        queue.add(end);
//        while(!queue.isEmpty()) {
//            BlockPos pos = queue.poll();
//            result.add(pos);
//            for (BlockPos neighbour : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
//                if(!blocks.contains(neighbour)) continue;
//                if(result.contains(neighbour)) continue;
//                queue.add(new BlockPos(neighbour));
//            }
//        }
//
//        return result;
//    }
//
//    private WaveParticleEffect createWave(List<BlockPos> waterBlocks, float speed, float scale, int maxAge) {
//        int minSiteAmount = 50;
//        List<Float> yaws = Lists.newArrayList();
//        List<BlockPos> usedWater = waterBlocks.stream().filter(pos -> {
//            SitePos site = this.waterHandler.getSiteForPos(pos);
//            if(site == null || !site.yawCalculated) return false;
//            if(site.xList.size() < minSiteAmount) return false;
//            yaws.add(site.yaw);
//            return true;
//        }).toList();
//        return new WaveParticleEffect(
//                yaws,
//                speed,
//                scale,
//                usedWater,
//                maxAge
//        );
//    }
//
//    private Set<BlockPos> reduceWaterBlocks(Set<BlockPos> waterBlocks, int radius) {
//        Set<BlockPos> returnBlocks = Sets.newHashSet();
//        Set<BlockPos> checkedBlocks = Sets.newHashSet();
//
//        for (BlockPos water : waterBlocks) {
//            if(checkedBlocks.contains(water)) continue;
//            for (BlockPos checkPos : BlockPos.iterate(water.add(-radius, 0, -radius), water.add(radius, 0, radius))) {
//                if(!waterBlocks.contains(checkPos)) continue;
//                checkedBlocks.add(new BlockPos(checkPos));
//            }
//            returnBlocks.add(water);
//        }
//        return returnBlocks;
//    }
//
//    private Set<BlockPos> getSpawnableWaterBlocks(Set<BlockPos> waterBlocks, int minSiteAmount) {
//        return waterBlocks.stream().filter(pos -> {
//            SitePos site = this.waterHandler.getSiteForPos(pos);
//            if(site == null || !site.yawCalculated) return false;
//            return site.xList.size() >= minSiteAmount;
//        }).collect(Collectors.toSet());
//    }

    public void debugWaveParticles(Set<BlockPos> waterBlocks) {
        Color color = Color.WHITE; //activates the movement particle's custom colors
//        Color color = Color.LIGHT_GRAY; //deactivates the movement particle's custom colors
        boolean farParticles = false;

        for (BlockPos water : waterBlocks) {
            SitePos site = this.waterHandler.getSiteForPos(water);
            if(site == null || !site.yawCalculated) continue;
//            if(site.xList.size() < 50) continue;

            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                    Vec3d.unpackRgb(color.getRGB()).toVector3f(),
                    1f,
                    site.getYaw(),
                    0.3f,
                    20);
            this.world.addParticle(particleEffect, farParticles, water.getX(), water.getY() + 2, water.getZ(), 0, 0,0);
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
