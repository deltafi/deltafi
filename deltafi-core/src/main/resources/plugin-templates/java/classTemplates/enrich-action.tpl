package {{package}};

import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichInput;
import org.deltafi.actionkit.action.enrich.EnrichResult;
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
        EnrichResult result = new EnrichResult(context);

        // TODO - add logic to fill in the enrich result

        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return Constants.DOMAINS;
    }
}
