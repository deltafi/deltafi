package org.deltafi.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    String url;
    String accessKey;
    String secretKey;
    long partSize;
}
