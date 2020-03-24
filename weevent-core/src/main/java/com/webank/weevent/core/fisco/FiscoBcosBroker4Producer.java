package com.webank.weevent.core.fisco;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.fisco.util.ParamCheckUtils;
import com.webank.weevent.core.fisco.web3sdk.FiscoBcosDelegate;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.JsonHelper;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.client.WeEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FiscoBcosBroker4Producer extends FiscoBcosTopicAdmin implements IProducer {

    public FiscoBcosBroker4Producer(FiscoBcosDelegate fiscoBcosDelegate) {
        super(fiscoBcosDelegate);
    }

    @Override
    public boolean startProducer() {
        return true;
    }

    @Override
    public boolean shutdownProducer() {
        return true;
    }

    @Override
    public CompletableFuture<SendResult> publish(WeEvent event, String groupIdStr) throws BrokerException {
        log.debug("publish {} groupId: {}", event, groupIdStr);

        String groupId = selectGroupId(groupIdStr);
        this.validateGroupId(groupId);
        ParamCheckUtils.validateEvent(event);

        // publishEvent support async operator in callback
        if (event.getExtensions().containsKey(WeEvent.WeEvent_SIGN)) {
            return fiscoBcosDelegate.sendRawTransaction(event.getTopic(),
                    Long.parseLong(groupId),
                    new String(event.getContent(), StandardCharsets.UTF_8));
        } else {
            return fiscoBcosDelegate.publishEvent(event.getTopic(),
                    Long.parseLong(groupId),
                    new String(event.getContent(), StandardCharsets.UTF_8),
                    JsonHelper.object2Json(event.getExtensions()));
        }
    }
}
