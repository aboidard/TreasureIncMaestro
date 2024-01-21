package org.bogomips.treasureInc.reporting;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.bogomips.treasureInc.user.User;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.sql.Timestamp;

@Path("reporting")
public class UserWeeklyReporting {
    @Inject
    ReactiveMailer mailer;

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
    public Uni<String> sendWeeklyReport() {
        return buildWeeklyReport().map(reportText -> {mailer.send(Mail.withText(adminEmail, "Weekly report", reportText)).subscribe().with(
                success -> System.out.println("Mail sent!"),
                failure -> System.out.println("Failed to send mail"));
            return reportText;
        });
    }

    @WithTransaction
    protected Uni<String> buildWeeklyReport() {
        return User.countByLastLoginGreaterThan(new Timestamp(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
                .onItem().ifNull().failWith(NotFoundException::new)
                .flatMap(textLogin -> User.countByCreatedAtGreaterThan(new Timestamp(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
                        .onItem().ifNull().failWith(NotFoundException::new).map(textCreated -> new Long[] {textLogin, textCreated}))
                .map(texts -> "Weekly report : \n" +
                        "Number of new users since last week : " + texts[1] + "\n" +
                        "Number of users who have logged in since last week : " + texts[0] + "\n");
    }
}
