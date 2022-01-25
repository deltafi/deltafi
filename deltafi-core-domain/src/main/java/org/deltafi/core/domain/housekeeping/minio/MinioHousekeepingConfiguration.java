package org.deltafi.core.domain.housekeeping.minio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinioHousekeepingConfiguration {
    private int initialDelaySeconds;
    private int delaySeconds;
    private int objectMinimumAgeForRemovalSeconds;
}
