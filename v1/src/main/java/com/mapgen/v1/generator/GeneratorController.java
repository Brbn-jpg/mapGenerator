package com.mapgen.v1.generator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mapgen.v1.records.GenerateRecord;

@RestController
@RequestMapping("/generate")
public class GeneratorController {
    private final GeneratorService generatorService;

    public GeneratorController(GeneratorService generatorService){
        this.generatorService = generatorService;
    }

    @PostMapping
    public ResponseEntity<String> generateMap(@RequestBody GenerateRecord record){
        this.generatorService.generate(record.size(), record.seed());
        return ResponseEntity.ok("Started procedure");
    }

    @GetMapping("/{id}")
    public ResponseEntity<GeneratedMap> getMapById(@PathVariable Long id){
        GeneratedMap map = this.generatorService.getMapById(id);
        if(map == null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(map);
    }
}
