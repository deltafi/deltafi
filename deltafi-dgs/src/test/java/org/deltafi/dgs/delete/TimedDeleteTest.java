package org.deltafi.dgs.delete;

import org.deltafi.dgs.services.DeltaFilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TimedDeleteTest {
    @Mock
    DeltaFilesService deltaFilesService;

    final Map<String, String> parameters = new HashMap<>();
    final String policyName = "policyName";
    final String flow = "the flow";

    @BeforeEach
    void setup() {
        parameters.clear();
    }

    @Test
    void testConstructorAfterCreate() {
        parameters.put("afterCreate", "PT50M");
        parameters.put("flow", flow);
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        assertThat(timedDelete.getName()).isEqualTo(policyName);
        assertThat(timedDelete.getAfterCreate().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterComplete());
        assertThat(timedDelete.getFlow()).isEqualTo(flow);
    }

    @Test
    void testConstructorAfterComplete() {
        parameters.put("afterComplete", "PT50M");
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        assertThat(timedDelete.getName()).isEqualTo(policyName);
        assertThat(timedDelete.getAfterComplete().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterCreate());
        assertNull(timedDelete.getFlow());
    }

    @Test
    void testConstructorThrowsWithNoParams() {
        assertThrows(IllegalArgumentException.class,() -> new TimedDelete(deltaFilesService, policyName, parameters));
    }

    @Test
    void testConstructorThrowsWithTooManyParams() {
        parameters.put("afterCreate", "PT50M");
        parameters.put("afterComplete", "PT50M");
        assertThrows(IllegalArgumentException.class,() -> new TimedDelete(deltaFilesService, policyName, parameters));
    }

    @Test
    void runsAfterCreate() {
        parameters.put("afterCreate", "PT50M");
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        timedDelete.run();

        verify(deltaFilesService).markForDelete(ArgumentMatchers.any(), ArgumentMatchers.isNull(), ArgumentMatchers.isNull(), ArgumentMatchers.eq(policyName));
    }

    @Test
    void runsAfterComplete() {
        parameters.put("afterComplete", "PT50M");
        parameters.put("flow", flow);
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        timedDelete.run();

        verify(deltaFilesService).markForDelete(ArgumentMatchers.isNull(), ArgumentMatchers.any(), ArgumentMatchers.eq(flow), ArgumentMatchers.eq(policyName));
    }
}
