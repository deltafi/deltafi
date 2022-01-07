package org.deltafi.core.domain.delete;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.services.RedisService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;

@SpringBootTest
class TimedDeleteApplicatonTest {

	@Autowired
	DeltaFileRepo deltaFileRepo;

	@TestConfiguration
	public static class Configuration {
		@Bean
		public RedisService redisService() {
			RedisService redisService = Mockito.mock(RedisService.class);
			try {
				// Allows the ActionEventScheduler to not hold up other scheduled tasks (by default, Spring Boot uses a
				// single thread for all scheduled tasks). Throwing an exception here breaks it out of its tight loop.
				Mockito.when(redisService.dgsFeed()).thenThrow(new RuntimeException());
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
			return redisService;
		}
	}

	@Autowired
	RedisService redisService;

	@Test
	void deleteActionAddedToRedisQueueAfterCompletion() throws Exception {
		deltaFileRepo.save(DeltaFile.newBuilder().did("a").stage(DeltaFileStage.COMPLETE)
				.modified(OffsetDateTime.now()).actions(Collections.emptyList()).build());
		Thread.sleep(1000);
		Mockito.verify(redisService, never()).enqueue(any(), any());

		Thread.sleep(2000);
		Mockito.verify(redisService).enqueue(Mockito.eq(Collections.singletonList("DeleteAction")), Mockito.any());
	}
}
