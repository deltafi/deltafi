#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
from deltafi.domain import Context, DeltaFileMessage
from deltafi.genericmodel import GenericModel
from deltafi.input import EgressInput, TransformInput
from deltafi.result import *
from pydantic import BaseModel


class Join(ABC):
    def join(self, transform_inputs: List[TransformInput]):
        all_content = []
        all_metadata = {}
        for transform_input in transform_inputs:
            all_content += transform_input.content
            all_metadata.update(transform_input.metadata)
        return TransformInput(content=all_content, metadata=all_metadata)


class Action(ABC):
    def __init__(self, action_type: ActionType, description: str, valid_result_types: tuple):
        self.action_type = action_type
        self.description = description
        self.valid_result_types = valid_result_types
        self.action_execution = None

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
    def __init__(self, description: str):
        super().__init__(ActionType.EGRESS, description, (EgressResult, ErrorResult, FilterResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return EgressInput(content=delta_file_message.content_list[0], metadata=delta_file_message.metadata)

    @abstractmethod
    def egress(self, context: Context, params: BaseModel, egress_input: EgressInput):
        pass

    def execute(self, context: Context, egress_input: EgressInput, params: BaseModel):
        return self.egress(context, params, egress_input)


class TimedIngressAction(Action, ABC):
    def __init__(self, description: str):
        super().__init__(ActionType.TIMED_INGRESS, description, (IngressResult, ErrorResult))

    def build_input(self, context: Context, delta_file_message: DeltaFileMessage):
        return None

    @abstractmethod
    def ingress(self, context: Context, params: BaseModel):
        pass

    def execute(self, context: Context, input_placeholder: Any, params: BaseModel):
        return self.ingress(context, params)


class TransformAction(Action, ABC):
    def __init__(self, description: str):
        super().__init__(ActionType.TRANSFORM, description,
                         (TransformResult, TransformResults, ErrorResult, FilterResult))

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
