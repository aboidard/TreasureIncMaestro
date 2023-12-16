package org.bogomips.treasureInc;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class UserTest {

    @BeforeAll
    public static void setup() {
           /* persist(new User("publicKey01", "privateKey01", 100));
            persist(new User("publicKey02", "privateKey02", 100));
            persist(new User("publicKey03", "privateKey03", 100));*/
    }
    @Test
    public void testGetUsersEndpoint() {
        given()
          .when().get("/users")
          .then()
             .statusCode(200);
    }

}