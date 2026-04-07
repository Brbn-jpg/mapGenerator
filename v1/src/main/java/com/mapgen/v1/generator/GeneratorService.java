package com.mapgen.v1.generator;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.mapgen.v1.enums.Biomes;
import com.mapgen.v1.util.FastNoiseLite;

import jakarta.transaction.Transactional;

@Service
public class GeneratorService {
    private static final Logger LOGGER = LogManager.getLogger();
    private final GeneratorRepository generatorRepository;

    public GeneratorService(GeneratorRepository generatorRepository){
        this.generatorRepository = generatorRepository;
    }
    
    @Transactional
    public UUID generate(Integer size, Integer seed){
        LOGGER.info("=== Started Generating ===");
        float[][] heightMap = new float[size][size];
        int[] flatMap = new int[size * size];

        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.05f);
        
        for(int x = 0; x < size; x++){
            for(int y = 0; y < size; y++){
                float rawNoise = noise.GetNoise(x * 0.1f, y * 0.1f);
                float normalisedHeight = (rawNoise + 1) / 2;
                int tileId;
                heightMap[x][y] = normalisedHeight;
                
                String[][] finalMap = new String[size][size];
                if(normalisedHeight < 0.3){
                    finalMap[x][y] = Biomes.OCEAN.toString();
                    tileId = 0;
                } else if(normalisedHeight < 0.5){
                    finalMap[x][y] = Biomes.BEACH.toString();
                    tileId = 1;
                } else if(normalisedHeight < 0.8){
                    finalMap[x][y] = Biomes.GRASS.toString();
                    tileId = 2;
                } else if(normalisedHeight < 0.9){
                    finalMap[x][y] = Biomes.MOUNTAINS.toString();
                    tileId = 3;
                } else {
                    finalMap[x][y] = Biomes.SNOWYPEAKS.toString();
                    tileId = 4;
                }
                flatMap[y * size + x] = tileId;
            }    
        }
        GeneratedMap map = new GeneratedMap();
        map.setSeed(seed);
        map.setSize(size);
        map.setMap(flatMap);
        this.generatorRepository.save(map);
        LOGGER.info("=== Finished Generating ===");

        return map.getId();
    }

    public GeneratedMap getMapById(UUID id){
        return this.generatorRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Map with id" + id + " was not found"));
    }
}
