package {{paramPackage}};

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@EqualsAndHashCode(callSuper = true)
public class {{paramClassName}} extends ActionParameters {

    // sample marking a parameter as required, flows that use these parameters will be required to provide this value
    @JsonProperty(required = true)
    @JsonPropertyDescription("This description will be sent back to the core system and used when configuring a flow")
    public String paramName;

}
