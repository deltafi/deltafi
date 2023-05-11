package {{paramPackage}};

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class {{paramClassName}} extends ActionParameters {

    // sample marking a parameter as required, flows that use these parameters will be required to provide this value
    @JsonProperty(required = true)
    @JsonPropertyDescription("This description will be sent back to the core system and used when configuring a flow")
    public String sampleString;

    @JsonPropertyDescription("Sample number parameter")
    public int sampleNumber;

    @JsonPropertyDescription("Sample list parameter")
    public List<String> sampleList;

    @JsonPropertyDescription("Sample boolean parameter")
    public boolean sampleBoolean;

    @JsonPropertyDescription("Sample map parameter")
    public Map<String, String> sampleMap;

}
