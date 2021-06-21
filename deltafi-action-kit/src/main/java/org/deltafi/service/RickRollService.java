package org.deltafi.service;

import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.event.Observes;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class RickRollService {

    // Guarantee instantiation if not injected...
    @SuppressWarnings("EmptyMethod")
    void startup(@Observes StartupEvent event) {}

    static RickRollService instance;

    static public RickRollService instance() { return instance; }

    RickRollService() {
        log.info(this.getClass().getSimpleName() + " instantiated");
        instance = this;
    }

    static final String[] song = {
            "Never gonna give you up",
            "Never gonna let you down",
            "Never gonna run around and desert you",
            "Never gonna make you cry",
            "Never gonna say goodbye",
            "Never gonna tell a lie and hurt you"
    };

    static int count = 0;
    static private String get() {
        return song[count++ % 6];
    }

    public String rickroll() {
        String rickroll = get();
        log.info(rickroll);
        return rickroll;
    }

}