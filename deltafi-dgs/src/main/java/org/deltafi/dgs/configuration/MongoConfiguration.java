package org.deltafi.dgs.configuration;

import org.deltafi.dgs.converters.OffsetDateTimeReadConverter;
import org.deltafi.dgs.converters.OffsetDateTimeWriteConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;

@Configuration
public class MongoConfiguration {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(new OffsetDateTimeReadConverter(), new OffsetDateTimeWriteConverter()));
    }
}
