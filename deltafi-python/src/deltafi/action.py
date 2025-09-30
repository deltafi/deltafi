#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

from abc import ABC, abstractmethod
from typing import Any, List

from pydantic import BaseModel

from deltafi.actiontype import ActionType
from deltafi.domain import DeltaFileMessage
from deltafi.genericmodel import GenericModel
from deltafi.input import EgressInput, TransformInput
from deltafi.result import *


class Join(ABC):
    def join(self, transform_inputs: List[TransformInput]):
        all_content = []
        all_metadata = {}
        for transform_input in transform_inputs:
            all_content += transform_input.content
            all_metadata.update(transform_input.metadata)
        return TransformInput(content=all_content, metadata=all_metadata)


class ContentSpec:
    name: str
    media_type: str
    description: str

    def __init__(self, name: str = None, media_type: str = None, description: str = None):
        self.name = name
        self.media_type = media_type
        self.description = description

    def json(self):
        json_dictionary = {}
        if self.name is not None:
            json_dictionary['name'] = self.name
        if self.media_type is not None:
            json_dictionary['mediaType'] = self.media_type
        if self.description is not None:
            json_dictionary['description'] = self.description
        return json_dictionary


class KeyedDescription:
    key: str
    description: str

    def __init__(self, key: str, description: str):
        self.key = key
        self.description = description

    def json(self):
        json_dictionary = {}
        if self.key is not None:
            json_dictionary['key'] = self.key
            json_dictionary['description'] = self.description
        return json_dictionary


class InputSpec:
    content_summary: str
    content_specs: List[ContentSpec]
    metadata_summary: str
    metadata_descriptions: List[KeyedDescription]

    def __init__(self, content_summary: str = None, content_specs: List[ContentSpec] = None,
                 metadata_summary: str = None, metadata_descriptions: List[KeyedDescription] = None):
        self.content_summary = content_summary
        self.content_specs = content_specs
        self.metadata_summary = metadata_summary
        self.metadata_descriptions = metadata_descriptions

    def json(self):
        json_dictionary = {}
        if self.content_summary is not None:
            json_dictionary['contentSummary'] = self.content_summary
        if self.content_specs is not None:
            json_dictionary['contentSpecs'] = [cs.json() for cs in self.content_specs]
        if self.metadata_summary is not None:
            json_dictionary['metadataSummary'] = self.metadata_summary
        if self.metadata_descriptions is not None:
            json_dictionary['metadataDescriptions'] = [md.json() for md in self.metadata_descriptions]
        return json_dictionary


class OutputSpec:
    content_summary: str
    content_specs: List[ContentSpec]
    metadata_summary: str
    metadata_descriptions: List[KeyedDescription]
    passthrough: bool
    annotations_summary: str
    annotation_descriptions: List[KeyedDescription]

    def __init__(self, content_summary: str = None, content_specs: List[ContentSpec] = None,
                 metadata_summary: str = None, metadata_descriptions: List[KeyedDescription] = None,
                 passthrough: bool = False, annotations_summary: str = None,
                 annotation_descriptions: List[KeyedDescription] = None):
        self.content_summary = content_summary
        self.content_specs = content_specs
        self.metadata_summary = metadata_summary
        self.metadata_descriptions = metadata_descriptions
        self.passthrough = passthrough
        self.annotations_summary = annotations_summary
        self.annotation_descriptions = annotation_descriptions

    def json(self):
        json_dictionary = {}
        if self.content_summary is not None:
            json_dictionary['contentSummary'] = self.content_summary
        if self.content_specs is not None:
            json_dictionary['contentSpecs'] = [cs.json() for cs in self.content_specs]
        if self.metadata_summary is not None:
            json_dictionary['metadataSummary'] = self.metadata_summary
        if self.metadata_descriptions is not None:
            json_dictionary['metadataDescriptions'] = [md.json() for md in self.metadata_descriptions]
        if self.passthrough is not None:
            json_dictionary['passthrough'] = self.passthrough
        if self.annotations_summary is not None:
            json_dictionary['annotationsSummary'] = self.annotations_summary
        if self.annotation_descriptions is not None:
            json_dictionary['annotationDescriptions'] = [ad.json() for ad in self.annotation_descriptions]
        return json_dictionary


class DescriptionWithConditions:
    description: str
    conditions: List[str]

    def __init__(self, description: str = None, conditions: List[str] = None):
        self.description = description
        self.conditions = conditions

    def json(self):
        json_dictionary = {}
        if self.description is not None:
            json_dictionary['description'] = self.description
        if self.conditions is not None:
            json_dictionary['conditions'] = [c for c in self.conditions]
        return json_dictionary


class ActionOptions:
    description: str
    input_spec: InputSpec
    output_spec: OutputSpec
    filters: List[DescriptionWithConditions] = None
    errors: List[DescriptionWithConditions] = None
    notes: List[str]
    details: str

    def __init__(self, description: str = None, input_spec: InputSpec = None, output_spec: OutputSpec = None,
                 filters: List = None, errors: List = None, notes: List[str] = None, details: str = None):
        self.description = description
        self.input_spec = input_spec
        self.output_spec = output_spec
        if filters is not None:
            self.filters = []
            for f in filters:
                if isinstance(f, DescriptionWithConditions):
                    self.filters.append(f)
                else:
                    self.filters.append(DescriptionWithConditions(description=f))
        if errors is not None:
            self.errors = []
            for e in errors:
                if isinstance(e, DescriptionWithConditions):
                    self.errors.append(e)
                else:
                    self.errors.append(DescriptionWithConditions(description=e))
        self.notes = notes
        self.details = details

    def json(self):
        json_dictionary = {}
        if self.description is not None:
            json_dictionary['description'] = self.description
        if self.input_spec is not None:
            json_dictionary['inputSpec'] = self.input_spec.json()
        if self.output_spec is not None:
            json_dictionary['outputSpec'] = self.output_spec.json()
        if self.filters is not None:
            json_dictionary['filters'] = [f.json() for f in self.filters]
        if self.errors is not None:
            json_dictionary['errors'] = [e.json() for e in self.errors]
        if self.notes is not None:
            json_dictionary['notes'] = [n for n in self.notes]
        if self.details is not None:
            json_dictionary['details'] = self.details
        return json_dictionary


class Action(ABC):
    def __init__(self, action_type: ActionType, description: str, valid_result_types: tuple,
                 action_options: ActionOptions = None):
        self.action_type = action_type
        if action_options is None:
            self.action_options = ActionOptions(description=description)
        else:
            self.action_options = action_options
        self.valid_result_types = valid_result_types

    @abstractmethod
    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        pass

    def execute_join_action(self, event):
        raise RuntimeError(f"Join is not supported for {self.__class__.__name__}")

    @abstractmethod
    def execute(self, context: Context, action_input: Any, params: BaseModel):
        pass

    def execute_action(self, event):
        if event.delta_file_messages is None or not len(event.delta_file_messages):
            raise RuntimeError(f"Received event with no delta file messages for did {event.context.did}")
        if event.context.join is not None:
            result = self.execute_join_action(event)
        else:
            result = self.execute(
                event.context,
                self.build_input(event.context, event.delta_file_messages[0]),
                self.param_class().model_validate(event.params))

        self.validate_type(result)
        return result

    @staticmethod
    def param_class():
        """Factory method to create and return an empty GenericModel instance.

        All action parameter classes must inherit pydantic.BaseModel.
        Use of complex types in custom action parameter classes must specify
        the internal types when defined. E.g., dict[str, str], or List[str]

        Returns
        -------
        GenericModel
            an empty GenericModel instance
        """
        return GenericModel

    def validate_type(self, result):
        if not isinstance(result, self.valid_result_types):
            raise ValueError(f"{self.__class__.__name__} must return one of "
                             f"{[result_type.__name__ for result_type in self.valid_result_types]} "
                             f"but a {result.__class__.__name__} was returned")


class EgressAction(Action, ABC):
    def __init__(self, description: str, action_options: ActionOptions = None):
        super().__init__(ActionType.EGRESS, description, (EgressResult, ErrorResult, FilterResult), action_options)

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        content = None
        if delta_file_message.content_list is not None and len(delta_file_message.content_list) > 0:
            content = delta_file_message.content_list[0]
        return EgressInput(content=content, metadata=delta_file_message.metadata)

    @abstractmethod
    def egress(self, context: Context, params: BaseModel, egress_input: EgressInput):
        pass

    def execute(self, context: Context, egress_input: EgressInput, params: BaseModel):
        return self.egress(context, params, egress_input)


class TimedIngressAction(Action, ABC):
    def __init__(self, description: str, action_options: ActionOptions = None):
        super().__init__(ActionType.TIMED_INGRESS, description, (IngressResult, ErrorResult), action_options)

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return None

    @abstractmethod
    def ingress(self, context: Context, params: BaseModel):
        pass

    def execute(self, context: Context, input_placeholder: Any, params: BaseModel):
        return self.ingress(context, params)


class TransformAction(Action, ABC):
    def __init__(self, description: str, action_options: ActionOptions = None):
        super().__init__(ActionType.TRANSFORM, description,
                         (TransformResult, TransformResults, ErrorResult, FilterResult), action_options)

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return TransformInput(content=delta_file_message.content_list, metadata=delta_file_message.metadata)

    def execute_join_action(self, event):
        if isinstance(self, Join):
            return self.execute(
                event.context,
                self.join([self.build_input(event.context, delta_file_message)
                           for delta_file_message in event.delta_file_messages]),
                self.param_class().model_validate(event.params))
        else:
            super().execute_join_action(event)

    @abstractmethod
    def transform(self, context: Context, params: BaseModel, transform_input: TransformInput):
        pass

    def execute(self, context: Context, transform_input: TransformInput, params: BaseModel):
        return self.transform(context, params, transform_input)
