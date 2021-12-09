package org.deltafi.actionkit.service;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.config.ActionKitConfig;

import javax.enterprise.context.ApplicationScoped;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class HostnameService {

    private static final String DEFAULT_HOSTNAME = "UNKNOWN";
    private static final String HOSTNAME_ENV_VAR = "HOSTNAME";
    private static final String COMPUTERNAME_ENV_VAR = "COMPUTERNAME";

    private String hostname;

    public HostnameService(ActionKitConfig actionKitConfig) {
        if (actionKitConfig.hostname().isPresent()) {
            this.hostname = actionKitConfig.hostname().get();
        } else {
            this.hostname = detectHostname();
        }
    }

    public String getHostname() {
        return hostname;
    }

    private String detectHostname() {
        Map<String, String> env = System.getenv();

        if (env.containsKey(HOSTNAME_ENV_VAR)) {
            return env.get(HOSTNAME_ENV_VAR);
        }

        if (env.containsKey(COMPUTERNAME_ENV_VAR)) {
            return env.get(COMPUTERNAME_ENV_VAR);
        }

        return detectHostnameFromInet();
    }

    private String detectHostnameFromInet() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            return localhost.getHostName();
        } catch (UnknownHostException e) {
            log.error("Failed to determine hostname", e);
            return DEFAULT_HOSTNAME;
        }
    }
}
