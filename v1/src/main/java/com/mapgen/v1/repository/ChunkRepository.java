package com.mapgen.v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mapgen.v1.models.GeneratedMap;
import com.mapgen.v1.models.MapChunk;


public interface ChunkRepository extends JpaRepository<MapChunk, UUID>{
    List<MapChunk> findByGeneratedMap(GeneratedMap generatedMap);
    List<MapChunk> findByGeneratedMapAndIdGreaterThan(GeneratedMap map, Long id);
}
