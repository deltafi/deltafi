package org.deltafi.core.domain.housekeeping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.core.domain.housekeeping.minio.MinioHousekeepingConfiguration;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HousekeepingConfiguration {
    private MinioHousekeepingConfiguration minio = new MinioHousekeepingConfiguration();
}