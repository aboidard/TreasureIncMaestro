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
        given().contentType("application/json").body("{}").when().post("/gifts")
                .then().statusCode(Response.Status.UNAUTHORIZED.getStatusCode());
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
        fakeEngine = engineReplying("forceSync", request -> {
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
        fakeEngine = engineReplying("forceSync", request -> mapper.createObjectNode().put("error", "database down"));

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

    @Test
    @Order(7)
    public void aGiftIsForwardedToTheEngine() throws Exception {
        java.util.concurrent.atomic.AtomicReference<JsonNode> received = new java.util.concurrent.atomic.AtomicReference<>();
        fakeEngine = engineReplying("sendGift", request -> {
            received.set(request.at("/payload/params"));
            ObjectNode body = mapper.createObjectNode().put("status", 200).put("message", "OK");
            body.putObject("payload").put("batch", "b-1").put("sent", 12);
            return body;
        });

        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .contentType("application/json")
                .body("{\"title\":\"Lancement du jeu : profitez de ces gemmes gratuites !\","
                        + "\"reward\":{\"gems\":1000,\"items\":2,\"rarity\":\"epic\"},"
                        + "\"players\":{\"activeDays\":7,\"publicKeys\":[\"12345678900\"]},\"expiresInDays\":30}")
                .when().post("/gifts")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("batch", is("b-1"))
                .body("sent", is(12));

        JsonNode params = received.get();
        org.junit.jupiter.api.Assertions.assertEquals(mapper.readTree(
                "{\"title\":\"Lancement du jeu : profitez de ces gemmes gratuites !\","
                        + "\"reward\":{\"gems\":1000,\"items\":2,\"rarity\":\"epic\"},"
                        + "\"players\":{\"activeDays\":7,\"publicKeys\":[\"12345678900\"]},\"expiresInDays\":30}"),
                params, "the gift is sent as it was given, without the fields left out");
    }

    @Test
    @Order(8)
    public void aGiftRefusedByTheEngineIsABadRequest() {
        fakeEngine = engineReplying("sendGift", request -> mapper.createObjectNode().put("status", 400)
                .put("message", "Bad Request: title is required, 120 characters at most"));

        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .contentType("application/json")
                .body("{\"reward\":{\"gems\":1000}}")
                .when().post("/gifts")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body("message", containsString("title is required"));

        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .contentType("application/json")
                .body("{\"title\":\"t\",\"reward\":{\"gems\":\"beaucoup\"}}")
                .when().post("/gifts")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @Order(9)
    public void theMaintenanceIsForwardedToTheEngine() throws Exception {
        java.util.concurrent.atomic.AtomicReference<JsonNode> received = new java.util.concurrent.atomic.AtomicReference<>();
        fakeEngine = engineReplying("setMaintenance", request -> {
            received.set(request.at("/payload/params"));
            return settingsReply(true, true, "Mise à jour", "0.22");
        });

        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .contentType("application/json")
                .body("{\"enabled\":true,\"reason\":\"Mise à jour\",\"start\":null,\"end\":\"2026-11-22T12:00:00Z\"}")
                .when().put("/settings/maintenance")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("maintenance.enabled", is(true))
                .body("maintenance.active", is(true))
                .body("maintenance.reason", is("Mise à jour"))
                .body("minClientVersion", is("0.22"));

        org.junit.jupiter.api.Assertions.assertEquals(true, received.get().get("enabled").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("2026-11-22T12:00:00Z", received.get().get("end").asText());

        given().header(API_KEY_TEST_HEADER, API_KEY_TEST).contentType("application/json").body("{}")
                .when().put("/settings/maintenance")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode());
        given().when().get("/settings").then().statusCode(Response.Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    @Order(10)
    public void settingsAndClientVersionComeFromTheEngine() {
        fakeEngine = engineReplying("getSettings", request -> settingsReply(false, false, null, "0.21"));
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().get("/settings")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("maintenance.enabled", is(false))
                .body("maintenance.reason", nullValue())
                .body("minClientVersion", is("0.21"));
        stopFakeEngine();

        fakeEngine = engineReplying("setMinClientVersion", request -> "bad".equals(request.at("/payload/params/version").asText())
                ? mapper.createObjectNode().put("status", 400).put("message", "Bad Request: version is numbers")
                : settingsReply(false, false, null, request.at("/payload/params/version").asText()));
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST).contentType("application/json")
                .body("{\"version\":\" 0.23 \"}")
                .when().put("/settings/min-client-version")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("minClientVersion", is("0.23"));
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST).contentType("application/json")
                .body("{\"version\":\"bad\"}")
                .when().put("/settings/min-client-version")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body("message", containsString("version"));
    }

    @Test
    void eventsAreSavedThroughTheEngineWithTheirCodeFromThePath() {
        fakeEngine = engineReplying("saveEvent", request -> {
            JsonNode params = request.at("/payload/params");
            if (params.path("level").asInt() > 3) {
                return mapper.createObjectNode().put("status", 400).put("message", "Bad Request: level is 1 to 3");
            }
            ObjectNode body = mapper.createObjectNode().put("status", 200).put("message", "OK");
            body.set("payload", params);
            return body;
        });
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST).contentType("application/json")
                .body("{\"name\":\"Lancement\",\"level\":1,\"floors\":10,\"rewards\":{\"gems\":1000},\"maxPerDay\":1}")
                .when().put("/events/lancement")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("code", is("lancement"))
                .body("rewards.gems", is(1000));
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST).contentType("application/json")
                .body("{\"name\":\"X\",\"level\":9,\"floors\":10}")
                .when().put("/events/x")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body("message", containsString("level"));
        stopFakeEngine();

        fakeEngine = engineReplying("listEvents", request -> {
            ObjectNode body = mapper.createObjectNode().put("status", 200).put("message", "OK");
            body.putObject("payload").putArray("events").addObject().put("code", "lancement").put("open", true);
            return body;
        });
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().get("/events")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("events[0].code", is("lancement"));
        stopFakeEngine();

        fakeEngine = engineReplying("deleteEvent", request -> "lancement".equals(request.at("/payload/params/code").asText())
                ? mapper.createObjectNode().put("status", 200).put("message", "OK")
                        .set("payload", mapper.createObjectNode().put("code", "lancement"))
                : mapper.createObjectNode().put("status", 404).put("message", "Event not found"));
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().delete("/events/lancement")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
        given().header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().delete("/events/morgoth")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body("message", containsString("not found"));
    }

    private ObjectNode settingsReply(boolean enabled, boolean active, String reason, String version) {
        ObjectNode body = mapper.createObjectNode().put("status", 200).put("message", "OK");
        ObjectNode payload = body.putObject("payload");
        payload.putObject("maintenance").put("enabled", enabled).put("active", active).put("reason", reason)
                .putNull("start").put("end", enabled ? "2026-11-22T12:00:00Z" : null);
        payload.put("minClientVersion", version).put("updatedAt", "2026-11-22T10:00:00Z");
        return body;
    }

    /** Subscribes to the request channel like the engine and answers the requests of this type with {@code reply}. */
    private RedisSubscriber engineReplying(String type, Function<JsonNode, JsonNode> reply) {
        return redis.pubsub(String.class).subscribe(REQUESTS, message -> {
            try {
                JsonNode request = mapper.readTree(message);
                if (!type.equals(request.at("/payload/type").asText())) {
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
