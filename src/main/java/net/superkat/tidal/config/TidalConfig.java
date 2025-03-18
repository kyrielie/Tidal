package net.superkat.tidal.config;

public class TidalConfig {
    //A proper config screen, likely using YetAnotherConfigLib(YACL), will come soon
    //For now though, this exists to make it easy to implement configurable stuff whilst making the code

    public static boolean modEnabled = true;
    public static boolean debug = true;
    public static int chunkRadius = 8; //will be defaulted to 5 later
    public static int chunkUpdatesRescanAmount = 50;

    public static int waveTicks = 80; //dummy value
    public static int waveDistFromShore = 8;

}
