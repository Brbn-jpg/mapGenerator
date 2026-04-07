package com.mapgen.v1.generator;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratorRepository extends JpaRepository<GeneratedMap, UUID>{

}
