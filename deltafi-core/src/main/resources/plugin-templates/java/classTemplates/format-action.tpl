package {{package}};

import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatInput;
import org.deltafi.actionkit.action.format.FormatResultType;
import {{paramPackage}}.{{paramClassName}};
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class {{className}} extends FormatAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public FormatResultType format(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull FormatInput formatInput) {
        return null;
    }

    @Override
    public List<String> getRequiresDomains() {
        return null;
    }
}
