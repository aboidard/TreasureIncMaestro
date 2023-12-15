package org.bogomips.treasureInc.security;

import io.quarkus.security.UnauthorizedException;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.bogomips.treasureInc.MaestroConfig;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

public class ApiFilter {

    @Inject
    MaestroConfig maestroConfig;

    @ServerRequestFilter(preMatching = true)
    public void filterApiKey(ContainerRequestContext containerRequestContext){
        if(!containerRequestContext.getUriInfo().getPath().contains("/api")) {
            return;
        }
        String apiKey = containerRequestContext.getHeaderString("X-API-KEY");
        if(apiKey == null || !apiKey.equals(maestroConfig.apiKey())) {
            throw new UnauthorizedException("Unauthorized");
        }
    }
}