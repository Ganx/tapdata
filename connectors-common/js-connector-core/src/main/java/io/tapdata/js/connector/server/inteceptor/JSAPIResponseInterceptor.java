package io.tapdata.js.connector.server.inteceptor;

import io.tapdata.common.support.APIInvoker;
import io.tapdata.common.support.APIResponseInterceptor;
import io.tapdata.common.support.entitys.APIEntity;
import io.tapdata.common.support.entitys.APIResponse;
import io.tapdata.entity.error.CoreException;
import io.tapdata.js.connector.server.function.support.BaseUpdateTokenFunction;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JSAPIResponseInterceptor implements APIResponseInterceptor {
    private APIInvoker invoker;
    private JSAPIInterceptorConfig config;
    private Map<String,Object> configMap;
    private BaseUpdateTokenFunction updateTokenFunction;

    public static JSAPIResponseInterceptor create(JSAPIInterceptorConfig config, APIInvoker invoker) {
        return new JSAPIResponseInterceptor().config(config).invoker(invoker);
    }

    public JSAPIResponseInterceptor configMap(Map<String,Object> configMap){
        this.configMap = configMap;
        return this;
    }
    public JSAPIResponseInterceptor updateToken(BaseUpdateTokenFunction updateTokenFunction){
        this.updateTokenFunction = updateTokenFunction;
        return this;
    }

    public JSAPIResponseInterceptor invoker(APIInvoker invoker) {
        this.invoker = invoker;
        return this;
    }

    public JSAPIResponseInterceptor config(JSAPIInterceptorConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public APIResponse intercept(APIResponse response, String urlOrName, String method, Map<String, Object> params) {
        if (Objects.isNull(response)) {
            throw new CoreException(String.format("Http request call failed, unable to get the request result: url or name [%s], method [%s].", urlOrName, method));
        }
        if (Objects.isNull(this.updateTokenFunction)){
            return response;
        }
        APIResponse interceptorResponse = response;
        Map<String, Object> exec = this.updateTokenFunction.exec(response);
        if (Objects.nonNull(exec) && !exec.isEmpty()) {
            this.configMap.putAll(exec);
            params.putAll(exec);
            interceptorResponse = invoker.invoke(urlOrName, params, method, true);
        }
        return interceptorResponse;
    }
}