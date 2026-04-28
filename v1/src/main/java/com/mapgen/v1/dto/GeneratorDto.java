package com.mapgen.v1.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneratorDto {
    @NotNull
    private Integer size;

    @NotNull
    private Integer seed;

    @DecimalMin("-0.5")
    @DecimalMax("0.5")
    private float temp = 0.2f;

    @DecimalMin("-0.5")
    @DecimalMax("0.5")
    private float moisutre = 0.0f;

    @DecimalMin("0.5")
    @DecimalMax("2")
    private float continent = 1.2f;

    @DecimalMin("0.0")
    @DecimalMax("0.5")
    private float city = 0.1f;
}
