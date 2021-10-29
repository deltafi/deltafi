package org.deltafi.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class DeltafiConfigServer {

    public static void main(String[] arguments) {
        SpringApplication.run(DeltafiConfigServer.class, arguments);
    }
}
