package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.pubsub.ReactivePubSubCommands;
import io.quarkus.redis.datasource.pubsub.ReactivePubSubCommands.ReactiveRedisSubscriber;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Request-reply with the engine over Redis pub/sub, the envelope the API uses:
 * {@code {replyId, payload: {type, params}}} published on the request channel, answered with
 * {@code {replyId, payload: {status, message, payload}}} on the reply channel.
 * The reply channel is shared with the API, which ignores the replyIds it does not know.
 */
@ApplicationScoped
class RedisEngine implements Engine {

    private static final Logger LOG = Logger.getLogger(RedisEngine.class);
    static final String FORCE_SYNC = "forceSync";

    private final ReactivePubSubCommands<String> pubsub;
    private final ObjectMapper mapper;
    private final String requestChannel;
    private final String replyChannel;
    private final Duration syncTimeout;

    RedisEngine(ReactiveRedisDataSource redis, ObjectMapper mapper,
                @ConfigProperty(name = "maestro.engine.channels.requests", defaultValue = "message-log") String requestChannel,
                @ConfigProperty(name = "maestro.engine.channels.replies", defaultValue = "message-log-res") String replyChannel,
                @ConfigProperty(name = "maestro.engine.sync-timeout", defaultValue = "30s") Duration syncTimeout) {
        this.pubsub = redis.pubsub(String.class);
        this.mapper = mapper;
        this.requestChannel = requestChannel;
        this.replyChannel = replyChannel;
        this.syncTimeout = syncTimeout;
    }

    @Override
    public Uni<SyncReport> forceSync() {
        return request(FORCE_SYNC, syncTimeout).map(reply -> {
            JsonNode body = reply.path("payload");
            return new SyncReport(body.path("startedAt").asText(), body.path("durationMs").asLong(),
                    body.path("syncedUsers").asInt(), body.path("errors").asInt());
        });
    }

    /** The reply body ({status, message, payload}); fails unless its status is 2xx. */
    Uni<JsonNode> request(String type, Duration timeout) {
        String replyId = "maestro-" + UUID.randomUUID();
        ObjectNode envelope = mapper.createObjectNode().put("replyId", replyId);
        envelope.putObject("payload").put("type", type).putObject("params");
        return Uni.createFrom().<JsonNode>emitter(emitter -> {
                    AtomicReference<ReactiveRedisSubscriber> subscriber = new AtomicReference<>();
                    AtomicBoolean terminated = new AtomicBoolean();
                    emitter.onTermination(() -> {
                        terminated.set(true);
                        unsubscribe(subscriber.getAndSet(null));
                    });
                    // subscribed before publishing, so the reply cannot be missed
                    pubsub.subscribe(replyChannel, message -> {
                                JsonNode reply = parse(message);
                                if (reply != null && replyId.equals(reply.path("replyId").asText())) {
                                    emitter.complete(reply.path("payload"));
                                }
                            })
                            .subscribe().with(s -> {
                                subscriber.set(s);
                                if (terminated.get()) {
                                    unsubscribe(subscriber.getAndSet(null));
                                    return;
                                }
                                LOG.infof("event=engine_request_sent type=%s replyId=%s", type, replyId);
                                pubsub.publish(requestChannel, envelope.toString())
                                        .subscribe().with(v -> { }, emitter::fail);
                            }, emitter::fail);
                })
                .ifNoItem().after(timeout).failWith(() -> new TimeoutException(type, timeout))
                .flatMap(body -> {
                    int status = body.path("status").asInt(500);
                    if (status < 200 || status >= 300) {
                        String message = body.hasNonNull("message") ? body.get("message").asText()
                                : body.path("error").asText("engine error");
                        return Uni.createFrom().failure(new FailureException(message));
                    }
                    return Uni.createFrom().item(body);
                });
    }

    private JsonNode parse(String message) {
        try {
            return mapper.readTree(message);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static void unsubscribe(ReactiveRedisSubscriber subscriber) {
        if (subscriber != null) {
            subscriber.unsubscribe().subscribe().with(v -> { },
                    t -> LOG.warnf("event=engine_unsubscribe_failed reason=%s", t.toString()));
        }
    }
}
