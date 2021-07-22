package org.deltafi.dgs.api.types;

import org.deltafi.dgs.generated.types.DeltaFile;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class ErrorDomain extends org.deltafi.dgs.generated.types.ErrorDomain {

    @Version
    private long version;

    @Id
    @Override
    public String getDid() {
        return super.getDid();
    }

    public long getVersion() { return version; }

    public void setVersion(long version) { this.version = version; }

    public static Builder newBuilder() { return new Builder(); }

    public static class Builder extends org.deltafi.dgs.generated.types.ErrorDomain.Builder {

        private String did;
        private DeltaFile originator;
        private String originatorDid;
        private String fromAction;
        private String cause;
        private String context;

        public Builder did(String did) {
            this.did = did;
            return this;
        }

        public Builder originator(DeltaFile originator) {
            this.originator = originator;
            return this;
        }

        public Builder originatorDid(String originatorDid) {
            this.originatorDid = originatorDid;
            return this;
        }

        public Builder fromAction(String fromAction) {
            this.fromAction = fromAction;
            return this;
        }

        public Builder cause(String cause) {
            this.cause = cause;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public ErrorDomain build() {
            ErrorDomain result = new ErrorDomain();
            result.setDid(this.did);
            result.setOriginator(this.originator);
            result.setOriginatorDid(this.originatorDid);
            result.setFromAction(this.fromAction);
            result.setCause(this.cause);
            result.setContext(this.context);
            return result;

        }
    }
}