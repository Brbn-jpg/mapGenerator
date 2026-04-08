package com.mapgen.v1.generator;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

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

    public UUID saveMap(GeneratedMap map) {
        return this.generatorRepository.saveAndFlush(map).getId();
    }
    
    @Async
    public void generate(GeneratedMap mapFromController){
        GeneratedMap map = this.generatorRepository.findById(mapFromController.getId()).orElseThrow();
        
        LOGGER.info("=== Started Generating ===");
        Integer size = map.getSize();
        Integer seed = map.getSeed();
        
        int[] flatMap = new int[size * size];

        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.05f);
        
        for(int chunkX = 0; chunkX < size; chunkX += CHUNK_SIZE){
            for (int chunkY = 0; chunkY < size; chunkY += CHUNK_SIZE){
                int[] flatChunkMap = new int[CHUNK_SIZE * CHUNK_SIZE];
                for (int x = 0; x < CHUNK_SIZE; x++){
                    for (int y = 0; y < CHUNK_SIZE; y++){
                        int globalX = chunkX + x;
                        int globalY = chunkY + y;

                        if (globalX >= size || globalY >= size) {
                            continue;
                        }

                        float rawNoise = noise.GetNoise(globalX * 0.1f, globalY * 0.1f);
                        float normalisedHeight = (rawNoise + 1) / 2;
                        int tileId;

                        if(normalisedHeight < 0.3){
                            tileId = 0;
                        } else if(normalisedHeight < 0.5){
                            tileId = 1;
                        } else if(normalisedHeight < 0.8){
                            tileId = 2;
                        } else if(normalisedHeight < 0.9){
                            tileId = 3;
                        } else {
                            tileId = 4;
                        }

                        flatMap[globalY * size + globalX] = tileId;
                        flatChunkMap[y * CHUNK_SIZE + x] = tileId;
                    }
                }
                MapChunk mapChunk = new MapChunk();
                mapChunk.setGeneratedMap(map);
                mapChunk.setChunkX(chunkX);
                mapChunk.setChunkY(chunkY);
                mapChunk.setChunk(flatChunkMap);
                this.chunkRepository.saveAndFlush(mapChunk);
            }
        }

        map.setMap(flatMap);
        map.setStatus(GeneratingStatus.COMPLETED);
        this.generatorRepository.saveAndFlush(map);
        LOGGER.info("=== Finished Generating Map ID: {} ===", map.getId());
    }

    public GeneratedMap getMapById(UUID id){
        return this.generatorRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Map with id" + id + " was not found"));
    }

    public List<MapChunk> getChunksByMapId(UUID id, Long afterId){
        GeneratedMap map = getMapById(id);
        if (afterId != null) {
            return this.chunkRepository.findByGeneratedMapAndIdGreaterThan(map, afterId);
        }
        return this.chunkRepository.findByGeneratedMap(map);
    }
    
}
