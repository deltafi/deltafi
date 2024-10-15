package {{package}};

import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import {{paramPackage}}.{{paramClassName}};
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class {{className}} extends EgressAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public EgressResultType egress(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull EgressInput egressInput) {
        byte[] formattedData = egressInput.getContent().loadBytes();

        // TODO: add logic to egress the data

        return new EgressResult(context, egressInput.getContent().getSize());
    }
}
