package com.mapgen.v1.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mapgen.v1.models.GeneratedMap;

public interface GeneratorRepository extends JpaRepository<GeneratedMap, Long>{
    @Query(value = "SELECT * FROM generated_maps ORDER BY id DESC LIMIT 5", nativeQuery = true)
    List<GeneratedMap> getLastFiveMaps();
}
