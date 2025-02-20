package net.superkat.tidal;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.water.DebugHelper;
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

    public boolean nearbyChunksLoaded = false;
    public int waveTicks = 0;

    public TidalWaveHandler(ClientWorld world) {
        this.world = world;
        this.waterHandler = new WaterHandler(world, this);
    }

    public void reloadNearbyChunks() {
        this.nearbyChunksLoaded = false;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player);
        }

        waterHandler.tick();
        debugTick(client, player);
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
            waveTicks++;

            if(client.world.getTime() % 20 != 0) return;

            BlockPos playerPos = player.getBlockPos();

            List<BlockPos> scannedBlocks = this.waterHandler.siteCache.values().stream().flatMap(map -> map.keySet().stream()).toList();
            if(scannedBlocks.contains(playerPos)) {
                long chunkPosL = new ChunkPos(playerPos).toLong();
                SitePos site = this.waterHandler.siteCache.get(chunkPosL).get(playerPos);
                System.out.println(site.getYawAsF3Angle());
            }
        }
    }

    public void debugChunkDirectionParticles(long chunkPosL, boolean farParticles) {
        Color color = Color.WHITE; //activates the movement particle's custom colors
//        Color color = Color.LIGHT_GRAY; //deactivates the movement particle's custom colors

        Object2ObjectOpenHashMap<BlockPos, SitePos> map = this.waterHandler.siteCache.get(chunkPosL);
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
