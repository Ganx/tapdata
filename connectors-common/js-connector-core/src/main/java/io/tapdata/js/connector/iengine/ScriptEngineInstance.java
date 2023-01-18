package io.tapdata.js.connector.iengine;

import io.tapdata.entity.error.CoreException;
import io.tapdata.js.connector.JSConnector;

import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;

public class ScriptEngineInstance {
    public static final String JS_FLOODER = "connectors-javascript";

    public LoadJavaScripter script;

    private static ScriptEngineInstance instance;

    public static ScriptEngineInstance instance() {
        if (Objects.isNull(instance)) {
            synchronized (ScriptEngineInstance.class) {
                if (Objects.isNull(instance)) {
                    instance = new ScriptEngineInstance();
                }
            }
        }
        return instance;//= new ScriptEngineInstance();
    }

    private ScriptEngineInstance() {
        this.scriptInstance();
    }

    private void scriptInstance() {
        this.script = LoadJavaScripter.loader("", JS_FLOODER);
    }

    public LoadJavaScripter script() {
        return this.script;
    }

    public void loadScript() {
        try {
            ClassLoader classLoader = JSConnector.class.getClassLoader();
            Enumeration<URL> resources = classLoader.getResources(JS_FLOODER + "/");
            this.script.load(resources);
        } catch (Exception error) {
            throw new CoreException(error.getMessage());
        }
    }
}
