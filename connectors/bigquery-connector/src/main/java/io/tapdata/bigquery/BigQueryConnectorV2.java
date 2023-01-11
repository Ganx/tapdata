package io.tapdata.bigquery;

import com.google.protobuf.Descriptors;
import io.tapdata.base.ConnectorBase;
import io.tapdata.bigquery.entity.ContextConfig;
import io.tapdata.bigquery.service.bigQuery.BigQueryConnectionTest;
import io.tapdata.bigquery.service.bigQuery.TableCreate;
import io.tapdata.bigquery.service.bigQuery.WriteRecord;
import io.tapdata.bigquery.service.command.Command;
import io.tapdata.bigquery.service.stream.handle.TapEventCollector;
import io.tapdata.bigquery.service.stream.v2.BigQueryStream;
import io.tapdata.bigquery.service.stream.v2.MergeHandel;
import io.tapdata.bigquery.util.bigQueryUtil.FieldChecker;
import io.tapdata.bigquery.util.tool.Checker;
import io.tapdata.entity.codec.TapCodecsRegistry;
import io.tapdata.entity.event.ddl.table.TapClearTableEvent;
import io.tapdata.entity.event.ddl.table.TapCreateTableEvent;
import io.tapdata.entity.event.ddl.table.TapDropTableEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.schema.value.*;
import io.tapdata.entity.utils.cache.Entry;
import io.tapdata.entity.utils.cache.Iterator;
import io.tapdata.entity.utils.cache.KVMap;
import io.tapdata.pdk.apis.annotations.TapConnectorClass;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.CommandResult;
import io.tapdata.pdk.apis.entity.ConnectionOptions;
import io.tapdata.pdk.apis.entity.TestItem;
import io.tapdata.pdk.apis.entity.WriteListResult;
import io.tapdata.pdk.apis.entity.message.CommandInfo;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.connector.target.CreateTableOptions;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@TapConnectorClass("spec-v2.json")
public class BigQueryConnectorV2 extends ConnectorBase {
    private static final String TAG = BigQueryConnector.class.getSimpleName();
    private static final int STREAM_SIZE = 30000;
    private static final int CUMULATIVE_TIME_INTERVAL = 5;

    private WriteRecord writeRecord;
    private TableCreate tableCreate;
    private TapEventCollector tapEventCollector;
    private BigQueryStream stream;
    private MergeHandel merge;
    private final AtomicBoolean running = new AtomicBoolean(Boolean.TRUE);
    private String tableId;

    @Override
    public void onStart(TapConnectionContext connectionContext) throws Throwable {
        this.writeRecord = (WriteRecord) WriteRecord.create(connectionContext).autoStart();
        this.tableCreate = (TableCreate) TableCreate.create(connectionContext).paperStart(this.writeRecord);
        if (connectionContext instanceof TapConnectorContext) {
            TapConnectorContext context = (TapConnectorContext) connectionContext;
            isConnectorStarted(connectionContext, connectorContext -> {
                Iterator<Entry<TapTable>> iterator = connectorContext.getTableMap().iterator();
                while (iterator.hasNext()) {
                    Entry<TapTable> next = iterator.next();
                    TapTable value = next.getValue();
                    if (Checker.isNotEmpty(value)) {
                        FieldChecker.verifyFieldName(value.getNameFieldMap());
                    }
                }
            });
            ContextConfig config = this.writeRecord.config();
            this.stream = (BigQueryStream) BigQueryStream.streamWrite(context).paperStart(this.writeRecord);
            this.merge = ((MergeHandel) MergeHandel.merge(connectionContext).paperStart(writeRecord))
                    .running(this.running)
                    .mergeDelaySeconds(config.mergeDelay());
            this.stream.merge(this.merge);

        }
    }

    @Override
    public void onStop(TapConnectionContext connectionContext) throws Throwable {
        synchronized (this) {
            this.notify();
        }
        Optional.ofNullable(this.writeRecord).ifPresent(WriteRecord::onDestroy);
        Optional.ofNullable(this.tapEventCollector).ifPresent(TapEventCollector::stop);
        this.running.set(false);
        Optional.ofNullable(this.merge).ifPresent(MergeHandel::stop);
        Optional.ofNullable(this.stream).ifPresent(BigQueryStream::closeStream);
    }

    @Override
    public void registerCapabilities(ConnectorFunctions connectorFunctions, TapCodecsRegistry codecRegistry) {
        //codecRegistry.registerFromTapValue(TapYearValue.class, "DATE", TapValue::getValue);
        codecRegistry.registerFromTapValue(TapYearValue.class, "INT64", TapValue::getValue);
        codecRegistry.registerFromTapValue(TapMapValue.class, "JSON", tapValue -> toJson(tapValue.getValue()));
        codecRegistry.registerFromTapValue(TapArrayValue.class, "JSON", tapValue -> toJson(tapValue.getValue()));
        codecRegistry.registerFromTapValue(TapDateTimeValue.class, tapDateTimeValue -> formatTapDateTime(tapDateTimeValue.getValue(), "yyyy-MM-dd HH:mm:ss.SSSSSS"));
        codecRegistry.registerFromTapValue(TapDateValue.class, tapDateValue -> formatTapDateTime(tapDateValue.getValue(), "yyyy-MM-dd"));
        codecRegistry.registerFromTapValue(TapTimeValue.class, tapTimeValue -> tapTimeValue.getValue().toTimeStr());
        codecRegistry.registerFromTapValue(TapYearValue.class, tapYearValue -> formatTapDateTime(tapYearValue.getValue(), "yyyy"));

        connectorFunctions.supportWriteRecord(this::writeRecord)
                .supportCommandCallbackFunction(this::command)
                .supportCreateTableV2(this::createTableV2)
                .supportClearTable(this::clearTable)
                .supportDropTable(this::dropTable)
                .supportReleaseExternalFunction(this::release)
        ;
    }

    private void release(TapConnectorContext context) {
        KVMap<Object> stateMap = context.getStateMap();
        Object temporaryTable = stateMap.get(ContextConfig.TEMP_CURSOR_SCHEMA_NAME);
        if (Objects.nonNull(temporaryTable)) {
            this.merge = (MergeHandel) MergeHandel.merge(context).autoStart();
            //删除临时表
            try {
                this.merge.dropTemporaryTable(String.valueOf(temporaryTable));
                this.merge.config().tempCursorSchema(null);
                stateMap.put(ContextConfig.TEMP_CURSOR_SCHEMA_NAME, null);
            } catch (Exception e) {
                TapLogger.warn(TAG, " Temporary table cannot be drop temporarily. Details: " + e.getMessage());
            }
        }
    }

    private void dropTable(TapConnectorContext context, TapDropTableEvent dropTableEvent) {
        this.tableCreate.dropTable(dropTableEvent);
    }

    private void clearTable(TapConnectorContext connectorContext, TapClearTableEvent clearTableEvent) {
        try {
            this.tableCreate.cleanTable(clearTableEvent);
        } catch (Exception e) {
            TapLogger.warn(TAG, " Table data cannot be cleared temporarily. Details: " + e.getMessage());
        }
        if (Objects.nonNull(this.merge)) {
            try {
                this.merge.cleanTemporaryTable();
            } catch (Exception e) {
                TapLogger.warn(TAG, " Temporary table data cannot be cleared temporarily. Details: " + e.getMessage());
            }
        }
    }

    private CreateTableOptions createTableV2(TapConnectorContext connectorContext, TapCreateTableEvent createTableEvent) {
        CreateTableOptions createTableOptions = CreateTableOptions.create().tableExists(tableCreate.isExist(createTableEvent));
        if (!createTableOptions.getTableExists()) {
            this.tableCreate.createSchema(createTableEvent);
        }
        return createTableOptions;
    }

    private CommandResult command(TapConnectionContext context, CommandInfo commandInfo) {
        return Command.command(context, commandInfo);
    }

    private void createTable(TapConnectorContext connectorContext, TapCreateTableEvent tapCreateTableEvent) {
        if (!this.tableCreate.isExist(tapCreateTableEvent)) {
            this.tableCreate.createSchema(tapCreateTableEvent);
        }
    }

    private void writeRecord(TapConnectorContext context, List<TapRecordEvent> events, TapTable table, Consumer<WriteListResult<TapRecordEvent>> consumer) throws Descriptors.DescriptorValidationException, IOException, InterruptedException {
        this.tableId = table.getId();
        this.stream.tapTable(table);
        this.writeRecordStream(context, events, table, consumer);
    }

    private void uploadEvents(Consumer<WriteListResult<TapRecordEvent>> consumer, List<TapRecordEvent> events, TapTable table) {
        try {
            consumer.accept(this.stream.writeRecord(events, table));
        } catch (Exception e) {
            TapLogger.warn(TAG, e.getMessage());
        }
    }

    private void writeRecordStream(TapConnectorContext context, List<TapRecordEvent> events, TapTable table, Consumer<WriteListResult<TapRecordEvent>> consumer) {
        if (Objects.isNull(this.tapEventCollector)) {
            synchronized (this) {
                if (Objects.isNull(this.tapEventCollector)) {
                    this.tapEventCollector = TapEventCollector.create()
                            .maxRecords(BigQueryConnectorV2.STREAM_SIZE)
                            .idleSeconds(BigQueryConnectorV2.CUMULATIVE_TIME_INTERVAL)
                            .table(table)
                            .writeListResultConsumer(consumer)
                            .eventCollected(this::uploadEvents);
                    this.tapEventCollector.start();
                }
            }
        }
        this.tapEventCollector.addTapEvents(events, table);
    }

    /**
     * @deprecated Because QPS is too low .
     */
    private void writeRecordDML(TapConnectorContext connectorContext, List<TapRecordEvent> tapRecordEvents, TapTable tapTable, Consumer<WriteListResult<TapRecordEvent>> writeListResultConsumer) {
        if (Objects.isNull(this.writeRecord)) {
            this.writeRecord = WriteRecord.create(connectorContext);
        }
        this.writeRecord.writeBatch(tapRecordEvents, tapTable, writeListResultConsumer);
    }


    @Override
    public void discoverSchema(TapConnectionContext connectionContext, List<String> tables, int tableSize, Consumer<List<TapTable>> consumer) throws Throwable {
        this.tableCreate.discoverSchema(tables, tableSize, consumer);
    }


    @Override
    public ConnectionOptions connectionTest(TapConnectionContext connectionContext, Consumer<TestItem> consumer) throws Throwable {
        ConnectionOptions connectionOptions = ConnectionOptions.create();
        BigQueryConnectionTest bigQueryConnectionTest = (BigQueryConnectionTest) BigQueryConnectionTest.create(connectionContext).autoStart();
        TestItem testItem = bigQueryConnectionTest.testServiceAccount();
        consumer.accept(testItem);
        if (TestItem.RESULT_FAILED == testItem.getResult()) {
            return connectionOptions;
        }
        TestItem tableSetItem = bigQueryConnectionTest.testTableSet();
        consumer.accept(tableSetItem);
        return connectionOptions;
    }

    @Override
    public int tableCount(TapConnectionContext connectionContext) throws Throwable {
        return ((TableCreate) TableCreate.create(connectionContext).autoStart()).schemaCount();
    }
}
