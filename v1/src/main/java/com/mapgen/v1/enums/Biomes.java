package com.mapgen.v1.enums;

public enum Biomes {
    SNOWYPEAKS("Snowy peaks"),
    MOUNTAINS("Mountains"),
    OCEAN("Ocean"),
    BEACH("Beach"),
    GRASS("Grass");

    private final String biome;

    Biomes(String biome){
        this.biome = biome;
    }

    public String toString(){
        return this.biome;
    }
}
