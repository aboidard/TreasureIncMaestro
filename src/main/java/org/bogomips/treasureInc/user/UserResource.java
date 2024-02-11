package org.bogomips.treasureInc.user;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.sql.Timestamp;
import java.util.List;


@Path("/users")
@WithTransaction
public class UserResource {

    private static final Logger LOG = Logger.getLogger(UserResource.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<User>> users() {
        LOG.debug("Getting all users");
        return User.listAll(Sort.by("id").descending());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{publicKey}")
    public Uni<User> users(String publicKey) {
        LOG.debug(STR."Getting user with public key : \{publicKey}");
        return User.findByPublicKey(publicKey).onItem().ifNull().failWith(NotFoundException::new);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<User> createUser() {
        LOG.debug("Creating new user");
        return persistNewUser().onItem().ifNull().failWith(NotFoundException::new);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{publicKey}")
    public Uni<User> updateUser(String publicKey, User user) {
        LOG.debug(STR."Updating user with public key : \{publicKey}");
        return User.findByPublicKey(publicKey)
                .onItem()
                .ifNull()
                .failWith(NotFoundException::new)
                .invoke(foundUser -> {
                    foundUser.money = user.money;
                    foundUser.updatedAt = new Timestamp(System.currentTimeMillis());
                });
    }

    private static Uni<User> persistNewUser() {
        var user = new User();
        user.publicKey = User.generatePublicKey();
        user.privateKey = User.generatePrivateKey();
        user.money = User.DEFAULT_MONEY;
        user.createdAt = new Timestamp(System.currentTimeMillis());
        user.updatedAt = new Timestamp(System.currentTimeMillis());
        user.lastLogin = new Timestamp(System.currentTimeMillis());

        LOG.info(STR."New user created : \{user.publicKey}");
        return user.persist();
    }
}
