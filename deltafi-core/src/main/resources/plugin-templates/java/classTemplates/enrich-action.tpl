package {{package}};

import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichInput;
import org.deltafi.actionkit.action.enrich.EnrichResultType;
import {{paramPackage}}.{{paramClassName}};
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class {{className}} extends EnrichAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public EnrichResultType enrich(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull EnrichInput enrichInput) {
        return null;
    }

    @Override
    public List<String> getRequiresDomains() {
        return null;
    }
}
