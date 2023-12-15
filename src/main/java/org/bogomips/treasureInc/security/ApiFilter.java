package org.bogomips.treasureInc.security;

import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

@SuppressWarnings("unused")
public class ApiFilter {

    @ConfigProperty(name = "maestro.apikey")
    String maestroApiKey;
    @SuppressWarnings("unused")
    @ServerRequestFilter(preMatching = true)
    public void filterApiKey(ContainerRequestContext containerRequestContext){
        String apiKeyProvided = containerRequestContext.getHeaderString("X-API-KEY");
        if(!maestroApiKey.equals(apiKeyProvided)) {
            throw new UnauthorizedException("Unauthorized");
        }
    }
}