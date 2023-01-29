package io.tapdata.js.connector.server.function.support;

import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.event.dml.TapDeleteRecordEvent;
import io.tapdata.entity.event.dml.TapInsertRecordEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.event.dml.TapUpdateRecordEvent;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.js.connector.base.EventTag;
import io.tapdata.js.connector.base.EventType;
import io.tapdata.js.connector.iengine.LoadJavaScripter;
import io.tapdata.js.connector.server.function.FunctionBase;
import io.tapdata.js.connector.server.function.FunctionSupport;
import io.tapdata.js.connector.server.function.JSFunctionNames;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.WriteListResult;
import io.tapdata.pdk.apis.functions.connector.target.WriteRecordFunction;
import io.tapdata.write.WriteValve;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;


public class JSWriteRecordFunction extends FunctionBase implements FunctionSupport<WriteRecordFunction> {
    AtomicBoolean isAlive = new AtomicBoolean(true);

    public JSWriteRecordFunction isAlive(AtomicBoolean isAlive) {
        this.isAlive = isAlive;
        return this;
    }

    JSWriteRecordFunction() {
        super();
        super.functionName = JSFunctionNames.WriteRecordFunction;
    }

    @Override
    public WriteRecordFunction function(LoadJavaScripter javaScripter) {
        if (super.hasNotSupport(javaScripter)) return null;
        return this::write;
    }

    private ConcurrentHashMap<String, ScriptEngine> writeEnginePool = new ConcurrentHashMap<>(16);

    private void write(TapConnectorContext context, List<TapRecordEvent> tapRecordEvents, TapTable table, Consumer<WriteListResult<TapRecordEvent>> writeListResultConsumer) throws ScriptException {
        if (Objects.isNull(context)) {
            throw new CoreException("TapConnectorContext cannot not be empty.");
        }
        if (Objects.isNull(table)) {
            throw new CoreException("Table lists cannot not be empty.");
        }
        String threadName = Thread.currentThread().getName();
        ScriptEngine scriptEngine;
        if (writeEnginePool.containsKey(threadName)) {
            scriptEngine = writeEnginePool.get(threadName);
        } else {
            scriptEngine = javaScripter.scriptEngine();
            writeEnginePool.put(threadName, scriptEngine);
        }

        AtomicLong insert = new AtomicLong(0);
        AtomicLong update = new AtomicLong(0);
        AtomicLong delete = new AtomicLong(0);

        List<Map<String, Object>> machiningEvents = machiningEvents(tapRecordEvents, table.getId(), insert, update, delete);

        WriteListResult<TapRecordEvent> result = new WriteListResult<>();

        try {
            super.javaScripter.invoker(
                    JSFunctionNames.WriteRecordFunction.jsName(),
                    context.getConfigContext(),
                    context.getNodeConfig(),
                    machiningEvents
            );
        } catch (Exception e) {
            throw new CoreException(String.format("Exceptions occurred when executing writeRecord to write data. The operations of adding %s, modifying %s, and deleting %s failed.", insert.get(), update.get(), delete.get()));
        }
        writeListResultConsumer.accept(result.insertedCount(insert.get()).modifiedCount(update.get()).removedCount(delete.get()));
    }


    private List<Map<String, Object>> machiningEvents(List<TapRecordEvent> tapRecordEvents, final String tableId, AtomicLong insert, AtomicLong update, AtomicLong delete) {
        List<Map<String, Object>> events = new ArrayList<>();
        if (Objects.isNull(tapRecordEvents)) return events;
        tapRecordEvents.stream().filter(Objects::nonNull).forEach(tapRecord -> {
            Map<String, Object> event = new HashMap<>();
            if (tapRecord instanceof TapInsertRecordEvent) {
                event.put(EventTag.EVENT_TYPE, EventType.insert);
                event.put(EventTag.AFTER_DATA, ((TapInsertRecordEvent) tapRecord).getAfter());
                insert.incrementAndGet();
            } else if (tapRecord instanceof TapUpdateRecordEvent) {
                event.put(EventTag.EVENT_TYPE, EventType.update);
                event.put(EventTag.BEFORE_DATA, ((TapUpdateRecordEvent) tapRecord).getBefore());
                event.put(EventTag.AFTER_DATA, ((TapUpdateRecordEvent) tapRecord).getAfter());
                update.incrementAndGet();
            } else if (tapRecord instanceof TapDeleteRecordEvent) {
                event.put(EventTag.EVENT_TYPE, EventType.delete);
                event.put(EventTag.BEFORE_DATA, ((TapDeleteRecordEvent) tapRecord).getBefore());
                delete.incrementAndGet();
            }
            event.put(EventTag.REFERENCE_TIME, tapRecord.getReferenceTime());
            event.put(EventTag.TABLE_NAME, tableId);
            events.add(event);
        });
        return events;
    }

    public static JSWriteRecordFunction create(AtomicBoolean isAlive) {
        return new JSWriteRecordFunction().isAlive(isAlive);
    }
    public WriteRecordFunction write(LoadJavaScripter loadJavaScripter){
        return this.function(loadJavaScripter);
    }
}
