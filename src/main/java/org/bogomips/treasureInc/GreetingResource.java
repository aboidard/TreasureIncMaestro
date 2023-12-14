package org.bogomips.treasureInc;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;


@Path("/users")
public class GreetingResource {

    @Inject
    EntityManager em;

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
}
