package com.mapgen.v1.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;

@Converter
public class IntArrayToBinaryConverter implements AttributeConverter<int[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(int[] attribute) {
        if (attribute == null) return null;
        ByteBuffer buffer = ByteBuffer.allocate(attribute.length * 4);
        for (int value : attribute) buffer.putInt(value);
        return buffer.array();
    }

    @Override
    public int[] convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) return null;
        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        int[] result = new int[dbData.length / 4];
        for (int i = 0; i < result.length; i++) result[i] = buffer.getInt();
        return result;
    }
}
