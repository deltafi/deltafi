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

import copy
from logging import Logger
from typing import Dict, List, NamedTuple

from deltafi.storage import ContentService, ContentReference


class Context(NamedTuple):
    did: str
    action_name: str
    source_filename: str
    ingress_flow: str
    egress_flow: str
    system: str
    hostname: str
    content_service: ContentService
    logger: Logger

    @classmethod
    def create(cls, context: dict, hostname: str, content_service: ContentService, logger: Logger):
        did = context['did']
        action_name = context['name']
        if 'sourceFilename' in context:
            source_filename = context['sourceFilename']
        else:
            source_filename = None
        ingress_flow = context['ingressFlow']
        if 'egressFlow' in context:
            egress_flow = context['egressFlow']
        else:
            egress_flow = None
        system = context['systemName']
        return Context(did=did,
                       action_name=action_name,
                       source_filename=source_filename,
                       ingress_flow=ingress_flow,
                       egress_flow=egress_flow,
                       system=system,
                       hostname=hostname,
                       content_service=content_service,
                       logger=logger)


class Content:
    """
    A Content class that holds information about a piece of content, including its name, reference, and service.
    Attributes:
        name (str): The name of the content.
        content_reference (ContentReference): A ContentReference object that holds information about the content's data.
        content_service (ContentService): A ContentService object used to retrieve the content data.
    """

    def __init__(self, name: str, content_reference: ContentReference, content_service: ContentService):
        self.name = name
        self.content_reference = content_reference
        self.content_service = content_service

    def json(self):
        """
        Returns a dictionary representation of the Content object.

        Returns:
            dict: A dictionary containing 'name' and 'contentReference' keys.
        """
        return {
            'name': self.name,
            'contentReference': self.content_reference.json(),
        }

    def copy(self):
        """
        Returns a deep copy of the Content object.

        Returns:
            Content: A deep copy of the Content object.
        """
        return Content(name=self.name,
                       content_reference=copy.deepcopy(self.content_reference),
                       content_service=self.content_service)

    def subcontent(self, offset: int, size: int):
        """
        Returns a new Content object with a subset of the original content.

        Args:
            offset (int): The starting byte offset.
            size (int): The size of the subset in bytes.

        Returns:
            Content: A new Content object with the specified subcontent.
        """
        return Content(name=self.name,
                       content_reference=self.content_reference.subreference(offset, size),
                       content_service=self.content_service)

    def get_size(self):
        """
        Returns the size of the content in bytes.

        Returns:
            int: The size of the content in bytes.
        """
        return self.content_reference.get_size()

    def get_media_type(self):
        """
        Returns the media type of the content.

        Returns:
        str: The media type of the content.
        """
        return self.content_reference.media_type

    def set_media_type(self, media_type: str):
        """
        Sets the media type of the content.

        Args:
            media_type (str): The media type to set.
        """
        self.content_reference = self.content_reference._replace(media_type=media_type)

    def load_bytes(self):
        """
        Retrieves the content as bytes.

        Returns:
            bytes: The content as bytes.
        """
        return self.content_service.get_bytes(self.content_reference)

    def load_str(self):
        """
        Retrieves the content as a string.

        Returns:
            str: The content as a string.
        """
        return self.content_service.get_str(self.content_reference)

    def prepend(self, other_content):
        """
        Prepends the content from another Content object.

        Args:
            other_content (Content): The Content object to prepend.
        """
        self.content_reference.segments[0:0] = other_content.content_reference.segments

    def append(self, other_content):
        """
        Appends the content from another Content object.

        Args:
            other_content (Content): The Content object to append.
        """
        self.content_reference.segments.extend(other_content.content_reference.segments)

    def __eq__(self, other):
        if isinstance(other, Content):
            return (self.name == other.name and
                    self.content_reference == other.content_reference and
                    self.content_service == other.content_service)
        return False

    @classmethod
    def from_str(cls, context: Context, str_data: str, name: str, media_type: str):
        content_reference = context.content_service.put_str(context.did, str_data, media_type)
        return Content(name=name, content_reference=content_reference, content_service=context.content_service)

    @classmethod
    def from_bytes(cls, context: Context, byte_data: bytes, name: str, media_type: str):
        content_reference = context.content_service.put_bytes(context.did, byte_data, media_type)
        return Content(name=name, content_reference=content_reference, content_service=context.content_service)

    @classmethod
    def from_dict(cls, content: dict, content_service: ContentService):
        if 'name' in content:
            name = content['name']
        else:
            name = None
        content_reference = ContentReference.from_dict(content['contentReference'])
        return Content(name=name,
                       content_reference=content_reference,
                       content_service=content_service)


class Domain(NamedTuple):
    name: str
    value: str
    media_type: str

    @classmethod
    def from_dict(cls, domain: dict):
        name = domain['name']
        if 'value' in domain:
            value = domain['value']
        else:
            value = None
        media_type = domain['mediaType']
        return Domain(name=name,
                      value=value,
                      media_type=media_type)


class SourceInfo(NamedTuple):
    filename: str
    flow: str
    metadata: Dict[str, str]

    def json(self):
        return {
            'filename': self.filename,
            'flow': self.flow,
            'metadata': self.metadata
        }


class DeltaFileMessage(NamedTuple):
    metadata: Dict[str, str]
    content_list: List[Content]
    domains: List[Domain]
    enrichment: List[Domain]

    @classmethod
    def from_dict(cls, delta_file_message: dict, content_service: ContentService):
        metadata = delta_file_message['metadata']
        content_list = [Content.from_dict(content, content_service) for content in delta_file_message['contentList']]
        domains = [Domain.from_dict(domain) for domain in delta_file_message['domains']] if 'domains' in delta_file_message else []
        enrichment = [Domain.from_dict(domain) for domain in delta_file_message['enrichment']] if 'enrichment' in delta_file_message else []

        return DeltaFileMessage(metadata=metadata,
                                content_list=content_list,
                                domains=domains,
                                enrichment=enrichment)


class Event(NamedTuple):
    delta_file_messages: List[DeltaFileMessage]
    context: Context
    params: dict
    queue_name: str
    return_address: str

    @classmethod
    def create(cls, event: dict, hostname: str, content_service: ContentService, logger: Logger):
        delta_file_messages = [DeltaFileMessage.from_dict(delta_file_message, content_service) for delta_file_message in event['deltaFileMessages']]
        context = Context.create(event['actionContext'], hostname, content_service, logger)
        params = event['actionParams']
        queue_name = None
        if 'queueName' in event:
            queue_name = event['queueName']
        return_address = None
        if 'returnAddress' in event:
            return_address = event['returnAddress']
        return Event(delta_file_messages, context, params, queue_name, return_address)
