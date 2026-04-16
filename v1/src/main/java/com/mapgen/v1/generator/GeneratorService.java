package com.mapgen.v1.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.mapgen.v1.enums.GeneratingStatus;
import com.mapgen.v1.models.GeneratedMap;
import com.mapgen.v1.models.MapChunk;
import com.mapgen.v1.repository.ChunkRepository;
import com.mapgen.v1.repository.GeneratorRepository;
import com.mapgen.v1.util.FastNoiseLite;
import com.mapgen.v1.util.FastNoiseLite.FractalType;
import com.mapgen.v1.util.FastNoiseLite.NoiseType;

@Service
public class GeneratorService {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int CHUNK_SIZE = 32;
    private final GeneratorRepository generatorRepository;
    private final ChunkRepository chunkRepository;

    public GeneratorService(GeneratorRepository generatorRepository, ChunkRepository chunkRepository){
        this.generatorRepository = generatorRepository;
        this.chunkRepository = chunkRepository;
    }

    public Long saveMap(GeneratedMap map) {
        return this.generatorRepository.saveAndFlush(map).getId();
    }
    
    @Async
    public void generate(GeneratedMap mapFromController){
        GeneratedMap map = this.generatorRepository.findById(mapFromController.getId()).orElseThrow();
        
        LOGGER.info("=== Started Generating ===");
        Integer size = map.getSize();
        Integer seed = map.getSeed();

        FastNoiseLite heightmap = new FastNoiseLite(seed);
        heightmap.SetNoiseType(NoiseType.ValueCubic);
        heightmap.SetFrequency(0.005f);
        heightmap.SetFractalType(FractalType.FBm);
        heightmap.SetFractalWeightedStrength(0.5f);
        heightmap.SetFractalOctaves(5);

        FastNoiseLite moisutre = new FastNoiseLite(seed+1000);
        moisutre.SetNoiseType(NoiseType.ValueCubic);
        moisutre.SetFrequency(0.003f);
        moisutre.SetFractalType(FractalType.FBm);
        moisutre.SetFractalOctaves(5);

        FastNoiseLite temperature = new FastNoiseLite(seed / 2 + 1);
        temperature.SetNoiseType(NoiseType.OpenSimplex2);
        temperature.SetFrequency(0.0001f);

        FastNoiseLite continentMask = new FastNoiseLite(seed-1000);
        continentMask.SetNoiseType(NoiseType.ValueCubic);
        continentMask.SetFrequency(0.001f);

        FastNoiseLite warpNoise = new FastNoiseLite(seed+2000);
        warpNoise.SetNoiseType(NoiseType.ValueCubic);
        warpNoise.SetFrequency(0.001f);
        warpNoise.SetFractalType(FractalType.FBm);
        warpNoise.SetFractalOctaves(3);

        FastNoiseLite riverNoise = new FastNoiseLite(seed+100);
        riverNoise.SetNoiseType(NoiseType.OpenSimplex2);
        riverNoise.SetFrequency(0.003f);
        riverNoise.SetFractalType(FractalType.FBm);
        riverNoise.SetFractalOctaves(3);

        List<MapChunk> chunkBuffer = new ArrayList<>();
        
        for(int chunkX = 0; chunkX < size; chunkX += CHUNK_SIZE){
            for (int chunkY = 0; chunkY < size; chunkY += CHUNK_SIZE){
                int[] flatChunkMap = new int[CHUNK_SIZE * CHUNK_SIZE];
                for (int x = 0; x < CHUNK_SIZE; x++){
                    for (int y = 0; y < CHUNK_SIZE; y++){
                        int globalX = chunkX + x;
                        int globalY = chunkY + y;

                        float warpX = warpNoise.GetNoise(globalX, globalY) * 30f;
                        float warpY = warpNoise.GetNoise(globalX + 1000, globalY + 1000) * 30f;

                        float warpedX = globalX + warpX;
                        float warpedY = globalY + warpY;

                        if (globalX >= size || globalY >= size) {
                            continue;
                        }

                        float rawHeightmapNoise = heightmap.GetNoise(warpedX, warpedY);
                        float normalisedHeight = (rawHeightmapNoise + 1) / 2;

                        float rawMoisutreNoise = moisutre.GetNoise(warpedX, warpedY);
                        float normalisedMoisture = (rawMoisutreNoise + 1) / 2;

                        float rawTemperatureNoise = temperature.GetNoise(warpedX, warpedY);
                        float normalisedTemperature = (rawTemperatureNoise + 1) / 2;

                        float rawMask = continentMask.GetNoise(warpedX, warpedY);
                        float normalisedMask = (rawMask + 1) / 2;
                        normalisedMask = (float) Math.pow(normalisedMask, 2.0);

                        float rawRiver = riverNoise.GetNoise(warpedX, warpedY);
                        boolean isRiver = Math.abs(rawRiver) < 0.025f;

                        float finalHeight = (normalisedMask * 0.8f) + (normalisedHeight * 0.2f);

                        int tileId;

                        // 1. WATER ZONE
                        if (finalHeight < 0.15) {
                            tileId = 0; // Abyss
                        } else if (finalHeight < 0.40) {
                            if (normalisedTemperature < 0.2) tileId = 3;   // Frozen Ocean
                            else if (normalisedMoisture > 0.6) tileId = 2; // Shallow water
                            else tileId = 1;                               // Standard Ocean
                        } 
                        // 2. COAST ZONE
                        else if (finalHeight < 0.45) {
                            if (normalisedTemperature < 0.3) tileId = 6;                                  // Rocky beach (Cold)
                            else if (normalisedTemperature > 0.7 && normalisedMoisture > 0.8) tileId = 7; // Mangroves
                            else if (normalisedMoisture < 0.3) tileId = 4;                                // Light sand
                            else tileId = 5;                                                              // Standard beach
                        } 
                        // 3. MAINLAND
                        else if (finalHeight < 0.85) {
                            if(isRiver){
                                tileId = 2;
                            } else {
                                if (normalisedTemperature < 0.35) {
                                    // --- COLD CLIMATE ---
                                    if (normalisedMoisture < 0.3) tileId = 8;      // Snow desert
                                    else if (normalisedMoisture < 0.6) tileId = 9; // Tundra
                                    else tileId = 10;                              // Taiga
                                    
                                } else if (normalisedTemperature < 0.65) {
                                    // --- TEMPERATE CLIMATE ---
                                    if (normalisedMoisture < 0.3) tileId = 11;      // Steppe
                                    else if (normalisedMoisture < 0.6) tileId = 12; // Plains
                                    else if (normalisedMoisture < 0.85) tileId = 13;// Mixed Forest
                                    else tileId = 14;                               // Swamps
                                    
                                } else {
                                    // --- HOT CLIMATE ---
                                    if (normalisedMoisture < 0.25) tileId = 15;     // Desert
                                    else if (normalisedMoisture < 0.5) tileId = 17; // Dry Shrubs
                                    else if (normalisedMoisture < 0.75) tileId = 16;// Savanna
                                    else tileId = 18;                               // Jungle
                                }
                            }
                            
                        } 
                        // 4. MOUNTAINS AND PEAKS
                        else {
                            if (normalisedTemperature < 0.4) tileId = 22;      // Eternal Snow
                            else if (normalisedTemperature < 0.7) tileId = 21; // Alpine Tundra
                            else if (normalisedMoisture < 0.4) tileId = 19;    // Canyons / Dry Rocks
                            else tileId = 20;                                  // Bare Rocks
                        }
                        flatChunkMap[y * CHUNK_SIZE + x] = tileId;
                    }
                }
                MapChunk mapChunk = new MapChunk();
                mapChunk.setGeneratedMap(map);
                mapChunk.setChunkX(chunkX);
                mapChunk.setChunkY(chunkY);
                mapChunk.setChunk(flatChunkMap);
                chunkBuffer.add(mapChunk);

            }
            this.chunkRepository.saveAll(chunkBuffer);
            chunkBuffer.clear();
        }

        map.setStatus(GeneratingStatus.COMPLETED);
        this.generatorRepository.save(map);
        LOGGER.info("=== Finished Generating Map ID: {} ===", map.getId());
    }

    public GeneratedMap getMapById(Long id){
        return this.generatorRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Map with id" + id + " was not found"));
    }

    public List<MapChunk> getChunksByMapId(Long id, Long afterId){
        GeneratedMap map = getMapById(id);
        if (afterId != null) {
            return this.chunkRepository.findByGeneratedMapAndIdGreaterThan(map, afterId);
        }
        return this.chunkRepository.findByGeneratedMap(map);
    }
    
}
