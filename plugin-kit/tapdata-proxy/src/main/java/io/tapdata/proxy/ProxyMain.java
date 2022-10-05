package io.tapdata.proxy;

import io.tapdata.entity.annotations.Bean;
import io.tapdata.entity.annotations.MainMethod;
import io.tapdata.entity.error.CoreException;
import io.tapdata.modules.api.net.error.NetErrors;
import io.tapdata.modules.api.net.service.CommandExecutionService;
import io.tapdata.modules.api.net.service.EventQueueService;
import io.tapdata.modules.api.net.service.node.connection.NodeConnectionFactory;
import io.tapdata.modules.api.proxy.data.NewDataReceived;
import io.tapdata.pdk.apis.entity.CommandInfo;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static io.tapdata.entity.simplify.TapSimplify.map;

@Bean
@MainMethod("main")
public class ProxyMain {
	@Bean
	private NodeConnectionFactory nodeConnectionFactory;
	@Bean
	private CommandExecutionService commandExecutionService;

	@Bean(type = "sync")
	private EventQueueService eventQueueService;
	public void main() {
		nodeConnectionFactory.registerReceiver(CommandInfo.class.getSimpleName(), this::handleCommandInfo);
		nodeConnectionFactory.registerReceiver(NewDataReceived.class.getSimpleName(), this::handleNewDataReceived);
	}

	private void handleNewDataReceived(String nodeId, NewDataReceived newDataReceived, BiConsumer<Object, Throwable> biConsumer) {
		eventQueueService.newDataReceived(newDataReceived.getSubscribeIds());
		biConsumer.accept(null, null);
	}

	private void handleCommandInfo(String nodeId, CommandInfo commandInfo, BiConsumer<Object, Throwable> biConsumer) {
		commandExecutionService.callLocal(commandInfo, (result, throwable) -> {
			CoreException coreException = null;
			if(throwable != null) {
				if(throwable instanceof CoreException) {
					coreException = (CoreException) throwable;
				} else {
					coreException = new CoreException(NetErrors.UNKNOWN_ERROR, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
				}
			}
			biConsumer.accept(result, coreException);
		});
	}
}
