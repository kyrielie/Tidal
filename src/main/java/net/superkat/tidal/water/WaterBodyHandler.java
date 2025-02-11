package net.superkat.tidal.water;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.superkat.tidal.Tidal;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugShorelineParticle;
import net.superkat.tidal.particles.debug.DebugWaterBodyParticle;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to handle(find/create, merge, delete) and tick water bodies.
 * <br><br>
 * How this goofy thing works: <br>
 * The {@link WaterBodyHandler#scanners} holds a map of {@link ChunkPos} as longs & {@link ChunkScanner}, making each scanner linked to a ChunkPos.<br><br>
 * {@link WaterBodyHandler#scheduleChunkScanner(ChunkPos)} schedules a chunk scanner, <b>called once a chunk is loaded</b>, which will begin ticking in the {@link WaterBodyHandler#tickScheduledScanners(ClientPlayerEntity)} method. Each scanner gets ticked 10 times per client tick, which was done to reduce the amount of lag per chunk load(instead of scanning the entire chunk during chunk load). <br><br>
 * A ChunkScanner scans a single chunk, creating WaterBodies and Shorelines which get added here upon their creation(in their tick method).<br><br>
 * Once the ChunkScanner is finished, its value in the scanners map <b>is set to null</b>, meaning the <b>ChunkPos long key still exists</b>. This was done to allow for all chunk scanners, water bodies, & shorelines, to be reset without missing any chunks.<br><br>
 * The idea behind this was that ChunkPos's are added to the scanners map upon a chunk being loaded. The keys(ChunkPos longs) in the scanners map are used during the {@link WaterBodyHandler#rebuild()} method to setup ChunkScanners for all the chunks. If it was removed, getting all loaded chunks is surprisingly difficult, as there is no accessible method. I decided instead of using mixins/access wideners to get it, I would do this instead.<br><br>
 * Scanning a specific radius of nearby chunks could cause some already loaded chunks(which would have been removed from the scanners map) to be missed, as they would not call the load chunk event. Even though they would be far away initially, moving toward them would be very noticeable. Note: client render distance doesn't always equal server render distance, and it isn't possible to get the server render distance on the client without an AW.<br><br>
 * A ChunkPos(as a long) is removed from the scanners when that chunk is unloaded. Each WaterBody/Shoreline has their own method which removes the blocks associated with that ChunkPos.<br><br>
 * ChunkPos' are updated and rescanned after a configurable amount of block updates within that chunk.
 *
 * @see TidalWaveHandler
 * @see ChunkScanner
 * @see AbstractBlockSetTracker
 */
public class WaterBodyHandler {
    public final TidalWaveHandler tidalWaveHandler;
    public final ClientWorld world;
    //stol... inspired by BiomeColorCache
    //The long is a chunkpos, with a scanner being attributed to it (i have no clue what anything means but ok)
    public Long2ObjectLinkedOpenHashMap<ChunkScanner> scanners = new Long2ObjectLinkedOpenHashMap<>(256, 0.25f);

    //Keep track of how many block updates have happened in a chunk - used to rescan chunks after enough updates
    public Long2IntOpenHashMap chunkUpdates = new Long2IntOpenHashMap(256, 0.25f);

    //using fastutils because... it has fast in its name? I've been told its fast! And I gotta go fast!
    public Set<WaterBody> waterBodies = new ReferenceOpenHashSet<>();
    public Set<Shoreline> shorelines = new ReferenceOpenHashSet<>();

    //boolean for if the initial joining/chunk reloading build is finished or not
    public boolean built = false;

    //Possible solution to wave direction
    //Break shorelines up into chunks, while knowing which ones are connected to maintain size(allow for waves to react to shoreline size)
    //Each chunk contains a yaw number, which is roughly the yaw used to offset the position towards the ocean
    //(figure out how many shoreline blocks have water blocks toward each yaw(0, 90, 180, 270) -> average them all = answer)
    //Some room, maybe ~30 degrees, of random rotation should be allowed

    //TODO - get all nearby loaded chunks to allow for scanners to have chunk scanners removed to save memory
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

    public Shoreline getClosestShoreline(BlockPos pos) {
        int closestDist = 0;
        Shoreline closest = null;
        for (Shoreline shoreline : this.shorelines) {
            if(shoreline.getBlocks().isEmpty()) continue;
            int distance = shoreline.randomPos().getManhattanDistance(pos);
            if(closest == null || distance < closestDist) {
                closest = shoreline;
                closestDist = distance;
            }
        }
        return closest;
    }

    private void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if(this.world.getTime() % 10 != 0) return;
        boolean farParticles = false;

        if(player.getMainHandStack().isOf(Items.COMPASS)) {
            for (WaterBody waterBody : this.waterBodies) {
                for (Map.Entry<Long, AbstractBlockSetTracker.TrackerChunkInfo> entry : waterBody.chunkedBlocks.entrySet()) {
                    Long aLong = entry.getKey();
                    AbstractBlockSetTracker.TrackerChunkInfo trackerChunkInfo = entry.getValue();
                    if(!trackerChunkInfo.shouldMergeYaw) continue;
                    ChunkPos cpos = new ChunkPos(aLong);
                    int x = cpos.x;
                    int z = cpos.z;
                    AbstractBlockSetTracker.TrackerChunkInfo[] neighbours = new AbstractBlockSetTracker.TrackerChunkInfo[]{
                            waterBody.chunkedBlocks.get(new ChunkPos(x + 1, z).toLong()),
                            waterBody.chunkedBlocks.get(new ChunkPos(x - 1, z).toLong()),
                            waterBody.chunkedBlocks.get(new ChunkPos(x, z + 1).toLong()),
                            waterBody.chunkedBlocks.get(new ChunkPos(x, z - 1).toLong())
                    };

                    float totalYaw = trackerChunkInfo.getYaw();
                    int addedYaws = 1;

                    for (AbstractBlockSetTracker.TrackerChunkInfo n : neighbours) {
                        if(n == null) continue;
                        if(!n.shouldMergeYaw) continue;
                        totalYaw += n.getYaw();
                        addedYaws++;
                    }

                    if(addedYaws != 1) totalYaw /= addedYaws;
                    BlockPos pos = new ChunkPos(aLong).getCenterAtY(67);
                    Direction direction = Direction.fromRotation(totalYaw);
                    BlockPos pos2 = pos.offset(direction);

                    this.world.addParticle(ParticleTypes.WAX_OFF, pos.getX(), pos.getY(), pos.getZ(), 0, 0, 0);
                    this.world.addParticle(ParticleTypes.NAUTILUS, pos2.getX(), pos2.getY(), pos2.getZ(), pos.getX() - pos2.getX(), 0, pos.getZ() - pos2.getZ());
                    this.world.addParticle(ParticleTypes.WAX_ON, pos2.getX(), pos2.getY(), pos2.getZ(), 0, 0, 0);

                }
            }
        }

        debugTrackerParticles(this.waterBodies, true, farParticles, player.getOffHandStack().isOf(Items.CLOCK));
        debugTrackerParticles(this.shorelines, false, farParticles, false);
    }

    private void debugTrackerParticles(Set<? extends AbstractBlockSetTracker> trackers, boolean waterBodyParticle, boolean farParticle, boolean showCenter) {
        int i = 0;
        for (AbstractBlockSetTracker tracker : trackers) {
            Color color = debugColor(i, trackers.size());
            i++;
            tracker.getBlocks().forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = waterBodyParticle ? new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f)
                        : new DebugShorelineParticle.DebugShorelineParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, farParticle, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            });

            if(showCenter) {
                Vec3d center = tracker.centerPos().toCenterPos();
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

    public void updateBlock(BlockPos pos, BlockState state) {
        long chunkPosL = new ChunkPos(pos).toLong();
        int currentUpdates = this.chunkUpdates.getOrDefault(chunkPosL, 0) + 1;
        if(currentUpdates >= TidalConfig.chunkUpdatesRescanAmount) {
            if(this.rescheduleChunkScanner(new ChunkPos(chunkPosL))) {
                currentUpdates = 0;
            }
        }
        this.chunkUpdates.put(chunkPosL, currentUpdates);
    }

    /**
     * Builds all the chunk scanners available to be built(within config range).
     * <br><br>Meant to be called during world join & chunk reload, where it is expected for high amounts of calculations, and lag, to happen.
     * @return If the build was successful
     */
    public boolean build() {
        long overallStartTime = Util.getMeasuringTimeMs();
        Tidal.LOGGER.info("-=+=========================+=-");

        for (ChunkScanner scanner : this.scanners.values()) {
            if(scanner == null) continue;
//            long scannerStartTime = Util.getMeasuringTimeMs();
            while (!scanner.isFinished()) {
                scanner.tick();
                if(scanner.isFinished()) {
                    scanners.put(scanner.chunkPos.toLong(), null);
                    MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.05f, 1f);
//                    Tidal.LOGGER.info("Scanner time: {} ms", Util.getMeasuringTimeMs() - scannerStartTime);
                }
            }
        }

        Tidal.LOGGER.info("Total Chunk Builder time: {} ms", Util.getMeasuringTimeMs() - overallStartTime);
        Tidal.LOGGER.info("-=+=========================+=-");

        return true;
    }

    /**
     * easy method to clear & rebuild
     */
    public void rebuild() {
        this.clear(); //clear waterbodies and shorelines

        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.sequencedEntrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            long pos = chunkPos.toLong();
            this.scanners.put(pos, new ChunkScanner(this, this.world, chunkPos));
        }

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
                MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.25f, 1f);
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
     * @see WaterBodyHandler#scheduleChunkScanner(ChunkPos)
     */
    public void scheduleChunk(Chunk chunk) {
        scheduleChunkScanner(chunk.getPos());
    }

    /**
     * Schedules a chunk to be scanned
     *
     * @param chunkPos ChunkPos of the chunk to be scanned
     * @see WaterBodyHandler#scheduleChunk(Chunk)
     */
    public void scheduleChunkScanner(ChunkPos chunkPos) {
        long pos = chunkPos.toLong();
        this.scanners.computeIfAbsent(pos, pos1 -> new ChunkScanner(this, this.world, chunkPos));
    }

    /**
     * Schedules a ChunkPos to be rescanned, removing blocks in the chunk from waterbodies and shorelines in the process.
     *
     * @param chunkPos ChunkPos to remove
     * @return If the reschedule was successful. It will return false if a scanner is already associated with the ChunkPos
     */
    public boolean rescheduleChunkScanner(ChunkPos chunkPos) {
        long pos = chunkPos.toLong();
        if (this.scanners.get(pos) != null) return false;
        this.scanners.put(pos, new ChunkScanner(this, this.world, chunkPos));
        this.removeChunkFromTrackers(pos);
        return true;
    }

    public void removeChunk(Chunk chunk) {
        long chunkPosL = chunk.getPos().toLong();
        this.removeChunkFromTrackers(chunkPosL);
        this.chunkUpdates.remove(chunkPosL);
        this.scanners.remove(chunkPosL);
    }

    public void removeChunkFromTrackers(long chunkPosL) {
        this.waterBodies.forEach(waterBody -> {
            waterBody.removeChunkBlocks(chunkPosL);
        });
        this.shorelines.forEach(shoreline -> {
            shoreline.removeChunkBlocks(chunkPosL);
        });
        this.waterBodies.removeIf(waterBody -> waterBody.chunkedBlocks.isEmpty());
        this.shorelines.removeIf(waterBody -> waterBody.chunkedBlocks.isEmpty());
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
            waterBody1.merge(previousWater, true);
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
        LongSet chunkPosLs = waterBody.chunkedBlocks.keySet();
        if(chunkPosLs.isEmpty()) return Collections.emptySet();
        return waterBodies.stream()
                .filter(checkedWater ->
                    chunkPosLs.longStream().anyMatch(chunkPosL ->
                            checkedWater.chunkedBlocks.keySet().contains(chunkPosL)
                                    && checkedWater.chunkedBlocks.get(chunkPosL).getBlocks().stream()
                                    .anyMatch(pos -> waterBody.chunkedBlocks.get(chunkPosL).getBlocks().contains(pos)))
//                                    && checkedWater.chunkedBlocks.get(chunkPosL).stream()
//                                    .anyMatch(pos -> waterBody.chunkedBlocks.get(chunkPosL).contains(pos)))
                    ).collect(Collectors.toSet());
    }

    public Set<Shoreline> shorelinesInAnother(Shoreline shoreline) {
        LongSet chunkPosLs = shoreline.chunkedBlocks.keySet();
        if(chunkPosLs.isEmpty()) return Collections.emptySet();
        return shorelines.stream()
                .filter(checkedShore ->
                        chunkPosLs.longStream().anyMatch(chunkPosL ->
                                checkedShore.chunkedBlocks.keySet().contains(chunkPosL)
                                        && checkedShore.chunkedBlocks.get(chunkPosL).getBlocks().stream()
                                        .anyMatch(pos -> shoreline.chunkedBlocks.get(chunkPosL).getBlocks().contains(pos)))
                ).collect(Collectors.toSet());
    }

    @Nullable
    public WaterBody posInWaterBody(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        return waterBodies.stream()
                .filter(waterBody -> waterBody.chunkedBlocks.keySet().contains(chunkPosL)
                        && waterBody.chunkedBlocks.get(chunkPosL).getBlocks().contains(pos))
                .findAny()
                .orElse(null);
    }

    @Nullable
    public Shoreline posInShoreline(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        return shorelines.stream()
                .filter(shoreline -> shoreline.chunkedBlocks.keySet().contains(chunkPosL)
                        && shoreline.chunkedBlocks.get(chunkPosL).getBlocks().contains(pos))
                .findAny()
                .orElse(null);
    }

    /**
     * Remove a BlockPos from all water bodies.
     * @param pos The BlockPos to be removed
     */
    public void removePosFromWaterBodies(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        for (WaterBody waterBody : waterBodies) {
            //don't want to call getBlockSet without it containing it since it computes the chunk value if missing
            if(!waterBody.chunkedBlocks.containsKey(chunkPosL)) continue;
            waterBody.getBlockSet(pos).remove(pos);
        }
    }

    public void removePosFromShorelines(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        for (Shoreline shoreline : shorelines) {
            if(!shoreline.chunkedBlocks.containsKey(chunkPosL)) continue;
            shoreline.getBlockSet(pos).remove(pos);
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
