package org.bogomips.treasureInc.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class DataHealthCheck implements HealthCheck {

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("treasure-inc")
                .up()
                .withData("version", version)
                .build();
    }
}
