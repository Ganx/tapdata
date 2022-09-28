package io.tapdata.flow.engine.V2.node.hazelcast.processor;

import com.tapdata.constant.Log4jUtil;
import com.tapdata.entity.TapdataEvent;
import com.tapdata.entity.task.context.ProcessorBaseContext;
import com.tapdata.tm.commons.task.dto.TaskDto;
import io.tapdata.aspect.ProcessorNodeProcessAspect;
import io.tapdata.aspect.utils.AspectUtils;
import io.tapdata.common.sample.sampler.AverageSampler;
import io.tapdata.common.sample.sampler.CounterSampler;
import io.tapdata.common.sample.sampler.ResetCounterSampler;
import io.tapdata.common.sample.sampler.SpeedSampler;
import io.tapdata.flow.engine.V2.exception.node.NodeException;
import io.tapdata.flow.engine.V2.node.hazelcast.HazelcastBaseNode;
import io.tapdata.metrics.TaskSampleRetriever;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * @author samuel
 * @Description
 * @create 2022-07-12 17:10
 **/
public abstract class HazelcastProcessorBaseNode extends HazelcastBaseNode {
	private Logger logger = LogManager.getLogger(HazelcastProcessorBaseNode.class);

	// statistic and sample related
	protected ResetCounterSampler resetInputCounter;
	protected CounterSampler inputCounter;
	protected ResetCounterSampler resetOutputCounter;
	protected CounterSampler outputCounter;
	protected SpeedSampler inputQPS;
	protected SpeedSampler outputQPS;
	protected AverageSampler timeCostAvg;
	private TapdataEvent pendingEvent;

	public HazelcastProcessorBaseNode(ProcessorBaseContext processorBaseContext) {
		super(processorBaseContext);
	}

	@Override
	protected void initSampleCollector() {
		super.initSampleCollector();

		// TODO: init outputCounter initial value
		Map<String, Number> values = TaskSampleRetriever.getInstance().retrieve(tags, Arrays.asList(
				"inputTotal", "outputTotal"
		));
		// init statistic and sample related initialize
		resetInputCounter = statisticCollector.getResetCounterSampler("inputTotal");
		inputCounter = sampleCollector.getCounterSampler("inputTotal", values.getOrDefault("inputTotal", 0).longValue());
		resetOutputCounter = statisticCollector.getResetCounterSampler("outputTotal");
		outputCounter = sampleCollector.getCounterSampler("outputTotal", values.getOrDefault("outputTotal", 0).longValue());
		inputQPS = sampleCollector.getSpeedSampler("inputQPS");
		outputQPS = sampleCollector.getSpeedSampler("outputQPS");
		timeCostAvg = sampleCollector.getAverageSampler("timeCostAvg");

		super.initSampleCollector();
	}

	@Override
	protected final boolean tryProcess(int ordinal, @NotNull Object item) throws Exception {
		try {
			Log4jUtil.setThreadContext(processorBaseContext.getTaskDto());
			if (null != pendingEvent) {
				if (offer(pendingEvent)) {
					pendingEvent = null;
				} else {
					return false;
				}
			}
			TapdataEvent tapdataEvent = (TapdataEvent) item;
			AtomicReference<TapdataEvent> processedEvent = new AtomicReference<>();
			try {
				AspectUtils.executeProcessorFuncAspect(ProcessorNodeProcessAspect.class, () -> new ProcessorNodeProcessAspect()
						.processorBaseContext(getProcessorBaseContext())
						.inputEvent(tapdataEvent)
						.start(), (processorNodeProcessAspect) -> {
					if (null == tapdataEvent.getTapEvent()) {
						// control tapdata event, skip the process consider process is done
						processedEvent.set(tapdataEvent);
						if (null != processorNodeProcessAspect) {
							AspectUtils.accept(processorNodeProcessAspect.state(ProcessorNodeProcessAspect.STATE_PROCESSING).getConsumers(), tapdataEvent);
						}
						return;
					}
					// Update memory from ddl event info map
					updateMemoryFromDDLInfoMap(tapdataEvent, getTgtTableNameFromTapEvent(tapdataEvent.getTapEvent()));
					if (tapdataEvent.isDML()) {
						transformFromTapValue(tapdataEvent, null);
					}
					tryProcess(tapdataEvent, (event, processResult) -> {
						if (null == event) {
							return;
						}
						if (tapdataEvent.isDML()) {
							if (null != processResult && null != processResult.getTableId()) {
								transformToTapValue(event, processorBaseContext.getTapTableMap(), processResult.getTableId());
							} else {
								transformToTapValue(event, processorBaseContext.getTapTableMap(), getNode().getId());
							}
						}

						// consider process is done
						processedEvent.set(event);
						if (null != processorNodeProcessAspect) {
							AspectUtils.accept(processorNodeProcessAspect.state(ProcessorNodeProcessAspect.STATE_PROCESSING).getConsumers(), event);
						}

					});
				});
			} catch (Throwable throwable) {
				NodeException nodeException = new NodeException("Error occurred when process events in processor", throwable)
						.context(getProcessorBaseContext())
						.event(tapdataEvent.getTapEvent());
				logger.error(nodeException.getMessage(), nodeException);
				obsLogger.error(nodeException);
				throw nodeException;
			}

			if (processedEvent.get() != null) {
				if (!offer(processedEvent.get())) {
					pendingEvent = processedEvent.get();
				}
			}
		} finally {
			ThreadContext.clearAll();
		}
		return true;
	}

	protected ProcessResult getProcessResult(String tableName) {
		if (!multipleTables && !StringUtils.equalsAnyIgnoreCase(processorBaseContext.getTaskDto().getSyncType(),
						TaskDto.SYNC_TYPE_DEDUCE_SCHEMA)) {
			tableName = processorBaseContext.getNode().getId();
		}
		return ProcessResult.create().tableId(tableName);
	}

	protected abstract void tryProcess(TapdataEvent tapdataEvent, BiConsumer<TapdataEvent, ProcessResult> consumer);

	protected static class ProcessResult {
		private String tableId;

		public static ProcessResult create() {
			return new ProcessResult();
		}

		public ProcessResult tableId(String tableId) {
			this.tableId = tableId;
			return this;
		}

		public String getTableId() {
			return tableId;
		}
	}
}
