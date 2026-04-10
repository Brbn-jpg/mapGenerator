package com.mapgen.v1.generator;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mapgen.v1.enums.GeneratingStatus;
import com.mapgen.v1.models.GeneratedMap;
import com.mapgen.v1.models.MapChunk;
import com.mapgen.v1.records.GenerateRecord;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/generate")
public class GeneratorController {
    private final GeneratorService generatorService;

    public GeneratorController(GeneratorService generatorService){
        this.generatorService = generatorService;
    }

    @PostMapping
    public ResponseEntity<Long> generateMap(@RequestBody GenerateRecord record){
        GeneratedMap map = new GeneratedMap();
        map.setSeed(record.seed());
        map.setSize(record.size());
        map.setStatus(GeneratingStatus.IN_PROGRESS);
        
        Long mapId = this.generatorService.saveMap(map);
        
        this.generatorService.generate(map);
        
        return ResponseEntity.ok(mapId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GeneratedMap> getMapById(@PathVariable Long id){
        GeneratedMap map = this.generatorService.getMapById(id);
        if(map == null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(map);
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<List<MapChunk>> getChunksByMapId(
        @PathVariable Long id, 
        @RequestParam(required = false) Long afterId) {
        return ResponseEntity.ok(this.generatorService.getChunksByMapId(id, afterId));
    }
    
}
