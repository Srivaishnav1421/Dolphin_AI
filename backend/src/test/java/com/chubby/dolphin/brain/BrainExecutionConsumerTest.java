package com.chubby.dolphin.brain;

import com.chubby.dolphin.brain.execution.BrainExecutionConsumer;
import com.chubby.dolphin.brain.execution.BrainExecutionService;
import com.chubby.dolphin.brain.execution.ExecutionResult;
import com.chubby.dolphin.brain.execution.ExecutionStatus;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

public class BrainExecutionConsumerTest {

    @Test
    public void testConsumeExecution() {
        BrainExecutionService service = mock(BrainExecutionService.class);
        BrainExecutionConsumer consumer = new BrainExecutionConsumer(service);

        when(service.executeRecommendation("d1")).thenReturn(
                ExecutionResult.builder().status(ExecutionStatus.EXECUTED).build()
        );

        consumer.consumeExecution("d1");

        verify(service, times(1)).executeRecommendation("d1");
    }
}
