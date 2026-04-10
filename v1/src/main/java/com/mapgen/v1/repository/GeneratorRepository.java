package com.mapgen.v1.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mapgen.v1.models.GeneratedMap;

public interface GeneratorRepository extends JpaRepository<GeneratedMap, Long>{

}
