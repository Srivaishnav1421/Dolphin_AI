package com.chubby.dolphin.brain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainExperimentEngineTest {

    private final BrainExperimentEngine experimentEngine = new BrainExperimentEngine();

    @Test
    public void testSelectActionWithEmptyCandidates() {
        assertNull(experimentEngine.selectAction("w1", null, null));
    }

    @Test
    public void testSelectActionBanditExploitation() {
        BrainLearningEngine learningEngine = mock(BrainLearningEngine.class);
        when(learningEngine.getLearningStats(eq("w1"), anyString()))
                .thenReturn(new BrainLearningEngine.LearningStats(0.95, 0.05, 20.0, 10.0));

        List<String> candidates = List.of("SCALE_UP", "PAUSE");
        String result = experimentEngine.selectAction("w1", candidates, learningEngine);

        assertNotNull(result);
        assertTrue(candidates.contains(result));
    }
}
