package io.tapdata.deduction;

import com.tapdata.entity.TapdataEvent;
import com.tapdata.entity.task.context.ProcessorBaseContext;
import com.tapdata.tm.commons.dag.Element;
import com.tapdata.tm.commons.dag.Node;
import com.tapdata.tm.commons.task.dto.TaskDto;
import io.tapdata.aspect.ProcessorFunctionAspect;
import io.tapdata.aspect.ProcessorNodeProcessAspect;
import io.tapdata.aspect.TaskStartAspect;
import io.tapdata.aspect.task.AbstractAspectTask;
import io.tapdata.aspect.task.AspectTaskSession;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.flow.engine.V2.util.TapEventUtil;
import io.tapdata.schema.SampleMockUtil;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@AspectTaskSession(
        includeTypes = {TaskDto.SYNC_TYPE_DEDUCE_SCHEMA, TaskDto.SYNC_TYPE_TEST_RUN},
        order = Integer.MIN_VALUE)
public class MockAspectTask extends AbstractAspectTask {

  private Set<String> nodeIds;

  public MockAspectTask() {
    observerHandlers.register(ProcessorNodeProcessAspect.class, this::processorNodeProcessAspect);
  }

  private Void processorNodeProcessAspect(ProcessorNodeProcessAspect processAspect) {
    ProcessorBaseContext processorBaseContext = processAspect.getProcessorBaseContext();
    String nodeId = processorBaseContext.getNode().getId();
    if (nodeIds.contains(nodeId)) {
      switch (processAspect.getState()) {
        case ProcessorNodeProcessAspect.STATE_START:
          if (nodeIds.contains(nodeId)) {
            TapdataEvent inputEvent = processAspect.getInputEvent();
            TapEvent tapEvent = inputEvent.getTapEvent();
            if (!(tapEvent instanceof TapRecordEvent)) {
              return null;
            }
            SampleMockUtil.mock(processorBaseContext.getTapTableMap().get(TapEventUtil.getTableId(tapEvent)),
                    TapEventUtil.getAfter(tapEvent));
          }
          break;
        case ProcessorFunctionAspect.STATE_END:
          break;
      }
    }
    return null;
  }

  @Override
  public void onStart(TaskStartAspect startAspect) {
    Optional<Node> optional = task.getDag().getNodes().stream().filter(n -> n.getType().equals("virtualTarget")).findFirst();
    optional.ifPresent(node -> this.nodeIds = task.getDag().predecessors(node.getId()).stream()
            .map(Element::getId).collect(Collectors.toSet()));
  }


}
