package com.quorum.tessera.sync;

import com.quorum.tessera.io.IOCallback;
import com.quorum.tessera.partyinfo.PartyInfoValidatorCallback;
import com.quorum.tessera.partyinfo.model.Recipient;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ClientEndpoint
public class WebSocketPartyInfoValidatorCallback implements PartyInfoValidatorCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketPartyInfoValidatorCallback.class);

    private final WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    private final SynchronousQueue<String> result = new SynchronousQueue<>();

    @Override
    public String requestDecode(Recipient recipient, byte[] encodedPayloadData) {

        URI uri = UriBuilder.fromPath(recipient.getUrl()).path("validate").build();
        LOGGER.info("Send request {} to decode encoded data", uri);

        Session session = WebSocketSessionCallback.execute(() -> container.connectToServer(this, uri));
        WebSocketSessionCallback.execute(
                () -> {
                    session.getAsyncRemote().sendBinary(ByteBuffer.wrap(encodedPayloadData));
                    LOGGER.info("Sent request {} to decode encoded data", uri);
                    return null;
                });

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            return ExecutorCallback.execute(
                    () -> executorService.submit(() -> result.take()).get(30, TimeUnit.SECONDS));
        } finally {
            executorService.shutdown();
        }
    }

    @OnMessage
    public void onMessage(Session session, String outcome) throws InterruptedException {
        LOGGER.info("onMessage[{}]", outcome);

        result.put(outcome);
        LOGGER.info("Added outome to queue [{}]", outcome);
        IOCallback.execute(
                () -> {
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "BYE"));
                    return null;
                });
    }
}