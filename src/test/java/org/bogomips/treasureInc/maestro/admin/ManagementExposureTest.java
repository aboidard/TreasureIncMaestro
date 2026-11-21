package org.bogomips.treasureInc.maestro.admin;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** /q/health and /q/metrics must not be reachable through the public HTTP port. */
@QuarkusTest
public class ManagementExposureTest {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String API_KEY = "TESTAPIKEY";

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    public void technicalEndpointsAreNotOnThePublicPort() {
        // the API filter answers 401 to anything without the key, so ask with the key: /q/* must not exist here
        given().header(API_KEY_HEADER, API_KEY).when().get("/q/health").then().statusCode(404);
        given().header(API_KEY_HEADER, API_KEY).when().get("/q/metrics").then().statusCode(404);
    }

    @Test
    public void technicalEndpointsAreOnTheManagementPort() {
        given().port(managementPort).when().get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
        given().port(managementPort).when().get("/q/metrics").then().statusCode(200);
    }

    @Test
    public void adminHealthNeedsTheApiKey() {
        given().when().get("/admin/health").then().statusCode(401);
        given().header(API_KEY_HEADER, API_KEY).when().get("/admin/health")
                .then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks[0].name", equalTo("treasure-inc"));
    }
}
