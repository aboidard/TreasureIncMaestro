package org.bogomips.treasureInc;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.bogomips.treasureInc.user.User;
import org.bogomips.treasureInc.user.UserResource;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Timestamp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestHTTPEndpoint(UserResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserRessourceTest {

    public static String API_KEY_TEST = "TESTAPIKEY";
    public static String API_KEY_TEST_HEADER = "X-API-KEY";

    public static Timestamp updatedAt;
    public static String publicKey;

    @Test
    @Order(1)
    public void shouldCreateUser() {
        User u = given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .contentType("application/json")
                .when().post()
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .body("publicKey", notNullValue())
                .body("privateKey", is(nullValue()))
                .body("money", is(User.DEFAULT_MONEY))
                .body("id", is(1))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue())
                .body("lastlogin", nullValue())
                .extract().as(User.class);
        updatedAt = u.updatedAt;
        publicKey = u.publicKey;
    }

    @Test
    @Order(2)
    public void shouldUpdateUserByPublicKey() {
        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .contentType("application/json")
                .body("{\"money\": 200}")
                .put(publicKey)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("publicKey", notNullValue())
                .body("privateKey", is(nullValue()))
                .body("money", is(200))
                .body("id", is(1))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue())
                .body("updatedAt", not(updatedAt))
                .body("lastlogin", nullValue());
    }

    @Test
    @Order(3)
    public void shouldFindUserByPublicKey() {
        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .contentType("application/json")
                .get(publicKey)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("publicKey", notNullValue())
                .body("privateKey", is(nullValue()))
                .body("money", is(200))
                .body("id", is(1))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue())
                .body("lastlogin", nullValue());
    }
}