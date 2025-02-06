package net.superkat.tidal.water;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.tidal.Tidal;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugShorelineParticle;
import net.superkat.tidal.particles.debug.DebugWaterBodyParticle;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to handle(find/create, merge, delete) and tick water bodies
 *
 * @see TidalWaveHandler
 */
public class WaterBodyHandler {
    public final TidalWaveHandler tidalWaveHandler;
    public final ClientWorld world;
    //stol... inspired by BiomeColorCache
    //The long is a chunkpos, with a scanner being attributed to it (i have no clue what anything means but ok)
    public Long2ObjectLinkedOpenHashMap<ChunkScanner> scanners = new Long2ObjectLinkedOpenHashMap<>(256, 0.25f);
    //using fastutils because... it has fast in its name? I've been told its fast! And I gotta go fast!
    public Set<WaterBody> waterBodies = new ReferenceOpenHashSet<>();
    public Set<Shoreline> shorelines = new ReferenceOpenHashSet<>();
    //boolean for if the initial joining/chunk reloading build is finished or not
    public boolean built = false;

    public WaterBodyHandler(ClientWorld world, TidalWaveHandler tidalWaveHandler) {
        this.world = world;
        this.tidalWaveHandler = tidalWaveHandler;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!built && tidalWaveHandler.nearbyChunksLoaded) {
            built = build();
        }

        tickScheduledScanners(player);

        debugTick(client, player);
    }

    private void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if(this.world.getTime() % 10 != 0) return;
        boolean farParticles = false;
        debugTrackerParticles(this.waterBodies, true, farParticles, player.getOffHandStack().isOf(Items.CLOCK));
        debugTrackerParticles(this.shorelines, false, farParticles, false);
    }

    private void debugTrackerParticles(Set<? extends AbstractBlockSetTracker> trackers, boolean waterBodyParticle, boolean farParticle, boolean showCenter) {
        int i = 0;
        for (AbstractBlockSetTracker tracker : trackers) {
            Color color = debugColor(i, trackers.size());
            i++;
            tracker.blocks.forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = waterBodyParticle ? new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f)
                        : new DebugShorelineParticle.DebugShorelineParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, farParticle, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            });

            if(showCenter) {
                Vec3d center = tracker.center().toCenterPos();
                this.world.addParticle(ParticleTypes.EXPLOSION, center.getX(), center.getY() + 1, center.getZ(), 0, 0, 0);
            }
        }
    }

    //sick
    private Color debugColor(int i, int size) {
        if(i == 0) { return Color.white; }
        //super ultra cursed debug colors - wait actually I'm a bit of a genuius
        int i1 = i > 3 ? 255 - (255 / (size - 3) * (i - 3)) : 0;
        int red = i == 1 ? 255 : i == 2 || i == 3 ? 0 : (i % 3 == 1 ? i1 : 255);
        int green = i == 2 ? 255 : i == 1 || i == 3 ? 0 : (i % 3 == 2 ? i1 : 255);
        int blue = i == 3 ? 255 : i == 1 || i == 2 ? 0 : (i % 3 == 0 ? i1 : 255);
        return new Color(checkColor(red), checkColor(green), checkColor(blue));
    }

    private int checkColor(int color) {
        if(color > 255) return 255;
        return Math.max(color, 0); //wow intellij really smart
    }

//    public void updateBlock(BlockPos pos, BlockState state) {
//        boolean isAir = world.isAir(pos);
//        boolean isWater = TidalWaveHandler.stateIsWater(state);
//
//        Shoreline shoreline = posInShoreline(pos);
//        //don't check waterbody if shoreline has already been found
//        WaterBody waterBody = shoreline == null ? posInWaterBody(pos) : null;
//
//        //pos is in either a shoreline or waterbody
//        if(shoreline != null || waterBody != null) {
//            if(isAir) {
//                if(shoreline != null) shoreline.removeBlock(pos);
//                else waterBody.removeBlock(pos);
//            } else {
//                if(isWater && shoreline != null) shoreline.removeBlock(pos);
//                else if(!isWater && waterBody != null) waterBody.removeBlock(pos);
//            }
//        } else {
//            //pos is not in a shoreline or waterbody - shouldn't be air
//            if(isAir) return;
//            for (Direction direction : Direction.Type.HORIZONTAL) {
//                BlockPos neighbor = pos.offset(direction);
//                boolean neighborIsWater = TidalWaveHandler.posIsWater(this.world, neighbor);
//                //checking for same type - cursed if statements I'm getting lazy now oh my i'm washed up
//                if(isWater && neighborIsWater) {
//                    WaterBody neighborWater = posInWaterBody(neighbor);
//                    if(neighborWater == null) continue;
//                    neighborWater.addBlock(pos);
//                    return;
//                } else if (!isWater && !neighborIsWater) {
//                    Shoreline neighborShore = posInShoreline(neighbor);
//                    if(neighborShore == null) continue;
//                    neighborShore.addBlock(pos);
//                    return;
//                }
//            }
//        }
//    }

    /**
     * Builds all the chunk scanners available to be built(within config range).
     * <br><br>Meant to be called during world join & chunk reload, where it is expected for high amounts of calculations, and lag, to happen.
     * @return If the build was successful
     */
    public boolean build() {
        long timeStart = Util.getMeasuringTimeMs();
//        BlockPos playerPos = MinecraftClient.getInstance().gameRenderer.getCamera().getBlockPos();
//        int chunkRadius = TidalConfig.chunkRadius;
        for (ChunkScanner scanner : this.scanners.values()) {
            if(scanner == null) continue;
//            if(!scannerInDistance(playerPos, scanner.chunkPos.toLong(), chunkRadius)) continue;
            while (!scanner.isFinished()) {
                scanner.tick();
                if(scanner.isFinished()) {
                    scanners.put(scanner.chunkPos.toLong(), null);
                    MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
                }
            }
        }
        Tidal.LOGGER.info("Chunk scanner time: {} ms", Util.getMeasuringTimeMs() - timeStart);

        return true;
    }

    /**
     * easy method to clear & rebuild
     */
    public void rebuild() {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        BlockPos playerPos = camera.getBlockPos();
        int verticalRadius = TidalConfig.verticalRadius;
        int playerY = playerPos.getY();
        int minY = playerY - verticalRadius; //calc once here
        int maxY = playerY + verticalRadius;
        this.clear(); //clear waterbodies and shorelines

        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.sequencedEntrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            long pos = chunkPos.toLong();
            this.scanners.put(pos, new ChunkScanner(this, this.world, chunkPos, minY, maxY));
        }

        //TODO - remove waterbody/shorelines from unloaded chunks(split? loop through blocks to remove?)
        this.built = build();
    }

    public void tickScheduledScanners(ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int chunkRadius = TidalConfig.chunkRadius; //caching this call might help?
        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.sequencedEntrySet()) {
            if(entry.getValue() == null) continue;
            long chunkPosL = entry.getKey();
            if(!scannerInDistance(playerPos, chunkPosL, chunkRadius)) continue;

            ChunkScanner scanner = entry.getValue();
            for (int i = 0; i < 10; i++) {
                scanner.tick();
            }
            if(scanner.isFinished()) {
                scanners.put(chunkPosL, null);
                MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
//                scanners.remove(chunkPosL);
            }
        }
    }

    public boolean scannerInDistance(BlockPos playerBlockPos, long chunkPosL, int chunkRadius) {
        ChunkPos pos = new ChunkPos(chunkPosL);
        ChunkPos playerPos = new ChunkPos(playerBlockPos);
        int distance = playerPos.getChebyshevDistance(pos);

        return distance < chunkRadius;
    }

    /**
     * Schedules a chunk to be scanned(calculates y coords for you)
     *
     * @param chunk Chunk to schedule
     * @see WaterBodyHandler#scheduleChunkScanner(ChunkPos, int, int)
     */
    public void scheduleChunk(Chunk chunk) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        BlockPos playerPos = camera.getBlockPos();
        int verticalRadius = TidalConfig.verticalRadius;
        int playerY = playerPos.getY();
        int minY = playerY - verticalRadius; //calc once here
        int maxY = playerY + verticalRadius;
        scheduleChunkScanner(chunk.getPos(), minY, maxY);
    }

    /**
     * Schedules a chunk to be scanned
     *
     * @param chunkPos ChunkPos of the chunk to be scanned
     * @param minY The minimum y coordinate of the scan
     * @param maxY The maximum y coordinate of the scan
     *
     * @see WaterBodyHandler#scheduleChunk(Chunk)
     */
    public void scheduleChunkScanner(ChunkPos chunkPos, int minY, int maxY) {
        long pos = chunkPos.toLong();
        this.scanners.computeIfAbsent(pos, pos1 -> new ChunkScanner(this, this.world, chunkPos, minY, maxY));
    }

    public void removeChunk(Chunk chunk) {
        ChunkPos pos = chunk.getPos();
        this.scanners.remove(pos.toLong());
    }

    public Set<WorldChunk> getNearbyChunks(ClientPlayerEntity player, int chunkRadius) {
        ClientWorld world = player.clientWorld;
        BlockPos playerPos = player.getBlockPos();
        Set<WorldChunk> chunks = Sets.newHashSet();
        int radius = chunkRadius * 8;

        ChunkPos start = new ChunkPos(playerPos.add(-radius, 0, -radius));
        ChunkPos end = new ChunkPos(playerPos.add(radius, 0, radius));

        for (ChunkPos pos : ChunkPos.stream(start, end).toList()) {
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
            chunks.add(chunk);
        }

        return chunks;
    }

    public void tickBlockSetTracker(Set<? extends AbstractBlockSetTracker> set) {
        Iterator<? extends AbstractBlockSetTracker> iterator = set.iterator();
        while (iterator.hasNext()) {
            AbstractBlockSetTracker tracker = iterator.next();
            tracker.tick();
            if(tracker.shouldRemove()) {
                //occasionally remove water bodies/shorelines to clear up mistakes in block removing and other jank
                iterator.remove();
            }
        }
    }

    /**
     * Attempt to merge a water body into another(adds the water body's blocks into another)
     *
     * @param waterBody The water body to attempt to merge into already existing water bodies
     * @return If the water body was successfully merged
     */
    public boolean tryMergeWaterBody(WaterBody waterBody) {
        Set<WaterBody> checkedWaters = waterBodiesInAnother(waterBody);
        if(checkedWaters.isEmpty()) return false;

        WaterBody previousWater = waterBody;
        for (WaterBody waterBody1 : checkedWaters) {
            waterBody1.merge(previousWater);
            waterBodies.remove(previousWater);
            previousWater = waterBody1;
        }
        return true;
    }

    public boolean tryMergeShoreline(Shoreline shoreline) {
        Set<Shoreline> checkedShorelines = shorelinesInAnother(shoreline);
        if(checkedShorelines.isEmpty()) return false;

        Shoreline previousShore = shoreline;
        for (Shoreline shoreline1 : checkedShorelines) {
            shoreline1.merge(previousShore);
            shorelines.remove(previousShore);
            previousShore = shoreline1;
        }
        return true;
    }

    /**
     * Check if a water body's set of positions overlaps with other water bodies.
     *
     * @param waterBody The water body whose positions should be checked
     * @return The set of water bodies that share any amount of BlockPos positions with the given water body
     */
    public Set<WaterBody> waterBodiesInAnother(WaterBody waterBody) {
        return waterBodies.stream().filter(checkedWater -> waterBody.blocks.stream().anyMatch(pos -> checkedWater.blocks.contains(pos)))
                .collect(Collectors.toSet());
    }

    public Set<Shoreline> shorelinesInAnother(Shoreline shoreline) {
        return shorelines.stream().filter(checkedShore -> shoreline.blocks.stream().anyMatch(pos -> checkedShore.blocks.contains(pos)))
                .collect(Collectors.toSet());
    }

    @Nullable
    public WaterBody posInWaterBody(BlockPos pos) {
        return waterBodies.stream().filter(waterBody -> waterBody.blocks.contains(pos)).findAny().orElse(null);
    }

    @Nullable
    public Shoreline posInShoreline(BlockPos pos) {
        return shorelines.stream().filter(shoreline -> shoreline.blocks.contains(pos)).findAny().orElse(null);
    }

    /**
     * Remove a BlockPos from all water bodies.
     * <br><br>
     * {@link AbstractBlockSetTracker#removeBlock(BlockPos)} is called to allow its tick value to be increased, making it get deleted sooner to fix any possible changed block jank
     * @param pos The BlockPos to be removed
     */
    public void removePosFromWaterBodies(BlockPos pos) {
        for (WaterBody waterBody : waterBodies) {
            waterBody.removeBlock(pos);
        }
    }

    public void removePosFromShorelines(BlockPos pos) {
        for (Shoreline shoreline : shorelines) {
            shoreline.removeBlock(pos);
        }
    }

    public void clear() {
        clearWaterBodies();
        clearShorelines();
    }

    public void clearWaterBodies() {
        waterBodies.clear();
    }

    public void clearShorelines() {
        shorelines.clear();
    }
}
