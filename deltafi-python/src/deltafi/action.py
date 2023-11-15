#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

from deltafi.actiontype import ActionType
from deltafi.genericmodel import GenericModel
from deltafi.domain import Context, DeltaFileMessage
from deltafi.input import DomainInput, EgressInput, EnrichInput, FormatInput, LoadInput, TransformInput, ValidateInput
from deltafi.result import *
from pydantic import BaseModel


class Action(ABC):
    def __init__(self, action_type: ActionType, description: str, requires_domains: List[str],
                 requires_enrichments: List[str], valid_result_types: tuple):
        self.action_type = action_type
        self.description = description
        self.requires_domains = requires_domains
        self.requires_enrichments = requires_enrichments
        self.valid_result_types = valid_result_types
        self.action_execution = None

    @abstractmethod
    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        pass

    def collect(self, action_inputs: List[Any]):
        raise RuntimeError(f"Collect is not supported for {self.__class__.__name__}")

    @abstractmethod
    def execute(self, context: Context, action_input: Any, params: BaseModel):
        pass

    def execute_action(self, event):
        if event.delta_file_messages is None or not len(event.delta_file_messages):
            raise RuntimeError(f"Received event with no delta file messages for did {event.context.did}")

        if event.context.collect is not None:
            result = self.execute(event.context, self.collect([self.build_input(event.context, delta_file_message)
                                                               for delta_file_message in event.delta_file_messages]),
                                  self.param_class().model_validate(event.params))
        else:
            result = self.execute(event.context, self.build_input(event.context, event.delta_file_messages[0]),
                                  self.param_class().model_validate(event.params))

        self.validate_type(result)
        return result

    @staticmethod
    def param_class( ):
        """Factory method to create and return an empty GenericModel instance.

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


class DomainAction(Action, ABC):
    def __init__(self, description: str, requires_domains: List[str]):
        super().__init__(ActionType.DOMAIN, description, requires_domains, [], (DomainResult, ErrorResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return DomainInput(content=delta_file_message.content_list, metadata=delta_file_message.metadata,
                           domains={domain.name: domain for domain in delta_file_message.domains})

    @abstractmethod
    def domain(self, context: Context, params: BaseModel, domain_input: DomainInput):
        pass

    def execute(self, context: Context, domain_input: DomainInput, params: BaseModel):
        return self.domain(context, params, domain_input)


class EgressAction(Action, ABC):
    def __init__(self, description: str):
        super().__init__(ActionType.EGRESS, description, [], [], (EgressResult, ErrorResult, FilterResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return EgressInput(content=delta_file_message.content_list[0], metadata=delta_file_message.metadata)

    @abstractmethod
    def egress(self, context: Context, params: BaseModel, egress_input: EgressInput):
        pass

    def execute(self, context: Context, egress_input: EgressInput, params: BaseModel):
        return self.egress(context, params, egress_input)


class EnrichAction(Action, ABC):
    def __init__(self, description: str, requires_domains: List[str], requires_enrichments: List[str]):
        super().__init__(ActionType.ENRICH, description, requires_domains, requires_enrichments,
                         (EnrichResult, ErrorResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return EnrichInput(content=delta_file_message.content_list, metadata=delta_file_message.metadata,
                           domains={domain.name: domain for domain in delta_file_message.domains},
                           enrichments={domain.name: domain for domain in delta_file_message.enrichments})

    @abstractmethod
    def enrich(self, context: Context, params: BaseModel, enrich_input: EnrichInput):
        pass

    def execute(self, context: Context, enrich_input: EnrichInput, params: BaseModel):
        return self.enrich(context, params, enrich_input)


class FormatAction(Action, ABC):
    def __init__(self, description: str, requires_domains: List[str], requires_enrichments: List[str]):
        super().__init__(ActionType.FORMAT, description, requires_domains, requires_enrichments,
                         (FormatResult, FormatManyResult, ErrorResult, FilterResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return FormatInput(content=delta_file_message.content_list, metadata=delta_file_message.metadata,
                           domains={domain.name: domain for domain in delta_file_message.domains},
                           enrichments={domain.name: domain for domain in delta_file_message.enrichments})

    def collect(self, format_inputs: List[FormatInput]):
        all_content = []
        all_metadata = {}
        all_domains = {}
        all_enrichments = {}
        for format_input in format_inputs:
            all_content += format_input.content
            all_metadata.update(format_input.metadata)
            all_domains.update(format_input.domains)
            all_enrichments.update(format_input.enrichments)
        return FormatInput(content=all_content, metadata=all_metadata, domains=all_domains, enrichments=all_enrichments)

    @abstractmethod
    def format(self, context: Context, params: BaseModel, format_input: FormatInput):
        pass

    def execute(self, context: Context, format_input: FormatInput, params: BaseModel):
        return self.format(context, params, format_input)


class LoadAction(Action, ABC):
    def __init__(self, description: str):
        super().__init__(ActionType.LOAD, description, [], [],
                         (LoadResult, LoadManyResult, ErrorResult, FilterResult, ReinjectResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return LoadInput(content=delta_file_message.content_list, metadata=delta_file_message.metadata)

    def collect(self, load_inputs: List[LoadInput]):
        all_content = []
        all_metadata = {}
        for load_input in load_inputs:
            all_content += load_input.content
            all_metadata.update(load_input.metadata)
        return LoadInput(content=all_content, metadata=all_metadata)

    @abstractmethod
    def load(self, context: Context, params: BaseModel, load_input: LoadInput):
        pass

    def execute(self, context: Context, load_input: LoadInput, params: BaseModel):
        return self.load(context, params, load_input)


class TimedIngressAction(Action, ABC):
    def __init__(self, description: str):
        super().__init__(ActionType.TIMED_INGRESS, description, [], [], (IngressResult, ErrorResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return None

    @abstractmethod
    def ingress(self, context: Context, params: BaseModel):
        pass

    def execute(self, context: Context, input_placeholder: Any, params: BaseModel):
        return self.ingress(context, params)


class TransformAction(Action, ABC):
    def __init__(self, description: str):
        super().__init__(ActionType.TRANSFORM, description, [], [], (TransformResult, ErrorResult, FilterResult, ReinjectResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return TransformInput(content=delta_file_message.content_list, metadata=delta_file_message.metadata)

    def collect(self, transform_inputs: List[TransformInput]):
        all_content = []
        all_metadata = {}
        for transform_input in transform_inputs:
            all_content += transform_input.content
            all_metadata.update(transform_input.metadata)
        return TransformInput(content=all_content, metadata=all_metadata)

    @abstractmethod
    def transform(self, context: Context, params: BaseModel, transform_input: TransformInput):
        pass

    def execute(self, context: Context, transform_input: TransformInput, params: BaseModel):
        return self.transform(context, params, transform_input)


class ValidateAction(Action, ABC):
    def __init__(self, description: str):
        super().__init__(ActionType.VALIDATE, description, [], [], (ValidateResult, ErrorResult, FilterResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return ValidateInput(content=delta_file_message.content_list[0], metadata=delta_file_message.metadata)

    @abstractmethod
    def validate(self, context: Context, params: BaseModel, validate_input: ValidateInput):
        pass

    def execute(self, context: Context, validate_input: ValidateInput, params: BaseModel):
        return self.validate(context, params, validate_input)
