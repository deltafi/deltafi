package {{package}};

import org.deltafi.actionkit.action.domain.DomainAction;
import org.deltafi.actionkit.action.domain.DomainInput;
import org.deltafi.actionkit.action.domain.DomainResult;
import org.deltafi.actionkit.action.domain.DomainResultType;
import org.deltafi.common.types.Domain;
import {{paramPackage}}.{{paramClassName}};
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class {{className}} extends DomainAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public DomainResultType extractAndValidate(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull DomainInput domainInput) {
        DomainResult domainResult = new DomainResult(context);

        Map<String, String> extractedMetadata = new HashMap<>();

        domainInput.getDomains().values().stream()
                .map(this::processDomain)
                .forEach(extractedMetadata::putAll);

        domainResult.addIndexedMetadata(extractedMetadata);
        return domainResult;
    }

    Map<String, String> processDomain(Domain domain) {
        // TODO: add logic to validate the domain and extract useful information
        return Map.of();
    }

    @Override
    public List<String> getRequiresDomains() {
        return Constants.DOMAINS;
    }
}
