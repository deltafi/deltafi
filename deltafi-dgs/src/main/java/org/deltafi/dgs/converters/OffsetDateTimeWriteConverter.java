package org.deltafi.dgs.converters;


import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.time.OffsetDateTime;
import java.util.Date;

@WritingConverter
public class OffsetDateTimeWriteConverter implements Converter<OffsetDateTime, Date> {

    @Override
    public Date convert(OffsetDateTime source) {
         return Date.from(source.toInstant());
    }
}
