package org.bogomips.treasureInc.user;

import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.sql.Timestamp;
import java.util.List;


@Path("/users")
public class UserResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> users() {
        return User.listAll(Sort.by("id").descending());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{publicKey}")
    public User users(String publicKey) {
        return User.findByPublicKey(publicKey);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    @Transactional
    public User createUser(User user) {
        persistNewUser(user);
        return user;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{publicKey}")
    @Transactional
    public User updateUser(String publicKey, User user) {
        User u = User.findByPublicKey(publicKey);
        if(u == null) {
            throw new NotFoundException();
        }
        u.money = user.money;
        return u;
    }

    private static void persistNewUser(User user) {
        user.publicKey = User.generatePublicKey();
        user.privateKey = User.generatePrivateKey();
        user.money = User.DEFAULT_MONEY;
        user.createdAt = new Timestamp(System.currentTimeMillis());
        user.updatedAt = new Timestamp(System.currentTimeMillis());
        user.persist();
    }
}
