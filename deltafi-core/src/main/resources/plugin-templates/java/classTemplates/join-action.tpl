package {{package}};

import org.deltafi.actionkit.action.join.JoinAction;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResultType;
import {{paramPackage}}.{{paramClassName}};
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.DeltaFile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class {{className}} extends JoinAction<{{paramClassName}}> {

    public {{className}}() {
        super("{{description}}");
    }

    @Override
    protected LoadResultType join(DeltaFile deltaFile, List<DeltaFile> joinedDeltaFiles, ActionContext context,
            {{paramClassName}} params) {
        return null;
    }
}
