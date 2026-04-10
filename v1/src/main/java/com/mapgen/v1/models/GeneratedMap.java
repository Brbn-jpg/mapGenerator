package com.mapgen.v1.models;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mapgen.v1.enums.GeneratingStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "generated_maps")
public class GeneratedMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(name = "size")
    private Integer size;

    @Column(name = "seed")
    private Integer seed;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private GeneratingStatus status;

    @OneToMany(mappedBy = "generatedMap", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<MapChunk> chunks = new ArrayList<>();
}
