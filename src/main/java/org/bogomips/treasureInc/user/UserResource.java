package org.bogomips.treasureInc.user;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;


@Path("/users")
public class UserResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> users() {
        return User.listAll();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{publicKey}")
    public User users(String publicKey) {
        return User.findByPublicKey(publicKey);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public User createUser(User user) {
        user.persist();
        return user;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{publicKey}")
    public User updateUser(String publicKey, User user) {
        User u = User.findByPublicKey(publicKey);
        u.setMoney(user.getMoney());
        u.persist();
        return u;
    }
}
