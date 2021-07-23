package org.deltafi.dgs.services;

import org.deltafi.dgs.configuration.EnrichActionConfiguration;
import org.deltafi.dgs.configuration.FormatActionConfiguration;
import org.deltafi.dgs.generated.types.ActionQueryInput;
import org.deltafi.dgs.generated.types.ActionType;
import org.deltafi.dgs.repo.ActionConfigRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ActionConfigServiceTest {

    @InjectMocks
    private ActionConfigService configService;

    @Mock
    private ActionConfigRepo actionConfigRepo;

    @Test
    void getLoadAction() {
        configService.getLoadAction("action");
        Mockito.verify(actionConfigRepo).findLoadAction("action");
    }

    @Test
    void getEnrichAction() {
        configService.getEnrichAction("action");
        Mockito.verify(actionConfigRepo).findEnrichAction("action");
    }

    @Test
    void getFormatAction() {
        configService.getFormatAction("action");
        Mockito.verify(actionConfigRepo).findFormatAction("action");
    }

    @Test
    void getEnrichActions() {
        EnrichActionConfiguration config = new EnrichActionConfiguration();
        config.setName("action");
        Mockito.when(actionConfigRepo.findAllByActionType(ActionType.ENRICH_ACTION)).thenReturn(List.of(config));
        List<String> actions = configService.getEnrichActions();
        assertEquals(1, actions.size());
        assertEquals("action", actions.get(0));
    }

    @Test
    void getFormatActions() {
        FormatActionConfiguration config = new FormatActionConfiguration();
        config.setName("action");
        Mockito.when(actionConfigRepo.findAllByActionType(ActionType.FORMAT_ACTION)).thenReturn(List.of(config));
        List<String> actions = configService.getFormatActions();
        assertEquals(1, actions.size());
        assertEquals("action", actions.get(0));
    }

    @Test
    void getConfigs_all() {
        configService.getActionConfigs(null);
        Mockito.verify(actionConfigRepo).findAll();
    }

    @Test
    void getConfigs_byType() {
        ActionQueryInput input = ActionQueryInput.newBuilder().actionType(ActionType.ENRICH_ACTION).build();
        configService.getActionConfigs(input);
        Mockito.verify(actionConfigRepo).findAllByActionType(ActionType.ENRICH_ACTION);
    }

    @Test
    void getConfigs_byNameAndType() {
        ActionQueryInput input = ActionQueryInput.newBuilder().actionType(ActionType.ENRICH_ACTION).name("action").build();
        configService.getActionConfigs(input);
        Mockito.verify(actionConfigRepo).findByNameAndActionType("action", ActionType.ENRICH_ACTION);
    }

    @Test
    void getActionNamesByType() {
        EnrichActionConfiguration config = new EnrichActionConfiguration();
        config.setName("action");
        Mockito.when(actionConfigRepo.findAllByActionType(ActionType.ENRICH_ACTION)).thenReturn(List.of(config));
        List<String> actionNames = configService.getActionNamesByType(ActionType.ENRICH_ACTION);
        assertEquals(1, actionNames.size());
        assertEquals("action", actionNames.get(0));
    }

    @Test
    void removeConfigs_all() {
        configService.removeActionConfigs(null);
        Mockito.verify(actionConfigRepo).deleteAllWithCount();
    }

    @Test
    void removeConfigs_byType() {
        ActionQueryInput input = ActionQueryInput.newBuilder().actionType(ActionType.ENRICH_ACTION).build();
        configService.removeActionConfigs(input);
        Mockito.verify(actionConfigRepo).deleteAllByActionType(ActionType.ENRICH_ACTION);
    }

    @Test
    void removeConfigs_byNameAndType() {
        ActionQueryInput input = ActionQueryInput.newBuilder().actionType(ActionType.ENRICH_ACTION).name("action").build();
        configService.removeActionConfigs(input);
        Mockito.verify(actionConfigRepo).deleteByNameAndActionType("action", ActionType.ENRICH_ACTION);
    }
}