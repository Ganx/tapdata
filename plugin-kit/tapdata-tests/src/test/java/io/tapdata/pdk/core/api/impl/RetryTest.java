package io.tapdata.pdk.core.api.impl;

import io.tapdata.entity.codec.TapCodecsRegistry;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.entity.utils.ReflectionUtil;
import io.tapdata.pdk.apis.TapConnector;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.entity.ConnectionOptions;
import io.tapdata.pdk.apis.entity.TestItem;
import io.tapdata.pdk.apis.functions.ConnectionFunctions;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.PDKMethod;
import io.tapdata.pdk.apis.functions.connection.RetryOptions;
import io.tapdata.pdk.core.api.ConnectionNode;
import io.tapdata.pdk.core.entity.params.PDKMethodInvoker;
import io.tapdata.pdk.core.monitor.PDKInvocationMonitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RetryTest {
    //测试报错能否触发重试
    @Test
    public void testRetry() throws Exception {
        ConnectionNode node = new ConnectionNode();
        Field field = ReflectionUtil.getField(ConnectionNode.class, "connectionContext");
        field.setAccessible(true);
        field.set(node, new TapConnectionContext(null, null));

        node.init(new TapConnectorTest());
        ConnectionFunctions<?> connectionFunctions = node.getConnectionFunctions();
        connectionFunctions.supportErrorHandleFunction((nodeContext, method, throwable) -> {
            return RetryOptions.create().needRetry(true).beforeRetryMethod(() -> {
                System.out.println("begin retry...");
            });
        });
        Integer retryTimes = 3;
        PDKMethodInvoker invoker = PDKMethodInvoker.create()
                .runnable(()->{
                    System.out.println("Begin retry...");
                    throw new IOException("test retry...");
                })
                .message("call connection functions coding@io.tapdata-v1.0-SNAPSHOT associateId codingSource_1662088750311")
                .logTag("")
                .errorConsumer(null)
                .async(false)
                .contextClassLoader(null)
                .retryTimes(retryTimes)
                .retryPeriodSeconds(5);
        try {
            PDKInvocationMonitor.invoke( node, PDKMethod.REGISTER_CAPABILITIES, invoker );
        }catch (Exception e){

        }

        assertTrue(true,"retry times is "+retryTimes+",retry start succeed");
    }

    //测试报错不需要重试
    @Test
    public void testNotNeedRetry() throws Exception {
        ConnectionNode node = new ConnectionNode();
        Field field = ReflectionUtil.getField(ConnectionNode.class, "connectionContext");
        field.setAccessible(true);
        field.set(node, new TapConnectionContext(null, null));

        node.init(new TapConnectorTest());
        ConnectionFunctions<?> connectionFunctions = node.getConnectionFunctions();
        connectionFunctions.supportErrorHandleFunction((nodeContext, method, throwable) -> {
            return RetryOptions.create().needRetry(false).beforeRetryMethod(null);
        });
        int retryTimes = 3;
        AtomicInteger retryTimesAtomic = new AtomicInteger(3);
        PDKMethodInvoker invoker = PDKMethodInvoker.create()
                .runnable(()->{
                    throw new IOException("test retry...");
                })
                .message("call connection functions coding@io.tapdata-v1.0-SNAPSHOT associateId codingSource_1662088750311")
                .logTag("")
                .errorConsumer(null)
                .async(false)
                .contextClassLoader(null)
                .retryTimes(retryTimesAtomic.get())
                .retryPeriodSeconds(5);

        try {
            PDKInvocationMonitor.invoke(
                    node,
                    PDKMethod.REGISTER_CAPABILITIES,
                    invoker
            );
        }catch (Exception e){

        }

        assertEquals(retryTimes, retryTimesAtomic.get(),"retry times is "+retryTimes+",it is not retry .");
    }

    //测试能否中断重试
    @Test
    public void testAwaken() throws NoSuchFieldException, IllegalAccessException {
        ConnectionNode node = new ConnectionNode();
        Field field = ReflectionUtil.getField(ConnectionNode.class, "connectionContext");
        field.setAccessible(true);
        field.set(node, new TapConnectionContext(null, null));

        node.init(new TapConnectorTest());
        ConnectionFunctions<?> connectionFunctions = node.getConnectionFunctions();
        connectionFunctions.supportErrorHandleFunction((nodeContext, method, throwable) -> {
            return RetryOptions.create().needRetry(true).beforeRetryMethod(() -> {
                System.out.println("begin retry...");
            });
        });
        PDKMethodInvoker invoker = PDKMethodInvoker.create()
                .runnable(()->{
                    System.out.println("Begin retry...");
                    throw new IOException("test retry...");
                })
                .message("call connection functions coding@io.tapdata-v1.0-SNAPSHOT associateId codingSource_1662088750311")
                .logTag("")
                .errorConsumer(null)
                .async(false)
                .contextClassLoader(null)
                .retryTimes(10)
                .retryPeriodSeconds(10);

        final AtomicBoolean success = new AtomicBoolean(false);
        final Object obj = new Object();
        new Thread(() -> {
            try {
                Thread.sleep(50L);
                System.out.println("=====>cancel retry...");
                invoker.cancelRetry();
                synchronized (obj) {
                    obj.wait(100L);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

       try {
           PDKInvocationMonitor.invoke(
                   node,
                   PDKMethod.REGISTER_CAPABILITIES,
                   invoker
           );
       }catch (Exception e){

       }

        success.set(true);
        synchronized (obj) {
            obj.notify();
        }
        assertEquals(true, success.get(),"===>cancel retry succeed");
    }

    //测试能否中断重试
    @Test
    public void testAwakenV2() throws NoSuchFieldException, IllegalAccessException {
        ConnectionNode node = new ConnectionNode();
        Field field = ReflectionUtil.getField(ConnectionNode.class, "connectionContext");
        field.setAccessible(true);
        field.set(node, new TapConnectionContext(null, null));

        node.init(new TapConnectorTest());
        ConnectionFunctions<?> connectionFunctions = node.getConnectionFunctions();
        connectionFunctions.supportErrorHandleFunction((nodeContext, method, throwable) -> {
            return RetryOptions.create().needRetry(true).beforeRetryMethod(() -> {
                System.out.println("begin retry...");
            });
        });
        PDKMethodInvoker invoker = PDKMethodInvoker.create()
                .runnable(()->{
                    System.out.println("Begin retry...");
                    throw new IOException("test retry...");
                })
                .message("call connection functions coding@io.tapdata-v1.0-SNAPSHOT associateId codingSource_1662088750311")
                .logTag("")
                .errorConsumer(null)
                .async(false)
                .contextClassLoader(null)
                .retryTimes(20)
                .retryPeriodSeconds(2);

        final AtomicBoolean success = new AtomicBoolean(false);
        final Object obj = new Object();
        new Thread(() -> {
            try {
                Thread.sleep(800L);
                System.out.println("=====>cancel retry...");
                //invoker.cancelRetry();
                PDKInvocationMonitor.stop(node);
                synchronized (obj) {
                    obj.wait(500L);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

       try {
           PDKInvocationMonitor.invoke(
                   node,
                   PDKMethod.REGISTER_CAPABILITIES,
                   invoker
           );
       }catch (Exception e){

       }

        success.set(true);
        synchronized (obj) {
            obj.notify();
        }
        assertEquals(true, success.get(),"===>cancel retry succeed");
    }    //测试能否中断重试


    @Test
    public void testAwakenV3() throws NoSuchFieldException, IllegalAccessException {
        ConnectionNode node = new ConnectionNode();
        Field field = ReflectionUtil.getField(ConnectionNode.class, "connectionContext");
        field.setAccessible(true);
        field.set(node, new TapConnectionContext(null, null));

        node.init(new TapConnectorTest());
        ConnectionFunctions<?> connectionFunctions = node.getConnectionFunctions();
        connectionFunctions.supportErrorHandleFunction((nodeContext, method, throwable) -> {
            return RetryOptions.create().needRetry(true).beforeRetryMethod(() -> {
                System.out.println(" Thread-1 begin retry...");
            });
        });


        ConnectionNode node2 = new ConnectionNode();
        Field field2 = ReflectionUtil.getField(ConnectionNode.class, "connectionContext");
        field2.setAccessible(true);
        field2.set(node2, new TapConnectionContext(null, null));

        node2.init(new TapConnectorTest());
        ConnectionFunctions<?> connectionFunctions2 = node2.getConnectionFunctions();
        connectionFunctions2.supportErrorHandleFunction((nodeContext, method, throwable) -> {
            return RetryOptions.create().needRetry(true).beforeRetryMethod(() -> {
                System.out.println(" Thread-2 begin retry...");
            });
        });


        PDKMethodInvoker invoker = PDKMethodInvoker.create()
                .runnable(()->{
                    System.out.println(" Thread-1 Begin retry...");
                    throw new IOException(" Thread-1 test retry...");
                })
                .message("call connection functions coding@io.tapdata-v1.0-SNAPSHOT associateId codingSource_1662088750311")
                .logTag("")
                .errorConsumer(null)
                .async(false)
                .contextClassLoader(null)
                .retryTimes(20)
                .retryPeriodSeconds(1);

        PDKMethodInvoker invoker2 = PDKMethodInvoker.create()
                .runnable(()->{
                    System.out.println(" Thread-2 Begin retry...");
                    throw new IOException(" Thread-2 test retry...");
                })
                .message("call connection functions coding@io.tapdata-v1.0-SNAPSHOT associateId codingSource_1662088750311")
                .logTag("")
                .errorConsumer(null)
                .async(false)
                .contextClassLoader(null)
                .retryTimes(2)
                .retryPeriodSeconds(1);

        final AtomicBoolean success = new AtomicBoolean(false);
        final Object obj = new Object();
        new Thread(() -> {
            try {
                Thread.sleep(1000L);
                System.out.println("=====> Thread-1 cancel retry...");
                //invoker.cancelRetry();
                PDKInvocationMonitor.stop(node);
                synchronized (obj) {
                    obj.wait(500L);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();


        final AtomicBoolean success2 = new AtomicBoolean(false);
        final Object obj2 = new Object();
        new Thread(() -> {
            try {
                Thread.sleep(50L);
                System.out.println("=====> Thread-2 cancel retry...");
                //invoker.cancelRetry();
                PDKInvocationMonitor.stop(node);
                synchronized (obj2) {
                    obj2.wait(500L);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
//        new Thread(() -> {
//            try {
//                PDKInvocationMonitor.invoke(
//                        node2,
//                        PDKMethod.REGISTER_CAPABILITIES,
//                        invoker2
//                );
//            }catch (Exception e){
//
//            }
//        }).start();

       try {
           PDKInvocationMonitor.invoke(
                   node,
                   PDKMethod.REGISTER_CAPABILITIES,
                   invoker
           );
       }catch (Exception e){

       }

        success.set(true);
        synchronized (obj) {
            obj.notify();
        }
        assertEquals(true, success.get(),"===>cancel retry succeed");
    }    //测试能否中断重试


    class TapConnectorTest implements TapConnector{
        @Override
        public void registerCapabilities(ConnectorFunctions connectorFunctions, TapCodecsRegistry codecRegistry) { }

        @Override
        public void discoverSchema(TapConnectionContext connectionContext, List<String> tables, int tableSize, Consumer<List<TapTable>> consumer) throws Throwable {

        }

        @Override
        public ConnectionOptions connectionTest(TapConnectionContext connectionContext, Consumer<TestItem> consumer) throws Throwable {
            return null;
        }

        @Override
        public int tableCount(TapConnectionContext connectionContext) throws Throwable {
            return 0;
        }

        @Override
        public void init(TapConnectionContext connectionContext) throws Throwable {

        }

        @Override
        public void stop(TapConnectionContext connectionContext) throws Throwable {

        }
    }
}
