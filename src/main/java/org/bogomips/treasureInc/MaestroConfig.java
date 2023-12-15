package org.bogomips.treasureInc;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "maestro")
public interface MaestroConfig {
    String apiKey();
}