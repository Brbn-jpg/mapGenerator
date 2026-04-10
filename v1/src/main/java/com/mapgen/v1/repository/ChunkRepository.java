package com.mapgen.v1.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mapgen.v1.models.GeneratedMap;
import com.mapgen.v1.models.MapChunk;


public interface ChunkRepository extends JpaRepository<MapChunk, Long>{
    List<MapChunk> findByGeneratedMap(GeneratedMap generatedMap);
    List<MapChunk> findByGeneratedMapAndIdGreaterThan(GeneratedMap map, Long id);
}
