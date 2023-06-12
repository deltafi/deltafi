package {{package}};

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.LoadResultType;
import {{paramPackage}}.{{paramClassName}};
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;

@Component
public class {{className}} extends LoadAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    public LoadResultType load(@NotNull ActionContext context, @NotNull {{paramClassName}} params, @NotNull LoadInput loadInput) {
        LoadResult loadResult = new LoadResult(context, new ArrayList<>());
        ActionContent actionContent = loadInput.content(0);

        byte[] contentBytes = actionContent.loadBytes();
        loadResult.saveContent(contentBytes, actionContent.getName(), actionContent.getName());
        Constants.DOMAINS.forEach(domain -> loadResult.addDomain(domain, "", MediaType.TEXT_PLAIN));

        return loadResult;
    }
}
