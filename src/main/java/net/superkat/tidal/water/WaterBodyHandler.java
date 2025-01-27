package net.superkat.tidal.water;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to handle(find/create, merge, delete) and tick water bodies
 *
 * @see TidalWaveHandler
 */
public class WaterBodyHandler {
    public final ClientWorld world;
    public final TidalWaveHandler tidalWaveHandler;
    public Set<WaterBody> waterBodies = Sets.newHashSet();

    //temp junk particles to see water bodies
    private static List<ParticleEffect> particles = Lists.newArrayList(
            ParticleTypes.BUBBLE_POP,
            ParticleTypes.FLAME,
            ParticleTypes.SOUL_FIRE_FLAME,
            ParticleTypes.SOUL,
            ParticleTypes.CHERRY_LEAVES,
            ParticleTypes.CRIT,
            ParticleTypes.ENCHANTED_HIT,
            ParticleTypes.SMOKE,
            ParticleTypes.WHITE_SMOKE,
            ParticleTypes.ASH,
            ParticleTypes.SPLASH,
            ParticleTypes.RAIN,
            ParticleTypes.WHITE_ASH,
            ParticleTypes.EGG_CRACK,
            ParticleTypes.WAX_OFF,
            ParticleTypes.WAX_ON,
            ParticleTypes.ELECTRIC_SPARK
    );

    public WaterBodyHandler(ClientWorld world, TidalWaveHandler tidalWaveHandler) {
        this.world = world;
        this.tidalWaveHandler = tidalWaveHandler;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        //debug tick test
        if(player.getActiveItem().isOf(Items.SPYGLASS) && player.getItemUseTime() >= 10) {
            if(player.getItemUseTime() == 10) {
                player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            }
            if(player.isSneaking()) {
                Entity entity = client.getCameraEntity();
                BlockHitResult blockHitResult = (BlockHitResult) entity.raycast(20, 0f, true);
                BlockPos raycastPos = blockHitResult.getBlockPos();
                tickPos(raycastPos);
            } else {
                for (int i = 0; i < 10; i++) {
                    tickRandomPos(player);
                }
            }
        }

//        if(player.getWorld().getTime() % 40 == 0) {
//            tick(player);
//        }

        //debug stuff
        if(world.getTime() % 10 != 0) return;
        int i = 0;
        for (WaterBody water : waterBodies) {
            i++;
            if (i >= particles.size()) {
                i = 0;
            }
            ParticleEffect particle = particles.get(i);
            water.waterBlocks.forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                this.world.addParticle(particle, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            });
        }
    }

    public void tickWaterBodies() {
        Iterator<WaterBody> iterator = this.waterBodies.iterator();

        while (iterator.hasNext()) {
            WaterBody waterBody = iterator.next();
            waterBody.tick();

            if(waterBody.shouldRemove()) {
                //occasionally remove water bodies to clear up mistakes in block removing and other jank
                iterator.remove();
            }
        }
    }

    public void tickRandomPos(ClientPlayerEntity player) {
        tickWaterBodies();

        BlockPos playerPos = player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int horizontalRadius = TidalConfig.horizontalWaveDistance;
        int verticalRadius = TidalConfig.verticalWaveDistance;
//        int horizontalRadius = 128;
//        int verticalRadius = 32;
        Random random = TidalWaveHandler.getRandom();

        int x = playerPos.getX() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        int y = playerPos.getY() + random.nextInt(verticalRadius) - random.nextInt(verticalRadius);
        int z = playerPos.getZ() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        pos.set(x, y, z);

        tickPos(pos);
    }


    public void tickPos(BlockPos pos) {
        //FIXME - if a water body is trying to be merged inbetween two other water bodies while the center block is not water
        //both water bodies will be merged despite not being connected

        //3x1x3 blocks
        Map<BlockPos, Boolean> neighbourBlocks = getBlockNeighbour(world, pos, 1, 0, 0);
        Set<BlockPos> nonWater = neighbourBlocks.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (BlockPos nonWaterPos : nonWater) {
            removePosFromWaterBodies(nonWaterPos);
            neighbourBlocks.remove(nonWaterPos);
        }

        //neighbourBlocks are now all water blocks
        if(neighbourBlocks.isEmpty()) return;
        boolean initPosIsWater = neighbourBlocks.containsKey(pos);
        BlockPos waterBodyInitPos = initPosIsWater ? pos : neighbourBlocks.keySet().stream().toList().getFirst();
        WaterBody waterBody = new WaterBody(waterBodyInitPos);
        waterBody.addNeighbours(world, neighbourBlocks.keySet());

        if(tryMergeWaterBody(waterBody)) return;

        this.waterBodies.add(waterBody);
    }

    public static Map<BlockPos, Boolean> getBlockNeighbour(ClientWorld world, BlockPos pos, int horizontalDistance, int y1, int y2) {
        Map<BlockPos, Boolean> cachedBlocks = Maps.newHashMap();

        for (BlockPos blockPos : BlockPos.iterate(pos.add(-horizontalDistance, y1, -horizontalDistance), pos.add(horizontalDistance, y2, horizontalDistance))) {
            cachedBlocks.computeIfAbsent(blockPos.mutableCopy(), pos1 -> posIsWater(world, pos1));
        }
        return cachedBlocks;
    }

    public boolean tryMergeWaterBody(WaterBody waterBody) {
        Set<WaterBody> checkedWaters = waterBodiesInAnother(waterBody);
        if(checkedWaters.isEmpty()) return false;

        WaterBody previousWater = waterBody;
        for (WaterBody waterBody1 : checkedWaters) {
            waterBody1.mergeWaterBody(previousWater);
            waterBodies.remove(previousWater);
            previousWater = waterBody1;
        }
        return true;
    }

    public Set<WaterBody> waterBodiesInAnother(WaterBody waterBody) {
        return waterBodies.stream().filter(checkedWater -> waterBody.waterBlocks.stream().anyMatch(pos -> checkedWater.waterBlocks.contains(pos)))
                .collect(Collectors.toSet());
    }

    public void removePosFromWaterBodies(BlockPos pos) {
        for (WaterBody waterBody : waterBodies) {
            waterBody.removeBlock(pos);
        }
    }

    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.WATER);
    }

    public boolean posInWaterBody(BlockPos pos) {
        return waterBodies.stream().anyMatch(waterBody -> waterBody.waterBlocks.contains(pos));
    }

    public void clearWaterBodies() {
        waterBodies.clear();
    }
}
