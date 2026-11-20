package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.pubsub.PubSubCommands.RedisSubscriber;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.bogomips.treasureInc.maestro.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Against the Redis and PostgreSQL of the dev services; the engine is played by the test on the Redis channels. */
@QuarkusTest
@TestHTTPEndpoint(AdminResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdminResourceTest {

    public static String API_KEY_TEST = "TESTAPIKEY";
    public static String API_KEY_TEST_HEADER = "X-API-KEY";
    private static final String REQUESTS = "message-log";
    private static final String REPLIES = "message-log-res";

    @Inject
    RedisDataSource redis;

    @Inject
    ReactiveRedisDataSource reactiveRedis;

    @Inject
    ObjectMapper mapper;

    private RedisSubscriber fakeEngine;

    @AfterEach
    void stopFakeEngine() {
        if (fakeEngine != null) {
            fakeEngine.unsubscribe();
            fakeEngine = null;
        }
    }

    @Test
    @Order(1)
    public void adminEndpointsNeedTheApiKey() {
        given().when().get("/status").then().statusCode(Response.Status.UNAUTHORIZED.getStatusCode());
        given().header(API_KEY_TEST_HEADER, "wrong").when().get("/status")
                .then().statusCode(Response.Status.UNAUTHORIZED.getStatusCode());
        given().when().post("/sync").then().statusCode(Response.Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    @Order(2)
    @RunOnVertxContext
    public void initPlayers(TransactionalUniAsserter asserter) {
        asserter.execute(() -> User.deleteAll());
        asserter.execute(() -> player("TESTADMIN1", Instant.now()).persist());
        asserter.execute(() -> player("TESTADMIN2", Instant.now()).persist());
        asserter.execute(() -> player("TESTADMIN3", Instant.now().minus(Duration.ofDays(3))).persist());
        // a lambda, not User::count: a method reference would call PanacheEntityBase.count, which Panache does not enhance
        asserter.assertEquals(() -> User.count(), 3L);
    }

    @Test
    @Order(3)
    public void statusSummarizesRedisAndTheDatabase() {
        Instant now = Instant.now();
        var sessions = redis.hash(String.class);
        sessions.hset(RedisGameState.SESSIONS_KEY, "00000000001", session(now.minusSeconds(10), now.minusSeconds(10), false));
        sessions.hset(RedisGameState.SESSIONS_KEY, "00000000002", session(now.minusSeconds(120), now.minusSeconds(90), true));
        sessions.hset(RedisGameState.SESSIONS_KEY, "00000000003",
                session(now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofMinutes(40)), true));
        sessions.hset(RedisGameState.SESSIONS_KEY, "00000000004",
                session(now.minus(Duration.ofMinutes(50)), now.minus(Duration.ofMinutes(50)), false));
        // the two latest complete buckets: still in the last minute if a bucket ends during the test
        String[] buckets = RedisGameState.lastMinuteBuckets(now);
        redis.value(String.class, Long.class).set(buckets[4], 30L);
        redis.value(String.class, Long.class).set(buckets[5], 12L);
        try {
            given()
                    .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                    .when().get("/status")
                    .then()
                    .statusCode(Response.Status.OK.getStatusCode())
                    .body("generatedAt", notNullValue())
                    .body("connectedUsers", is(2))
                    .body("desyncedUsers", is(2))
                    .body("redisMemoryMb", greaterThan(0f))
                    .body("requestsPerMinute", is(42.0f))
                    .body("extra.keySet()", containsInAnyOrder("totalUsers", "newUsersToday", "oldestPendingSync"))
                    .body("extra.totalUsers.label", not(emptyOrNullString()))
                    .body("extra.totalUsers.value", is(3))
                    .body("extra.newUsersToday.value", is(2))
                    .body("extra.oldestPendingSync.value", allOf(greaterThanOrEqualTo(2400), lessThan(2460)))
                    .body("extra.oldestPendingSync.unit", is("s"));
        } finally {
            redis.key().del(RedisGameState.SESSIONS_KEY, buckets[4], buckets[5]);
        }
    }

    @Test
    @Order(4)
    public void syncAsksTheEngineAndReturnsItsReport() {
        fakeEngine = engineReplying(request -> {
            ObjectNode body = mapper.createObjectNode().put("status", 200).put("message", "OK");
            body.putObject("payload").put("startedAt", "2026-11-20T10:00:00Z").put("durationMs", 37)
                    .put("syncedUsers", 4).put("errors", 1);
            return body;
        });

        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().post("/sync")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("startedAt", is("2026-11-20T10:00:00Z"))
                .body("durationMs", is(37))
                .body("syncedUsers", is(4))
                .body("errors", is(1));
    }

    @Test
    @Order(5)
    public void anEngineErrorIsABadGateway() {
        fakeEngine = engineReplying(request -> mapper.createObjectNode().put("error", "database down"));

        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().post("/sync")
                .then()
                .statusCode(Response.Status.BAD_GATEWAY.getStatusCode())
                .body("message", containsString("database down"));
    }

    @Test
    @Order(6)
    public void noEngineIsAGatewayTimeout() {
        // maestro.engine.sync-timeout is 3s in the test profile
        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().post("/sync")
                .then()
                .statusCode(Response.Status.GATEWAY_TIMEOUT.getStatusCode())
                .body("message", containsString("forceSync"));
    }

    /** Subscribes to the request channel like the engine and answers forceSync requests with {@code reply}. */
    private RedisSubscriber engineReplying(Function<JsonNode, JsonNode> reply) {
        return redis.pubsub(String.class).subscribe(REQUESTS, message -> {
            try {
                JsonNode request = mapper.readTree(message);
                if (!"forceSync".equals(request.at("/payload/type").asText())) {
                    return;
                }
                ObjectNode envelope = mapper.createObjectNode();
                envelope.set("replyId", request.get("replyId"));
                envelope.set("payload", reply.apply(request));
                // called on the event loop: the reactive client must be used
                reactiveRedis.pubsub(String.class).publish(REPLIES, envelope.toString()).subscribe().with(v -> { });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private String session(Instant lastAction, Instant lastSync, boolean dirty) {
        ObjectNode n = mapper.createObjectNode();
        n.putObject("user").put("id", 1).put("public_key", "00000000001").put("money", 100);
        n.put("lastAction", lastAction.toString()).put("keyHash", "h").put("lastSync", lastSync.toString())
                .put("dirty", dirty);
        return n.toString();
    }

    private static User player(String publicKey, Instant createdAt) {
        User u = new User();
        u.publicKey = publicKey;
        u.money = 100;
        u.createdAt = new Timestamp(createdAt.toEpochMilli());
        u.lastLogin = new Timestamp(createdAt.toEpochMilli());
        return u;
    }
}
