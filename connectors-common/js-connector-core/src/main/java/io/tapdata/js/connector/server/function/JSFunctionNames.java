package io.tapdata.js.connector.server.function;

import java.util.Objects;

public enum JSFunctionNames {
    BatchReadFunction("batch_read", "BatchReadFunction", ""),
    CommandV2("command_callback_v2", "CommandCallbackFunction", ""),
    CommandV1("command_callback", "CommandCallbackFunction", ""),
    StreamReadFunction("stream_read", "StreamReadFunction", ""),
    WriteRecordFunction("write_record", "WriteRecordFunction", ""),
    BatchCountFunction("batch_count", "BatchCountFunction", ""),
    CreateTableV2Function("create_table_v2", "CreateTableV2Function", ""),
    TimestampToStreamOffset("timestamp_to_stream_offset", "TimestampToStreamOffset", ""),

    CONNECTION_TEST("connection_test", "ConnectionTest", ""),
    DISCOVER_SCHEMA("discover_schema", "DiscoverSchema", ""),
    TABLE_COUNT("table_count", "TableCount", ""),
    EXPIRE_STATUS("expire_status", "ExpireStatus", ""),
    UPDATE_TOKEN("update_token", "UpdateToken", ""),

    SCANNING_CAPABILITIES_IN_JAVA_SCRIPT("_scanning_capabilities_in_java_script", "scanningCapabilitiesInJavaScript", ""),
    ;
    String jsName;
    String javaName;
    String description;

    JSFunctionNames(String jsName, String javaName, String description) {
        this.javaName = javaName;
        this.jsName = jsName;
        this.description = description;
    }

    public String jsName() {
        return this.jsName;
    }

    public String javaName() {
        return this.javaName;
    }

    public String description() {
        return this.description;
    }

    public static JSFunctionNames isSupport(String functionName) {
        if (Objects.isNull(functionName)) return null;
        JSFunctionNames[] values = values();
        for (JSFunctionNames value : values) {
            if (functionName.equals(value.jsName())) return value;
        }
        return null;
    }

    public static boolean isSupported(String functionName) {
        return Objects.nonNull(JSFunctionNames.isSupport(functionName));
    }
}
