package org.deltafi.dgs.services;

import org.deltafi.dgs.Util;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.generated.types.Action;
import org.deltafi.dgs.generated.types.ActionState;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

@ExtendWith(SpringExtension.class)
@Import({DeltaFilesService.class, DeltaFiProperties.class})
@MockBean({StateMachine.class, DeltaFiConfigService.class})
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
            Action action = Action.newBuilder().name(fromAction).state(ActionState.DISPATCHED).build();
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

        Assertions.assertDoesNotThrow(() -> deltaFilesService.completeActionAndAdvance(did, fromAction));
        Mockito.verify(deltaFileRepo, Mockito.times(2)).save(Mockito.any());
    }

}
