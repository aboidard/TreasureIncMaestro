package org.bogomips.treasureInc;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.vertx.ext.mail.MailMessage;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.bogomips.treasureInc.reporting.UserWeeklyReporting;
import org.bogomips.treasureInc.user.User;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;
import org.wildfly.common.Assert;

import java.sql.Timestamp;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestHTTPEndpoint(UserWeeklyReporting.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserWeeklyReportingTest {

    public static String API_KEY_TEST = "TESTAPIKEY";
    public static String API_KEY_TEST_HEADER = "X-API-KEY";
    public static Long NUMBER_OF_TEST_USERS = 10L;

    @ConfigProperty(name = "maestro.adminEmail")
    String mailTo;

    @Inject
    MockMailbox mailbox;

    @Test
    @Order(1)
    @RunOnVertxContext
    public void init(TransactionalUniAsserter asserter) {
        // truncate the user database
        asserter.execute(User::deleteAll);

        //create a bunch of users in database
        for (int i = 0; i < NUMBER_OF_TEST_USERS; i++) {
            User u = new User();
            u.publicKey = STR."TESTPUBLIC\{i}";
            u.money = 100;
            u.createdAt = new Timestamp(System.currentTimeMillis());
            if (i % 2 == 0) {
                u.lastLogin = new Timestamp(System.currentTimeMillis());
            }else {
                u.lastLogin = new Timestamp(System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000);
            }
            asserter.execute(() -> u.persist());
        }
        asserter.assertEquals(User::count, NUMBER_OF_TEST_USERS);
    }

    @BeforeEach
    public void clear() {
        mailbox.clear();
    }

    @Test
    @Order(2)
    public void shouldSendWeeklyReport() {
        given()
                .header(API_KEY_TEST_HEADER, API_KEY_TEST)
                .when().get("/sendWeeklyReport")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(containsString(STR."Number of new users since last week : \{NUMBER_OF_TEST_USERS}"))
                .body(containsString(STR."Number of users who have logged in since last week : \{NUMBER_OF_TEST_USERS / 2}"));

        //check that the mail was sent
        List<MailMessage> sent = mailbox.getMailMessagesSentTo(mailTo);
        Assert.assertTrue(sent.size() ==1);
        MailMessage actual = sent.getFirst();
        Assert.assertTrue(actual.getText().contains(STR."Number of new users since last week : \{NUMBER_OF_TEST_USERS}"));
        Assert.assertTrue("Weekly report".equals(actual.getSubject()));
        Assert.assertTrue(mailbox.getTotalMessagesSent() == 1);
    }
}
