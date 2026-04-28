package com.mapgen.v1.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mapgen.v1.dto.GeneratorDto;
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
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long mapId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        emitters.put(mapId, emitter);
        emitter.onCompletion(() -> emitters.remove(mapId));
        emitter.onTimeout(() -> emitters.remove(mapId));
        return emitter;
    }

    public GeneratorService(GeneratorRepository generatorRepository, ChunkRepository chunkRepository){
        this.generatorRepository = generatorRepository;
        this.chunkRepository = chunkRepository;
    }

    public Long saveMap(GeneratedMap map) {
        return this.generatorRepository.saveAndFlush(map).getId();
    }
    
    @Async
    public void generate(GeneratedMap mapFromController, GeneratorDto dto){
        GeneratedMap map = this.generatorRepository.findById(mapFromController.getId()).orElseThrow();
        
        LOGGER.info("=== Started Generating ===");
        Integer size = map.getSize();
        Integer seed = map.getSeed();

        FastNoiseLite heightmap = new FastNoiseLite(seed);
        heightmap.SetNoiseType(NoiseType.OpenSimplex2);
        heightmap.SetFrequency(0.003f);
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
        continentMask.SetNoiseType(NoiseType.OpenSimplex2);
        continentMask.SetFrequency(0.0004f);

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

                Random chunkRandom = new Random(seed + chunkX * 73856L + chunkY * 1920L);
                int candidateLocalX = chunkRandom.nextInt(CHUNK_SIZE);
                int candidateLocalY = chunkRandom.nextInt(CHUNK_SIZE);

                boolean attemptCityGen = chunkRandom.nextFloat() < dto.getCity();
                boolean cityBuilt = false;

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
                        float moistureBias = dto.getMoisutre();
                        float normalisedMoisture = (rawMoisutreNoise + 1) / 2;
                        normalisedMoisture += moistureBias;
                        normalisedMoisture = Math.max(0.0f, Math.min(1.0f, normalisedMoisture));

                        float rawTemperatureNoise = temperature.GetNoise(warpedX, warpedY);
                        float normalisedTemperature = (rawTemperatureNoise + 1) / 2;
                        float tempBias = dto.getTemp();
                        normalisedTemperature += tempBias;
                        normalisedTemperature = Math.max(0.0f, Math.min(1.0f, normalisedTemperature));

                        float rawMask = continentMask.GetNoise(warpedX, warpedY);
                        float normalisedMask = (rawMask + 1) / 2;
                        float continentBias = dto.getContinent();
                        normalisedMask = (float) Math.pow(normalisedMask, continentBias);

                        float rawRiver = riverNoise.GetNoise(warpedX, warpedY);
                        boolean isRiver = Math.abs(rawRiver) < 0.025f;

                        float finalHeight = (normalisedMask * 0.65f) + (normalisedMask * normalisedHeight * 0.35f);

                        int tileId;

                        // 1. WATER ZONE
                        if (finalHeight < 0.35) {
                            if (normalisedTemperature < 0.2) {
                                tileId = 3; // Frozen Ocean
                            } 
                            else if (finalHeight < 0.15) {
                                tileId = 0; // Abyss
                            } 
                            else {
                                if (normalisedMoisture > 0.6) tileId = 2; // Shallow water
                                else tileId = 1;                          // Standard Ocean
                            }
                        } 
                        // 2. COAST ZONE
                        else if (finalHeight < 0.4) {
                            if (normalisedTemperature < 0.3) tileId = 6;                                  // Rocky beach (Cold)
                            else if (normalisedTemperature > 0.7 && normalisedMoisture > 0.8) tileId = 7; // Mangroves
                            else if (normalisedMoisture < 0.3) tileId = 4;                                // Light sand
                            else tileId = 5;                                                              // Standard beach
                        } 
                        // 3. MAINLAND
                        else if (finalHeight < 0.70) {
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
                                    if (normalisedMoisture < 0.25) {
                                        if (finalHeight > 0.55) tileId = 24; // Badlands
                                        else tileId = 25;                    // Dunes
                                    }
                                    else if (normalisedMoisture < 0.4) tileId = 15; // Desert
                                    else if (normalisedMoisture < 0.6) tileId = 17; // Dry Shrubs
                                    else if (normalisedMoisture < 0.8) tileId = 16;// Savanna
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

                        if(attemptCityGen && x == candidateLocalX && y == candidateLocalY){
                            if(finalHeight > 0.45 && finalHeight < 0.85){
                                boolean foundWater = false;

                                for(int dx = -5; dx <= 5; dx++){
                                    for(int dy = -5; dy <= 5; dy++){
                                        float nGlobalX = globalX + dx;
                                        float nGlobalY = globalY + dy;

                                        float nWarpX = warpNoise.GetNoise(nGlobalX, nGlobalY) * 30f;
                                        float nWarpY = warpNoise.GetNoise(nGlobalX + 1000, nGlobalY + 1000) * 30f;

                                        float rawRiverNeighbour = riverNoise.GetNoise(nGlobalX + nWarpX, nGlobalY + nWarpY);

                                        if(Math.abs(rawRiverNeighbour) < 0.025f){
                                            foundWater = true;
                                            break;
                                        }

                                    }
                                    if(foundWater){
                                        break;
                                    }
                                }
                                boolean isHabitable = normalisedTemperature > 0.35 && normalisedTemperature < 0.8;
                                if(isHabitable && foundWater){
                                    tileId = 23;
                                    cityBuilt = true;
                                }
                            }
                        }

                        int shadowByte = 128;
                        if(finalHeight >= 0.4 && tileId != 23){
                            float neighborNoise = heightmap.GetNoise(warpedX - 1, warpedY - 1);
                            float neighborHeight = (neighborNoise + 1) / 2;
                            float slope = normalisedHeight - neighborHeight;

                            float shadowIntensity = Math.max(-1f, Math.min(1f, slope * 15f));

                            shadowByte = (int) (((shadowIntensity + 1f) / 2f) * 255f);
                        }

                        int packedData = (shadowByte << 8) | tileId;
                        flatChunkMap[y * CHUNK_SIZE + x] = packedData;
                    }
                }
                if (cityBuilt) {
                    for (int cx = -1; cx <= 1; cx++) {
                        for (int cy = -1; cy <= 1; cy++) {
                            int drawX = candidateLocalX + cx;
                            int drawY = candidateLocalY + cy;
                            
                            if (drawX >= 0 && drawX < CHUNK_SIZE && drawY >= 0 && drawY < CHUNK_SIZE) {
                                flatChunkMap[drawY * CHUNK_SIZE + drawX] = 23;
                            }
                        }
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
            SseEmitter emitter = emitters.get(map.getId());
            if (emitter != null) {
                for (MapChunk chunk : chunkBuffer) {
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                        emitters.remove(map.getId());
                        break;
                    }
                }
            }
            chunkBuffer.clear();
        }

        map.setStatus(GeneratingStatus.COMPLETED);
        this.generatorRepository.save(map);
        LOGGER.info("=== Finished Generating Map ID: {} ===", map.getId());

        SseEmitter emitter = emitters.get(map.getId());
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("done").data(map.getId()));
            } catch (IOException ignored) {}
            emitter.complete();
        }
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
