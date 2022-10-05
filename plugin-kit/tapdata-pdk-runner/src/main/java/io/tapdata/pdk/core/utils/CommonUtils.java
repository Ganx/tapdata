package io.tapdata.pdk.core.utils;

import io.tapdata.entity.error.TapAPIErrorCodes;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.error.CoreException;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.functions.ConnectionFunctions;
import io.tapdata.pdk.apis.functions.PDKMethod;
import io.tapdata.pdk.apis.functions.connection.ErrorHandleFunction;
import io.tapdata.pdk.apis.functions.connection.RetryOptions;
import io.tapdata.pdk.apis.spec.TapNodeSpecification;
import io.tapdata.pdk.core.api.ConnectionNode;
import io.tapdata.pdk.core.api.ConnectorNode;
import io.tapdata.pdk.core.api.Node;
import io.tapdata.pdk.core.entity.params.PDKMethodInvoker;
import io.tapdata.pdk.core.error.PDKRunnerErrorCodes;
import io.tapdata.pdk.core.error.QuiteException;
import io.tapdata.pdk.core.executor.ExecutorsManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import io.tapdata.pdk.core.monitor.PDKInvocationMonitor;
import org.apache.commons.io.output.AppendableOutputStream;

import javax.naming.CommunicationException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.CRC32;

public class CommonUtils {
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    public static String dateString() {
        return dateString(new Date());
    }
    public static String dateString(Date date) {
        return sdf.format(date);
    }

    public static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if(version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if(dot != -1) { version = version.substring(0, dot); }
        } return Integer.parseInt(version);
    }
    public boolean pdkEquals(Node pdkNode, TapEvent event) {
        return pdkEquals(pdkNode, event, true);
    }
    public boolean pdkEquals(Node pdkNode, TapEvent event, boolean ignoreVersion) {
        if(pdkNode != null &&
                pdkNode.getTapNodeInfo() != null &&
                pdkNode.getTapNodeInfo().getTapNodeSpecification() != null &&
                event != null &&
                event.getPdkId() != null &&
                event.getPdkGroup() != null &&
                event.getPdkVersion() != null
        ) {
            TapNodeSpecification specification = pdkNode.getTapNodeInfo().getTapNodeSpecification();
            if(specification.getId() != null && specification.getGroup() != null && specification.getVersion() != null) {
                return specification.getId().equals(event.getPdkId()) && specification.getGroup().equals(event.getPdkGroup()) &&
                        (ignoreVersion || specification.getVersion().equals(event.getPdkVersion()));
            }
        }
        return false;
    }

    public interface AnyError {
        void run() throws Throwable;
    }



    public static void awakenRetryObj(Object syncWaitObj) {
        if(syncWaitObj != null) {
            synchronized (syncWaitObj) {
                syncWaitObj.notifyAll();
            }
        }
    }

    public static void autoRetry(Node node,PDKMethod method,PDKMethodInvoker invoker) {
        CommonUtils.AnyError runable = invoker.getR();
        String message = invoker.getMessage();
        final String logTag = invoker.getLogTag();
        boolean async = invoker.isAsync();
        long retryPeriodSeconds = invoker.getRetryPeriodSeconds();
        if(retryPeriodSeconds <= 0) {
            throw new IllegalArgumentException("PeriodSeconds can not be zero or less than zero");
        }
        try {
            runable.run();
        }catch(Throwable errThrowable) {
            ErrorHandleFunction function = null;
            TapConnectionContext tapConnectionContext = null;
            ConnectionFunctions<?> connectionFunctions = null;
            if(node instanceof ConnectionNode) {
                ConnectionNode connectionNode = (ConnectionNode) node;
                connectionFunctions = connectionNode.getConnectionFunctions();
                if (null != connectionFunctions) {
                    function = connectionFunctions.getErrorHandleFunction();
                }else {
                    throw new CoreException("ConnectionFunctions must be not null,connectionNode does not contain ConnectionFunctions");
                }
                tapConnectionContext = connectionNode.getConnectionContext();
            } else if(node instanceof ConnectorNode) {
                ConnectorNode connectorNode = (ConnectorNode) node;
                connectionFunctions = connectorNode.getConnectorFunctions();
                if (null != connectionFunctions) {
                    function = connectionFunctions.getErrorHandleFunction();
                }else {
                    throw new CoreException("ConnectionFunctions must be not null,connectionNode does not contain connectionFunctions");
                }
                tapConnectionContext = connectorNode.getConnectorContext();
            }
            if (null == tapConnectionContext){
                throw new IllegalArgumentException("NeedTry filed ,cause tapConnectionContext:[ConnectionContext or ConnectorContext] is Null,the param must not be null!");
            }

            if(null == function){
                TapLogger.debug(logTag,"This PDK data source not support retry. ");
                if(errThrowable instanceof CoreException) {
                    throw (CoreException) errThrowable;
                }
                throw new CoreException(PDKRunnerErrorCodes.COMMON_UNKNOWN, message + " execute failed, " + errThrowable.getMessage(),errThrowable);
            }

            ErrorHandleFunction finalFunction = function;
            TapConnectionContext finalTapConnectionContext = tapConnectionContext;
            try {
                RetryOptions retryOptions = finalFunction.needRetry(finalTapConnectionContext, method,errThrowable);
                if(retryOptions == null || !retryOptions.isNeedRetry()) {
                    throw errThrowable;
                }
                if(retryOptions.getBeforeRetryMethod() != null) {
                    CommonUtils.ignoreAnyError(() -> retryOptions.getBeforeRetryMethod().run(), logTag);
                }
            } catch (Throwable e) {
                TapLogger.info(logTag,TapAPIErrorCodes.NEED_RETRY_FAILED+" Error retry failed: Not need retry." + logTag);
                if(errThrowable instanceof CoreException) {
                    throw (CoreException) errThrowable;
                }
                throw new CoreException(PDKRunnerErrorCodes.COMMON_UNKNOWN, message + " execute failed, " + errThrowable.getMessage(),errThrowable);
            }

            long retryTimes = invoker.getRetryTimes();
            if(retryTimes > 0) {
                TapLogger.warn(logTag, "AutoRetry info: retry times ({}) | periodSeconds ({}s) | error [{}] Please wait...", invoker.getRetryTimes(), retryPeriodSeconds,errThrowable.getMessage());//, message
                invoker.setRetryTimes(retryTimes-1);
                if(async) {
                    ExecutorsManager.getInstance().getScheduledExecutorService().schedule(() -> autoRetry(node,method,invoker), retryPeriodSeconds, TimeUnit.SECONDS);
                } else {
                    synchronized (invoker) {
                        try {
                            invoker.wait(retryPeriodSeconds * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    autoRetry(node, method, invoker);
                }
            } else {
                if(errThrowable instanceof CoreException) {
                    throw (CoreException) errThrowable;
                }
                throw new CoreException(PDKRunnerErrorCodes.COMMON_UNKNOWN, message + " execute failed, " + errThrowable.getMessage(),errThrowable);
            }
        }
    }

    public static void autoRetryAsync(AnyError runnable, String tag, String message, long times, long periodSeconds) {
        try {
            runnable.run();
        } catch(Throwable throwable) {
            TapLogger.info(tag, "AutoRetryAsync info: retry times ({}) | periodSeconds ({}). Please wait...\\n\"", message, times, periodSeconds);
            if(times > 0) {
                ExecutorsManager.getInstance().getScheduledExecutorService().schedule(() -> {
                    autoRetryAsync(runnable, tag, message, times - 1, periodSeconds);
                }, periodSeconds, TimeUnit.SECONDS);
            } else {
                if(throwable instanceof CoreException) {
                    throw (CoreException) throwable;
                }
                throw new CoreException(PDKRunnerErrorCodes.COMMON_UNKNOWN, message + " execute failed, " + throwable.getMessage());
            }
        }
    }

    public static void ignoreAnyError(AnyError runnable, String tag) {
        try {
            runnable.run();
        } catch(CoreException coreException) {
            coreException.printStackTrace();
            TapLogger.warn(tag, "Error code {} message {} will be ignored. ", coreException.getCode(), ExceptionUtils.getStackTrace(coreException));
        } catch(Throwable throwable) {
            if(!(throwable instanceof QuiteException)) {
                throwable.printStackTrace();
                TapLogger.warn(tag, "Unknown error message {} will be ignored. ", ExceptionUtils.getStackTrace(throwable));
            }
        }
    }

    private static AtomicLong counter = new AtomicLong(0);

    /**
     * A lot faster than UUID.
     *
     * 1000000 UUID takes 1089, this takes 139
     *
     * @return
     */
    public static String processUniqueId() {
        return Long.toHexString(System.currentTimeMillis()) + Long.toHexString(counter.getAndIncrement());
    }

    public static void handleAnyError(AnyError r) {
        handleAnyError(r, null);
    }
    public static void handleAnyError(AnyError r, Consumer<Throwable> consumer) {
        try {
            r.run();
        } catch(CoreException coreException) {
            if(consumer != null) {
                consumer.accept(coreException);
            } else {
                throw coreException;
            }
        } catch(Throwable throwable) {
            if(consumer != null) {
                consumer.accept(throwable);
            } else {
                throw new CoreException(PDKRunnerErrorCodes.COMMON_UNKNOWN, throwable.getMessage(), throwable);
            }
        }
    }

    public static void logError(String logTag, String prefix, Throwable throwable) {
        TapLogger.error(logTag, errorMessage(prefix, throwable));
    }

    public static String errorMessage(String prefix, Throwable throwable) {
        if(throwable instanceof CoreException) {
            CoreException coreException = (CoreException) throwable;
            StringBuilder builder = new StringBuilder(prefix).append(",");
            builder.append(" code ").append(coreException.getCode()).append(" message ").append(coreException.getMessage());
            List<CoreException> moreExceptions = coreException.getMoreExceptions();
            if(moreExceptions != null) {
                builder.append(", more errors,");
                for(CoreException coreException1 : moreExceptions) {
                    builder.append(" code ").append(coreException1.getCode()).append(" message ").append(coreException1.getMessage()).append(";");
                }
            }
            return builder.toString();
        } else {
            return prefix + ", unknown error " + throwable.getMessage();
        }
    }

    public static CoreException generateCoreException(Throwable throwable) {
        if (throwable instanceof CoreException) {
            return (CoreException) throwable;
        } else {
            Throwable cause = throwable.getCause();
            if (cause != null && cause instanceof CoreException) {
                return (CoreException) cause;
            }
        }
        return new CoreException(PDKRunnerErrorCodes.COMMON_UNKNOWN, throwable.getMessage(), throwable);
    }

    public static String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if(value == null)
            value = System.getenv(key);
        if(value == null)
            value = defaultValue;
        return value;
    }

    public static String getProperty(String key) {
        String value = System.getProperty(key);
        if(value == null)
            value = System.getenv(key);
        return value;
    }

    public static boolean getPropertyBool(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        if(value == null)
            value = System.getenv(key);
        Boolean valueBoolean = null;
        if(value != null) {
            try {
                valueBoolean = Boolean.parseBoolean(value);
            } catch(Throwable ignored) {}
        }
        if(valueBoolean == null)
            valueBoolean = defaultValue;
        return valueBoolean;
    }

    public static int getPropertyInt(String key, int defaultValue) {
        String value = System.getProperty(key);
        if(value == null)
            value = System.getenv(key);
        Integer valueInt = null;
        if(value != null) {
            try {
                valueInt = Integer.parseInt(value);
            } catch(Throwable ignored) {}
        }
        if(valueInt == null)
            valueInt = defaultValue;
        return valueInt;
    }

    public static long getPropertyLong(String key, long defaultValue) {
        String value = System.getProperty(key);
        if(value == null)
            value = System.getenv(key);
        Long valueLong = null;
        if(value != null) {
            try {
                valueLong = Long.parseLong(value);
            } catch(Throwable ignored) {}
        }
        if(valueLong == null)
            valueLong = defaultValue;
        return valueLong;
    }

    public static void setProperty(String key, String value) {
        System.setProperty(key, value);
    }

    public static void main(String[] args) {
//        AtomicLong counter = new AtomicLong();
//
//        int times = 2000000;
//        long time = System.currentTimeMillis();
//        for(int i = 0; i < times; i++) {
//            Runnable r = new Runnable() {
//                @Override
//                public void run() {
//                    counter.incrementAndGet();
//                }
//            };
//            r.run();
//        }
//        System.out.println("1takes " + (System.currentTimeMillis() - time));
//
//        time = System.currentTimeMillis();
//        for(int i = 0; i < times; i++) {
//            Runnable r = () -> counter.incrementAndGet();
//            r.run();
//        }
//        System.out.println("2takes " + (System.currentTimeMillis() - time));
        fun(10,100);
    }

    public static void fun(int j,int k){
        final int i = k;
        System.out.println(i+"---"+k);
        if (j-->0){
            fun(j,k);
        }else {
            return;
        }
    }
}
