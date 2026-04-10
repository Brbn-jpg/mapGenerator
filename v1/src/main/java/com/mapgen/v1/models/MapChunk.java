package com.mapgen.v1.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "map_chunks")
public class MapChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(name = "chunkX")
    private Integer chunkX;

    @Column(name = "chunkY")
    private Integer chunkY;

    @Column(name = "chunk")
    private int[] chunk;

    @ManyToOne
    @JoinColumn(name = "generated_map_id", nullable = false)
    @JsonIgnore
    private GeneratedMap generatedMap;
}
