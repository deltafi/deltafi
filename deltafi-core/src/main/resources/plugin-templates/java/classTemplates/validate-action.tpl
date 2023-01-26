package {{package}};

import {{paramPackage}}.{{paramClassName}};
import org.deltafi.actionkit.action.validate.ValidateAction;
import org.deltafi.actionkit.action.validate.ValidateInput;
import org.deltafi.actionkit.action.validate.ValidateResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class {{className}} extends ValidateAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public ValidateResultType validate(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull ValidateInput validateInput) {
        return null;
    }
}
