package net.superkat.tidal.water;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.superkat.tidal.TidalWaveHandler;

import java.awt.*;

/**
 * General utils class for helping show data. For example, colors for a list to use with the debug particles.
 */
public class DebugHelper {

    //aha!
    public static boolean usingSpyglass() {
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

    public static boolean usingShield() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if(player.getActiveItem().isOf(Items.SHIELD) && (player.getItemUseTime() == 1 || player.isSneaking())) {
            player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            return true;
        }
        return false;
    }

    public static boolean holdingCompass() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player.getMainHandStack().isOf(Items.COMPASS);
    }

    //sick
    //This method was redone an embarrassing amount of times to get nice looking colors
    public static Color debugColor(int i, int size) {
        if(i == 0) return Color.white;
        if(i == 1) return Color.RED;
        if(i == 2) return Color.GREEN;
        if(i == 3) return Color.BLUE;

        i -= 3; //buy any get first 4 free

        //super ultra cursed debug colors - wait actually I'm a bit of a genuius
        //confusing, mind confusing confused, don't understand no snese uh - confusing
        int i1 = 255 -  ((((i / 3) + 1) * 30) % 255);
        int i2 = 255 -  ((((i / 3) + 30) * 30) % 255);
        int i3 = 255 -  ((((i / 3) - 90) * 30) % 255);

        int red = (i % 3 == 0 ? i1 : i % 3 == 1 ? i2 : i3);
        int green = (i % 3 == 1 ? i1 : i % 3 == 2 ? i2 : i3);
        int blue = (i % 3 == 2 ? i1 : i % 3 == 0 ? i2 : i3);
        return new Color(checkColor(red), checkColor(green), checkColor(blue));
    }

    public static Color randomDebugColor() {
        Random random = TidalWaveHandler.getRandom();
        int rgbIncrease = random.nextBetween(1, 3);
        int red = rgbIncrease == 1 ? random.nextBetween(150, 255) : 255;
        int green = rgbIncrease == 2 ? random.nextBetween(150, 255) : 255;
        int blue = rgbIncrease == 3 ? random.nextBetween(150, 255) : 255;
        return new Color(red, green, blue);
    }

    private static int checkColor(int color) {
        //confirm rgb int is within 255 because that debugColor method is pretty cursed
        if(color > 255) return 255;
        return Math.max(color, 0); //wow intellij really smart
    }

}
