package {{package}};

import org.deltafi.actionkit.action.domain.DomainAction;
import org.deltafi.actionkit.action.domain.DomainInput;
import org.deltafi.actionkit.action.domain.DomainResultType;
import {{paramPackage}}.{{paramClassName}};
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class {{className}} extends DomainAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public DomainResultType extractAndValidate(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull DomainInput domainInput) {
        return null;
    }

    @Override
    public List<String> getRequiresDomains() {
        return null;
    }
}
