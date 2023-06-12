package {{package}};

import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.validate.ValidateAction;
import org.deltafi.actionkit.action.validate.ValidateInput;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.actionkit.action.validate.ValidateResultType;
import org.deltafi.common.types.ActionContext;
import {{paramPackage}}.{{paramClassName}};
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class {{className}} extends ValidateAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public ValidateResultType validate(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull ValidateInput validateInput) {
        byte[] formattedData = validateInput.content().loadBytes();

        // TODO: add logic to validate the formatted data
        boolean valid = true;
        if (!valid) {
            return new ErrorResult(context, "Invalid formatted data");
        }

        return new ValidateResult(context);
    }
}
