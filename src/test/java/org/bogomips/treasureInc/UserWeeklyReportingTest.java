package org.bogomips.treasureInc;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.bogomips.treasureInc.reporting.UserWeeklyReporting;
import org.bogomips.treasureInc.user.User;
import org.junit.jupiter.api.*;

import java.sql.Timestamp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestHTTPEndpoint(UserWeeklyReporting.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserWeeklyReportingTest {

    public static String API_KEY_TEST = "TESTAPIKEY";
    public static String API_KEY_TEST_HEADER = "X-API-KEY";
    public static int NUMBER_OF_TEST_USERS = 10;
    @Inject
    MockMailbox mailbox;

    @BeforeAll
    @Transactional
    public static void init(){
        // truncate the user database
        User.deleteAll();
        //create a bunch of users in database
        for(int i = 0; i < NUMBER_OF_TEST_USERS; i++){
            User u = new User();
            u.setPublicKey("TESTPUBLIC" + i);
            u.setMoney(100);
            u.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            if(i%2 == 0){
                u.setLastLogin(new Timestamp(System.currentTimeMillis()));
            }
            u.persist();
        }
        var countUser = User.count();
        assertEquals(NUMBER_OF_TEST_USERS, countUser);
    }
    @BeforeEach
    public void clear(){
        mailbox.clear();
    }

    @Test
    @Order(1)
    public void shouldSendWeeklyReport(){
        given()
            .header(API_KEY_TEST_HEADER, API_KEY_TEST)
            .when().get("/sendWeeklyReport")
            .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body(containsString("Number of users created last week : " + NUMBER_OF_TEST_USERS))
            .body(containsString("Number of users logged in last week : " + NUMBER_OF_TEST_USERS/2));
    }
}
