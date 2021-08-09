package org.deltafi.dgs.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.dgs.generated.types.ErrorDomain;
import org.deltafi.dgs.services.ErrorService;

import java.util.ArrayList;
import java.util.List;

@DgsComponent
public class ErrorDatafetcher {

    final ErrorService errorService;

    public ErrorDatafetcher(ErrorService errorService) {
        this.errorService = errorService;
    }

    @DgsQuery
    @SuppressWarnings("unused")
    public ErrorDomain getError(String did) {
        ErrorDomain errorDomain = errorService.getError(did);

        if (errorDomain == null) {
            throw new DgsEntityNotFoundException("ErrorDomain " + did + " not found.");
        }

        return errorDomain;
    }

    @DgsQuery
    @SuppressWarnings("unused")
    public List<ErrorDomain> getErrorsFor(String originatorDid) {
        List<ErrorDomain> errors = errorService.getErrorsFor(originatorDid);

        if (errors == null) {
            return new ArrayList<>();
        }

        return errors;
    }

}