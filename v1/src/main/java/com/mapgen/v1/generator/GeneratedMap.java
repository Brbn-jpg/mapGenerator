package com.mapgen.v1.generator;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarbinaryJdbcType;

import com.mapgen.v1.util.IntArrayToBinaryConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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

    @Lob //Large Object
    @Convert(converter = IntArrayToBinaryConverter.class)
    @JdbcType(VarbinaryJdbcType.class)
    @Column(name = "Generated_map", columnDefinition = "BYTEA")
    private int[] map;
}
