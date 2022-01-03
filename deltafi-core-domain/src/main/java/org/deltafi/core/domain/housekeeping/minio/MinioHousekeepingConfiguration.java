package org.deltafi.core.domain.housekeeping.minio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinioHousekeepingConfiguration {
    private int initialDelaySeconds;
    private int delaySeconds;
    private int objectMinimumAgeForRemovalSeconds;
}
