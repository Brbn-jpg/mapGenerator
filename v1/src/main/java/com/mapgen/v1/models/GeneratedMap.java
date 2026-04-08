package com.mapgen.v1.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarbinaryJdbcType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mapgen.v1.enums.GeneratingStatus;
import com.mapgen.v1.util.IntArrayToBinaryConverter;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column
    private UUID id;

    @Column(name = "size")
    private Integer size;

    @Column(name = "seed")
    private Integer seed;

    @Lob 
    @Convert(converter = IntArrayToBinaryConverter.class)
    @JdbcType(VarbinaryJdbcType.class)
    @Column(name = "Generated_map", columnDefinition = "BYTEA")
    private int[] map;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private GeneratingStatus status;

    @OneToMany(mappedBy = "generatedMap", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<MapChunk> chunks = new ArrayList<>();
}
