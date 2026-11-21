package org.bogomips.treasureInc.maestro.admin;

import io.smallrye.health.SmallRyeHealthReporter;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
