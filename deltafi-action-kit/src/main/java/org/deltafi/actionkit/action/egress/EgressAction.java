/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialization class for EGRESS actions.
 * <p>
 * This abstract class provides the foundation for implementing egress actions that send data
 * to external systems. It handles the creation of EgressInput objects from DeltaFileMessage
 * instances and delegates the actual egress logic to implementing classes.
 * </p>
 *
 * @param <P> Parameter class for configuring the egress action
 */
public abstract class EgressAction<P extends ActionParameters> extends Action<EgressInput, P, EgressResultType> {
    

    /**
     * Constructs a new EgressAction with the specified description.
     *
     * @param description a human-readable description of this egress action
     */
    protected EgressAction(@NotNull String description) {
        this(ActionOptions.builder().description(description).build());
    }

    /**
     * Constructs a new EgressAction with the specified action options.
     *
     * @param actionOptions the configuration options for this action
     */
    protected EgressAction(ActionOptions actionOptions) {
        super(ActionType.EGRESS, actionOptions);
    }

    /**
     * Builds an EgressInput from the provided ActionContext and DeltaFileMessage.
     * <p>
     * This method validates the input parameters, creates ActionContent from the first content
     * in the message (or null if no content exists), and builds the EgressInput
     * with the content and metadata.
     * </p>
     *
     * @param context the action context containing content storage service
     * @param deltaFileMessage the message containing content and metadata to egress
     * @return EgressInput containing the content and metadata
     * @throws NullPointerException if context or deltaFileMessage is null
     */
    @Override
    protected EgressInput buildInput(@NotNull ActionContext context, @NotNull DeltaFileMessage deltaFileMessage) {
        ActionContent content = createActionContent(deltaFileMessage.getContentList(), context);
        Map<String, String> metadata = getMetadataOrDefault(deltaFileMessage.getMetadata());

        return EgressInput.builder()
                .content(content)
                .metadata(metadata)
                .build();
    }
    
    /**
     * Creates ActionContent from the content list, or null if the list is null or empty.
     *
     * @param contentList the list of content objects from the DeltaFileMessage
     * @param context the action context containing the content storage service
     * @return ActionContent representing the content to egress, or null if no content available
     */
    private ActionContent createActionContent(List<Content> contentList, ActionContext context) {
        if (contentList == null || contentList.isEmpty()) {
            return null;
        }
        return new ActionContent(contentList.getFirst(), context.getContentStorageService());
    }
    
    
    /**
     * Returns the metadata map or an empty map if metadata is null.
     *
     * @param metadata the metadata map from the DeltaFileMessage
     * @return the metadata map, or empty map if null
     */
    private Map<String, String> getMetadataOrDefault(Map<String, String> metadata) {
        return metadata != null ? metadata : new HashMap<>();
    }

    /**
     * Executes the egress action by delegating to the implementing class's egress method.
     *
     * @param context the action configuration context object for this action execution
     * @param input the egress input containing content and metadata
     * @param params the parameter class that configures the behavior of this action execution
     * @return a result object containing results for the action execution
     */
    @Override
    protected final EgressResultType execute(@NotNull ActionContext context, @NotNull EgressInput input, @NotNull P params) {
        return egress(context, params, input);
    }

    /**
     * Implements the egress execution function of an egress action.
     * <p>
     * This method must be implemented by concrete egress action classes to define the specific
     * logic for sending data to external systems. The method receives the action context,
     * configuration parameters, and the egress input containing content and metadata.
     * </p>
     *
     * @param context the action configuration context object for this action execution
     * @param params the parameter class that configures the behavior of this action execution
     * @param egressInput action input from the DeltaFile containing content and metadata
     * @return a result object containing results for the action execution. The result can be an
     *         ErrorResult, a FilterResult, or an EgressResult
     * @see EgressResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract EgressResultType egress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput egressInput);
}
