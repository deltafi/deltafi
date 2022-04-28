package org.deltafi.core.domain.services;

import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.ActionState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

@ExtendWith(SpringExtension.class)
@Import({DeltaFilesService.class, DeltaFiProperties.class})
@ImportAutoConfiguration(RefreshAutoConfiguration.class)
@MockBean({StateMachine.class, IngressFlowService.class, EgressFlowService.class, RedisService.class})
@EnableRetry
public class DeltaFileServiceRetryTest {

    @Autowired
    private DeltaFilesService deltaFilesService;

    @MockBean
    private DeltaFileRepo deltaFileRepo;

    @Test
    void testRetry() {
        String did = "abc";
        String fromAction = "validateAction";

        Mockito.when(deltaFileRepo.findById(did)).thenAnswer((Answer<Optional<DeltaFile>>) invocationOnMock -> {
            DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow");
            Action action = Action.newBuilder().name(fromAction).state(ActionState.QUEUED).build();
            deltaFile.setActions(new ArrayList<>(Collections.singletonList(action)));
            return Optional.of(deltaFile);
        });

        Mockito.when(deltaFileRepo.save(Mockito.any())).thenAnswer(new Answer<DeltaFile>() {
            int count = 0;

            @Override
            public DeltaFile answer(InvocationOnMock invocationOnMock) {
                if (count == 0) {
                    count++;
                    throw new OptimisticLockingFailureException("");
                }
                return new DeltaFile();
            }

        });

        Assertions.assertDoesNotThrow(() -> deltaFilesService.handleActionEvent(ActionEventInput.newBuilder().type(ActionEventType.VALIDATE).did(did).action(fromAction).time(OffsetDateTime.now()).build()));
        Mockito.verify(deltaFileRepo, Mockito.times(2)).save(Mockito.any());
    }

}