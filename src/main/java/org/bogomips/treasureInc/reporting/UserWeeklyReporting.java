package org.bogomips.treasureInc.reporting;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.bogomips.treasureInc.user.User;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.sql.Timestamp;

@Path("reporting")
public class UserWeeklyReporting {
    @Inject
    Mailer mailer;

    @ConfigProperty(name = "maestro.adminEmail")
    String adminEmail;

    //send en email every week to admin with :
    //  -the number of new users since last week
    //  -the number of users who have logged in since last week
    @SuppressWarnings("unused")
    @Scheduled(cron = "0 0 0 ? * MON")
    public void scheduledSendWeeklyReport() {
        sendWeeklyReport();
    }

    @GET
    @Path("sendWeeklyReport")
    @Produces("text/plain")
    @ResponseStatus(200)
    public String sendWeeklyReport() {
        String textMail = buildWeeklyReport();
        mailer.send(Mail.withText(adminEmail,"Weekly report", textMail));
        return textMail;
    }

    protected String buildWeeklyReport(){
        var userLoggedInLastWeek = User.countByLastLoginGreaterThan(new Timestamp(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000));
        var userCreatedLastWeek = User.countByCreatedAtGreaterThan(new Timestamp(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000));

        return "Number of users logged in last week : " + userLoggedInLastWeek +
                "\nNumber of users created last week : " + userCreatedLastWeek;
    }
}
