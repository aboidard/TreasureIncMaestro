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
@WithTransaction
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
        return buildWeeklyReport().onItem().invoke(text ->
        mailer.send(Mail.withText(adminEmail,"Weekly report", text)));
    }

    protected Uni<String> buildWeeklyReport(){
        Uni<Long> loginText = User.countByLastLoginGreaterThan(new Timestamp(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
                .onItem().ifNull().failWith(NotFoundException::new);
        Uni<Long> createdText = User.countByCreatedAtGreaterThan(new Timestamp(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
                .onItem().ifNull().failWith(NotFoundException::new);


//        return Uni.join().all().usingConcurrencyOf(1).unis(loginText, createdText).combinedWith((created, logged)  -> "Weekly report : \n" +
//                "Number of users logged in last week : " + logged + "\n" +
//                "Number of users created last week : " + created + "\n");
        return Uni.combine().all().unis(loginText, createdText).combinedWith((created, logged)  -> "Weekly report : \n" +
                                            "Number of users logged in last week : " + logged + "\n" +
                                            "Number of users created last week : " + created + "\n");
    }
}
