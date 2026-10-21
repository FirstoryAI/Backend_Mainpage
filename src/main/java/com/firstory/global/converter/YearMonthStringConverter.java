package com.firstory.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/**
 * YearMonth ↔ "2021-03"
 */
@Converter
public class YearMonthStringConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        return dbData == null ? null : YearMonth.parse(dbData);
    }
}
