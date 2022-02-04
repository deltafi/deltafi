package org.deltafi.core.domain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
@EnableRetry
public class DeltaFiCoreDomainApplication {
	public static void main(String[] args) {
		SpringApplication.run(DeltaFiCoreDomainApplication.class, args);
	}
}