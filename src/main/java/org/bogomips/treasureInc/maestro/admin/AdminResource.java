package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.health.SmallRyeHealthReporter;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;

/** Production administration. Like every endpoint, protected by the X-API-KEY header (see ApiFilter). */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private static final Logger LOG = Logger.getLogger(AdminResource.class);

    private final StatusService status;
    private final Engine engine;
    private final SmallRyeHealthReporter health;

    public AdminResource(StatusService status, Engine engine, SmallRyeHealthReporter health) {
        this.status = status;
        this.engine = engine;
        this.health = health;
    }

    /**
     * The SmallRye health report behind the API key: /q/health only lives on the management port,
     * which is not exposed outside the pod. 503 when a check is DOWN, like /q/health.
     */
    @GET
    @Path("/health")
    public Uni<Response> health() {
        return health.getHealthAsync().map(report -> Response
                .status(report.isDown() ? Response.Status.SERVICE_UNAVAILABLE : Response.Status.OK)
                .type(MediaType.APPLICATION_JSON)
                .entity(report.getPayload().toString())
                .build());
    }

    @GET
    @Path("/status")
    public Uni<StatusReport> status() {
        return status.status();
    }

    /** Has the engine write every pending Redis session to PostgreSQL now (it owns that logic and the locks). */
    @POST
    @Path("/sync")
    public Uni<SyncReport> sync() {
        LOG.info("event=forced_sync_requested");
        return engine.forceSync()
                .invoke(r -> LOG.infof("event=forced_sync_done users=%d errors=%d durationMs=%d",
                        r.syncedUsers(), r.errors(), r.durationMs()));
    }

    /**
     * Puts a gift in the gift box of the players (all of them, or those of {@code players}); the engine checks the
     * gift and writes one row per player in the database.
     */
    @POST
    @Path("/gifts")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> sendGift(GiftRequest gift) {
        if (gift == null) {
            return Uni.createFrom().item(error(Response.Status.BAD_REQUEST,
                    "A gift is required: {title, reward, players, expiresInDays}"));
        }
        LOG.infof("event=gift_send_requested title=\"%s\"", gift.title());
        return engine.sendGift(gift)
                .invoke(r -> LOG.infof("event=gift_sent batch=%s sent=%d", r.batch(), r.sent()))
                .map(r -> Response.ok(r).build());
    }

    /** The settings of the game: maintenance and oldest version of the Unity client allowed to log in. */
    @GET
    @Path("/settings")
    public Uni<GameSettings> settings() {
        return engine.getSettings();
    }

    /**
     * Switches the maintenance on or off: {enabled, reason, start, end}. While it is under way the API and the engine
     * refuse the requests of the game (503 with the reason); the administration requests are still served.
     */
    @PUT
    @Path("/settings/maintenance")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> setMaintenance(GameSettings.MaintenanceRequest maintenance) {
        if (maintenance == null || maintenance.enabled() == null) {
            return Uni.createFrom().item(error(Response.Status.BAD_REQUEST,
                    "A maintenance is required: {enabled, reason, start, end}"));
        }
        LOG.infof("event=maintenance_requested enabled=%s reason=\"%s\" start=%s end=%s", maintenance.enabled(),
                maintenance.reason(), maintenance.start(), maintenance.end());
        return engine.setMaintenance(maintenance)
                .invoke(s -> LOG.infof("event=maintenance_set enabled=%s active=%s",
                        s.maintenance().enabled(), s.maintenance().active()))
                .map(s -> Response.ok(s).build());
    }

    /** Sets the oldest version of the Unity client allowed to log in: {version}, e.g. "0.22" ("0": any). */
    @PUT
    @Path("/settings/min-client-version")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> setMinClientVersion(GameSettings.VersionRequest request) {
        if (request == null || request.version() == null || request.version().isBlank()) {
            return Uni.createFrom().item(error(Response.Status.BAD_REQUEST, "A version is required: {version}"));
        }
        LOG.infof("event=min_client_version_requested version=%s", request.version());
        return engine.setMinClientVersion(request.version().strip()).map(s -> Response.ok(s).build());
    }

    /**
     * Every event, open or not: {events: [{code, name, description, image, level, floors,
     * rewards: {gems, money, items}, maxPerDay, start, end, enabled, open, sortOrder, updatedAt}]}.
     */
    @GET
    @Path("/events")
    public Uni<JsonNode> events() {
        return engine.listEvents();
    }

    /**
     * Creates the event of this code, or replaces it: {name, description, image, level (1 easy to 3 hard), floors
     * (1 to 10), rewards: {gems, money, items}, maxPerDay (0: no limit, 1 by default), start, end (ISO-8601, null:
     * not limited), enabled, sortOrder}. Every engine instance knows it within engine.events.refresh. 400 with what is
     * wrong.
     */
    @PUT
    @Path("/events/{code}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> saveEvent(@PathParam("code") String code, JsonNode event) {
        if (event == null || !event.isObject()) {
            return Uni.createFrom().item(error(Response.Status.BAD_REQUEST,
                    "An event is required: {name, level, floors, rewards, maxPerDay, start, end, enabled}"));
        }
        ObjectNode body = ((ObjectNode) event.deepCopy()).put("code", code);
        LOG.infof("event=event_save_requested code=%s", code);
        return engine.saveEvent(body).map(saved -> Response.ok(saved).build());
    }

    /** Deletes an event; 400 when there is none. The expeditions already sent there still come back. */
    @DELETE
    @Path("/events/{code}")
    public Uni<Response> deleteEvent(@PathParam("code") String code) {
        LOG.infof("event=event_delete_requested code=%s", code);
        return engine.deleteEvent(code).map(deleted -> Response.ok(deleted).build());
    }

    @ServerExceptionMapper
    public Response engineTimeout(Engine.TimeoutException e) {
        LOG.error(e.getMessage());
        return error(Response.Status.GATEWAY_TIMEOUT, e.getMessage());
    }

    @ServerExceptionMapper
    public Response engineFailure(Engine.FailureException e) {
        LOG.errorf("event=engine_request_failed reason=%s", e.getMessage());
        return error(Response.Status.BAD_GATEWAY, "The engine failed: " + e.getMessage());
    }

    /** The engine refused the request: 400 with its reason. */
    @ServerExceptionMapper
    public Response engineRejected(Engine.RejectedException e) {
        LOG.warnf("event=engine_request_rejected reason=%s", e.getMessage());
        return error(Response.Status.BAD_REQUEST, e.getMessage());
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("message", message)).build();
    }
}
