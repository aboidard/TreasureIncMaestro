package org.bogomips.treasureInc;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class GreetingResourceTest {

    @BeforeAll
    public static void setup() {


           /* persist(new User("publicKey01", "privateKey01", 100));
            persist(new User("publicKey02", "privateKey02", 100));
            persist(new User("publicKey03", "privateKey03", 100));*/
    }
    @Test
    public void testHelloEndpoint() {
        given()
          .when().get("/users")
          .then()
             .statusCode(200)
             .body(is("[{\"id\":1,\"publicKey\":\"13278661044\",\"privateKey\":\"bI3zpZreUpNq7niNHD3s26ckljQgj6\",\"money\":100000,\"createdAt\":\"2023-10-21T16:18:11.977+00:00\",\"updatedAt\":\"2023-10-21T16:18:11.977+00:00\"},{\"id\":2,\"publicKey\":\"53521400459\",\"privateKey\":\"S1KPZDwSjuOQkIW6xfJfZjzbS0MDGK\",\"money\":100000,\"createdAt\":\"2023-12-03T11:08:40.038+00:00\",\"updatedAt\":\"2023-12-03T11:08:40.038+00:00\"},{\"id\":3,\"publicKey\":\"27354294161\",\"privateKey\":\"ii12wy6ZqWgWZOgbQvGSclm8A2JQTr\",\"money\":100000,\"createdAt\":\"2023-12-03T11:08:41.062+00:00\",\"updatedAt\":\"2023-12-03T11:08:41.062+00:00\"},{\"id\":4,\"publicKey\":\"72507616009\",\"privateKey\":\"yvzfVqlT9ksXaKGxDOlKckUgMmrgdF\",\"money\":100000,\"createdAt\":\"2023-12-03T11:08:41.638+00:00\",\"updatedAt\":\"2023-12-03T11:08:41.638+00:00\"},{\"id\":5,\"publicKey\":\"01488033302\",\"privateKey\":\"E1Kt5sogy5jzHu5cMSIvnm8gs2JH3h\",\"money\":100000,\"createdAt\":\"2023-12-03T11:08:42.125+00:00\",\"updatedAt\":\"2023-12-03T11:08:42.125+00:00\"},{\"id\":6,\"publicKey\":\"41952775307\",\"privateKey\":\"RtXXfSkCEBDVX6kbPbtCj1vn6vGYdi\",\"money\":100000,\"createdAt\":\"2023-12-03T11:08:42.603+00:00\",\"updatedAt\":\"2023-12-03T11:08:42.603+00:00\"}]"));
    }

}