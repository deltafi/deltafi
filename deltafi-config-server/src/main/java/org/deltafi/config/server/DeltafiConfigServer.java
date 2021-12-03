package org.deltafi.config.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.config.ConfigServerAutoConfiguration;

@EnableConfigServer
@SpringBootApplication(exclude = {ConfigServerAutoConfiguration.class})
public class DeltafiConfigServer {

    public static void main(String[] arguments) {
        SpringApplication.run(DeltafiConfigServer.class, arguments);
    }
}
