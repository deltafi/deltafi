package org.deltafi.dgs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableConfigurationProperties
@EnableRetry
public class DeltaFiDgsApplication {
	public static void main(String[] args) {
		SpringApplication.run(DeltaFiDgsApplication.class, args);
	}
}
