package org.deltafi.dgs;

import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class Util {
    public static DeltaFile emptyDeltaFile(String did, String flow) {
        OffsetDateTime now = OffsetDateTime.now();
        return DeltaFile.newBuilder()
                .did(did)
                .stage(DeltaFileStage.TRANSFORM.name())
                .actions(new ArrayList<>())
                .sourceInfo(SourceInfo.newBuilder().flow(flow).build())
                .protocolStack(new ArrayList<>())
                .domains(DeltaFiDomains.newBuilder().did(did).domainTypes(new ArrayList<>()).build())
                .enrichment(DeltaFiEnrichments.newBuilder().did(did).enrichmentTypes(new ArrayList<>()).build())
                .formattedData(new ArrayList<>())
                .created(now)
                .modified(now)
                .build();
    }

    public static boolean equalIgnoringDates(DeltaFile d1, DeltaFile d2) {
        return java.util.Objects.equals(d1.getDid(), d2.getDid()) &&
                java.util.Objects.equals(d1.getStage(), d2.getStage()) &&
                actionsEqualIgnoringDates(d1.getActions(), d2.getActions()) &&
                java.util.Objects.equals(d1.getSourceInfo(), d2.getSourceInfo()) &&
                java.util.Objects.equals(d1.getProtocolStack(), d2.getProtocolStack()) &&
                java.util.Objects.equals(d1.getDomains(), d2.getDomains()) &&
                java.util.Objects.equals(d1.getEnrichment(), d2.getEnrichment()) &&
                java.util.Objects.equals(d1.getFormattedData(), d2.getFormattedData());
    }

    public static boolean actionsEqualIgnoringDates(List<Action> a1, List<Action> a2) {
        if (a1 == null && a2 == null) {
            return true;
        } else if (a1 == null || a2 == null) {
            return false;
        } else {
            for (int i=0; i<a1.size(); i++) {
                if (!actionEqualIgnoringDates(a1.get(i), a2.get(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean actionEqualIgnoringDates(Action a1, Action a2) {
        if (a1 == null && a2 == null) {
            return true;
        } else if (a1 == null || a2 == null) {
            return false;
        } else {
            return java.util.Objects.equals(a1.getName(), a2.getName()) &&
                    java.util.Objects.equals(a1.getState(), a2.getState()) &&
                    java.util.Objects.equals(a1.getErrorContext(), a2.getErrorContext()) &&
                    java.util.Objects.equals(a1.getErrorCause(), a2.getErrorCause());
        }
    }
}