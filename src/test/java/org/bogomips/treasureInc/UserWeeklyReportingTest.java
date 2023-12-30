package org.bogomips.treasureInc;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.bogomips.treasureInc.reporting.UserWeeklyReporting;
import org.bogomips.treasureInc.user.UserResource;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestHTTPEndpoint(UserWeeklyReporting.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserWeeklyReportingTest {

    public static String API_KEY_TEST = "TESTAPIKEY";
    public static String API_KEY_TEST_HEADER = "X-API-KEY";
    @Inject
    MockMailbox mailbox;

    @BeforeEach
    public void init(){
        mailbox.clear();
    }

    @Test
    @Order(1)
    public void shouldSendWeeklyReport(){
        given()
            .header(API_KEY_TEST_HEADER, API_KEY_TEST)
            .when().get("/sendWeeklyReport")
            .then()
            .statusCode(Response.Status.OK.getStatusCode());
    }
}
