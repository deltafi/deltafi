package {{package}};

import {{paramPackage}}.{{paramClassName}};
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class {{className}} extends TransformAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull TransformInput transformInput) {
        return null;
    }
}
