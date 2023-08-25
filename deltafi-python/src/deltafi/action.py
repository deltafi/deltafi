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

from deltafi.actiontype import ActionType
from deltafi.domain import Context, DeltaFileMessage
from deltafi.input import DomainInput, EgressInput, EnrichInput, FormatInput, LoadInput, TransformInput, ValidateInput
from deltafi.result import *
from pydantic import BaseModel


class Action(ABC):
    def __init__(self, action_type: ActionType, description: str, requires_domains: List[str],
                 requires_enrichments: List[str]):
        self.action_type = action_type
        self.description = description
        self.requires_domains = requires_domains
        self.requires_enrichments = requires_enrichments

    @abstractmethod
    def execute(self, event):
        pass

    def param_class(self):
        return BaseModel

    def validate_type(self, result, types: tuple):
        if not isinstance(result, types):
            raise ValueError(f"{self.__class__.__name__} must return one of "
                             f"{[result_type.__name__ for result_type in types]} "
                             f"but a {result.__class__.__name__} was returned")


class DomainAction(Action):
    def __init__(self, description: str, requires_domains: List[str]):
        super().__init__(ActionType.DOMAIN, description, requires_domains, [])

    def execute(self, event):
        domain_input = DomainInput(content=event.delta_file_messages[0].content_list,
                                   metadata=event.delta_file_messages[0].metadata,
                                   domains={domain.name: domain for domain in event.delta_file_messages[0].domains})
        result = self.domain(event.context, self.param_class().parse_obj(event.params), domain_input)
        self.validate_type(result, (DomainResult, ErrorResult))
        return result

    @abstractmethod
    def domain(self, context: Context, params: BaseModel, domain_input: DomainInput):
        pass


class EgressAction(Action):
    def __init__(self, description: str):
        super().__init__(ActionType.EGRESS, description, [], [])

    def execute(self, event):
        egress_input = EgressInput(content=event.delta_file_messages[0].content_list[0],
                                   metadata=event.delta_file_messages[0].metadata)
        result = self.egress(event.context, self.param_class().parse_obj(event.params), egress_input)
        self.validate_type(result, (EgressResult, ErrorResult, FilterResult))
        return result

    @abstractmethod
    def egress(self, context: Context, params: BaseModel, egress_input: EgressInput):
        pass


class EnrichAction(Action):
    def __init__(self, description: str, requires_domains: List[str], requires_enrichments: List[str]):
        super().__init__(ActionType.ENRICH, description, requires_domains, requires_enrichments)

    def execute(self, event):
        enrich_input = EnrichInput(content=event.delta_file_messages[0].content_list,
                                   metadata=event.delta_file_messages[0].metadata,
                                   domains={domain.name: domain for domain in event.delta_file_messages[0].domains},
                                   enrichments={domain.name: domain for domain in event.delta_file_messages[0].enrichments})
        result = self.enrich(event.context, self.param_class().parse_obj(event.params), enrich_input)
        self.validate_type(result, (EnrichResult, ErrorResult))
        return result

    @abstractmethod
    def enrich(self, context: Context, params: BaseModel, enrich_input: EnrichInput):
        pass


class FormatAction(Action):
    def __init__(self, description: str, requires_domains: List[str], requires_enrichments: List[str]):
        super().__init__(ActionType.FORMAT, description, requires_domains, requires_enrichments)

    def execute(self, event):
        format_input = FormatInput(content=event.delta_file_messages[0].content_list,
                                   metadata=event.delta_file_messages[0].metadata,
                                   domains={domain.name: domain for domain in event.delta_file_messages[0].domains},
                                   enrichments={domain.name: domain for domain in event.delta_file_messages[0].enrichments})
        result = self.format(event.context, self.param_class().parse_obj(event.params), format_input)
        self.validate_type(result, (FormatResult, FormatManyResult, ErrorResult, FilterResult))
        return result

    @abstractmethod
    def format(self, context: Context, params: BaseModel, format_input: FormatInput):
        pass


class LoadAction(Action):
    def __init__(self, description: str):
        super().__init__(ActionType.LOAD, description, [], [])

    def execute(self, event):
        load_input = LoadInput(content=event.delta_file_messages[0].content_list,
                               metadata=event.delta_file_messages[0].metadata)
        result = self.load(event.context, self.param_class().parse_obj(event.params), load_input)
        self.validate_type(result, (LoadResult, LoadManyResult, ErrorResult, FilterResult, ReinjectResult))
        return result

    @abstractmethod
    def load(self, context: Context, params: BaseModel, load_input: LoadInput):
        pass


class TransformAction(Action):
    def __init__(self, description: str):
        super().__init__(ActionType.TRANSFORM, description, [], [])

    def execute(self, event):
        transform_input = TransformInput(content=event.delta_file_messages[0].content_list,
                                         metadata=event.delta_file_messages[0].metadata)
        result = self.transform(event.context, self.param_class().parse_obj(event.params), transform_input)
        self.validate_type(result, (TransformResult, ErrorResult, FilterResult, ReinjectResult))
        return result

    @abstractmethod
    def transform(self, context: Context, params: BaseModel, transform_input: TransformInput):
        pass


class ValidateAction(Action):
    def __init__(self, description: str):
        super().__init__(ActionType.VALIDATE, description, [], [])

    def execute(self, event):
        validate_input = ValidateInput(content=event.delta_file_messages[0].content_list[0],
                                       metadata=event.delta_file_messages[0].metadata)
        result = self.validate(event.context, self.param_class().parse_obj(event.params), validate_input)
        self.validate_type(result, (ValidateResult, ErrorResult, FilterResult))
        return result

    @abstractmethod
    def validate(self, context: Context, params: BaseModel, validate_input: ValidateInput):
        pass
