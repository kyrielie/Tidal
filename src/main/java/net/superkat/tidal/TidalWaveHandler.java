package net.superkat.tidal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.water.WaterBody;
import org.apache.commons.compress.utils.Sets;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TidalWaveHandler {

    public final ClientWorld world;
    public Set<WaterBody> waterBodies = Sets.newHashSet();

    public TidalWaveHandler(ClientWorld world) {
        this.world = world;
    }

    //TODO - good luck syncing this client-side only lol
    public static Random getRandom() {
        return Random.create();
    }

    public void tickWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(player.getActiveItem().isOf(Items.SPYGLASS) && player.getItemUseTime() >= 10) {
            for (int i = 0; i < 10; i++) {
                tick(player);
            }
        }

        //temp junk particles to see water bodies
        List<ParticleEffect> particles = Lists.newArrayList(
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

    public void tick(ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int horizontalRadius = TidalConfig.horizontalWaveDistance;
        int verticalRadius = TidalConfig.verticalWaveDistance;
//        int horizontalRadius = 128;
//        int verticalRadius = 32;
        Random random = getRandom();

        int x = playerPos.getX() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        int y = playerPos.getY() + random.nextInt(verticalRadius) - random.nextInt(verticalRadius);
        int z = playerPos.getZ() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        pos.set(x, y, z);

        tickPos(pos);
    }

    //I hope in time I'm able to look back at this and think, "wow, that's really inefficient",
    //not because I want this to be inefficient, but because I hope I'll have improved and be more knowledgeable
    public void tickPos(BlockPos pos) {
        //3x3x3 blocks
        Map<BlockPos, Boolean> neighbourBlocks = getBlockNeighbour(world, pos, 1, -1, 1);
        Set<BlockPos> nonWater = neighbourBlocks.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (BlockPos nonWaterBlock : nonWater) {
            removeBlockFromWaterBodies(nonWaterBlock);
            neighbourBlocks.remove(nonWaterBlock);
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

    @Nullable
    public WaterBody waterBodyInAnother(WaterBody waterBody) {
        //intellij magic oh my gosh
        return waterBodies.stream().filter(checkedWater -> waterBody.waterBlocks.stream().anyMatch(pos -> checkedWater.waterBlocks.contains(pos)))
                .findFirst()
                .orElse(null);
    }

    public void removeBlockFromWaterBodies(BlockPos pos) {
        waterBodies.forEach(waterBody -> waterBody.waterBlocks.remove(pos));
    }

    public boolean posInWaterBody(BlockPos pos) {
        return waterBodies.stream().anyMatch(waterBody -> waterBody.waterBlocks.contains(pos));
    }

    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.WATER);
    }

    public void clearWaterBodies() {
        waterBodies.clear();
    }

    public static boolean airAbove(World world, BlockPos pos) {
        BlockState aboveState = world.getBlockState(pos.offset(Direction.UP, 1));
        return aboveState.isAir();
    }

    public static boolean waterBelow(World world, BlockPos pos) {
        BlockState beneathState = world.getBlockState(pos.offset(Direction.DOWN, 1));
        return beneathState.isOf(Blocks.WATER);
    }
}
