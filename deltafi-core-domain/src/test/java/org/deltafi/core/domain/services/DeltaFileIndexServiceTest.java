package org.deltafi.core.domain.services;

import org.deltafi.core.domain.api.repo.DeltaFileRepo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

@SpringBootTest
@TestPropertySource(properties = {"deltafi.deltaFileTtl=3d"})
class DeltaFileIndexServiceTest {

    @Autowired
    public DeltaFileIndexService indexService;

    @MockBean
    private DeltaFileRepo deltaFileRepo;

    @Test
    void setDeltaFileTtl() {
        Mockito.verify(deltaFileRepo).setExpirationIndex(Duration.ofDays(3));
    }
}