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
from datetime import datetime, timedelta, timezone
from logging import Logger
from typing import Dict, List, NamedTuple

from deltafi.storage import ContentService, Segment


class ActionExecution(NamedTuple):
    clazz: str
    action: str
    did: str
    start_time: datetime

    def exceeds_duration(self, duration: timedelta) -> bool:
        return self.start_time + duration < datetime.now(timezone.utc)

    @property
    def key(self) -> str:
        return f"{self.clazz}:{self.action}:{self.did}"


class Context(NamedTuple):
    did: str
    action_flow: str
    action_name: str
    source_filename: str
    ingress_flow: str
    egress_flow: str
    system: str
    hostname: str
    content_service: ContentService
    collect: dict = None
    collected_dids: List[str] = None
    memo: str = None
    logger: Logger = None

    @classmethod
    def create(cls, context: dict, hostname: str, content_service: ContentService, logger: Logger):
        did = context['did']
        action_name_parts = context['name'].split(".")
        action_flow = action_name_parts[0]
        action_name = action_name_parts[1]
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
        if 'collect' in context:
            collect = context['collect']
        else:
            collect = None
        if 'collectedDids' in context:
            collected_dids = context['collectedDids']
        else:
            collected_dids = None
        if 'memo' in context:
            memo = context['memo']
        else:
            memo = None
        return Context(did=did,
                       action_flow=action_flow,
                       action_name=action_name,
                       source_filename=source_filename,
                       ingress_flow=ingress_flow,
                       egress_flow=egress_flow,
                       system=system,
                       hostname=hostname,
                       content_service=content_service,
                       collect=collect,
                       collected_dids=collected_dids,
                       memo=memo,
                       logger=logger)


class Content:
    """
    A Content class that holds information about a piece of content, including its name, segments, mediaType, and service.
    Attributes:
        name (str): The name of the content.
        segments (List<Segment): The list of segments in storage that make up the Content
        media_type (str): The media type of the content
        content_service (ContentService): A ContentService object used to retrieve the content data.
    """

    def __init__(self, name: str, segments: List[Segment], media_type: str, content_service: ContentService):
        self.name = name
        self.segments = segments
        self.media_type = media_type
        self.content_service = content_service

    def json(self):
        """
        Returns a dictionary representation of the Content object.

        Returns:
            dict: A dictionary containing 'name', 'segments', and 'mediaType' keys.
        """
        return {
            'name': self.name,
            'segments': [segment.json() for segment in self.segments],
            'mediaType': self.media_type
        }

    def copy(self):
        """
        Returns a deep copy of the Content object.

        Returns:
            Content: A deep copy of the Content object.
        """
        return Content(name=self.name,
                       segments=copy.deepcopy(self.segments),
                       media_type=self.media_type,
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
                       segments=self.subsegments(offset, size),
                       media_type=self.media_type,
                       content_service=self.content_service)

    def subsegments(self, offset: int, size: int):
        if offset < 0:
            raise ValueError(f"subsegments offset must be positive, got {offset}")

        if size < 0:
            raise ValueError(f"subsegments size must be positive, got {size}")

        if size + offset > self.get_size():
            raise ValueError(f"Size + offset ({size} + {offset}) exceeds total Content size of {self.get_size()}")

        if size == 0:
            return []

        new_segments = []
        offset_remaining = offset
        size_remaining = size

        for segment in self.segments:
            if offset_remaining > 0:
                if segment.size < offset_remaining:
                    # the first offset is past this segment, skip it
                    offset_remaining -= segment.size
                    continue
                else:
                    # chop off the front of this segment
                    segment = Segment(uuid=segment.uuid,
                                      offset=segment.offset + offset_remaining,
                                      size=segment.size - offset_remaining,
                                      did=segment.did)
                    offset_remaining = 0

            if size_remaining < segment.size:
                # chop off the back of this segment
                segment = Segment(uuid=segment.uuid,
                                  offset=segment.offset,
                                  size=size_remaining,
                                  did=segment.did)
            size_remaining -= segment.size
            new_segments.append(segment)
            if size_remaining == 0:
                break

        return new_segments


    def get_size(self):
        """
        Returns the size of the content in bytes.

        Returns:
            int: The size of the content in bytes.
        """
        sum = 0
        for segment in self.segments:
            sum = sum + segment.size
        return sum

    def get_media_type(self):
        """
        Returns the media type of the content.

        Returns:
        str: The media type of the content.
        """
        return self.media_type

    def set_media_type(self, media_type: str):
        """
        Sets the media type of the content.

        Args:
            media_type (str): The media type to set.
        """
        self.media_type = media_type

    def load_bytes(self):
        """
        Retrieves the content as bytes.

        Returns:
            bytes: The content as bytes.
        """
        return self.content_service.get_bytes(self.segments)

    def load_str(self):
        """
        Retrieves the content as a string.

        Returns:
            str: The content as a string.
        """
        return self.content_service.get_str(self.segments)

    def prepend(self, other_content):
        """
        Prepends the content from another Content object.

        Args:
            other_content (Content): The Content object to prepend.
        """
        self.segments[0:0] = other_content.segments

    def append(self, other_content):
        """
        Appends the content from another Content object.

        Args:
            other_content (Content): The Content object to append.
        """
        self.segments.extend(other_content.segments)

    def __eq__(self, other):
        if isinstance(other, Content):
            return (self.name == other.name and
                    self.segments == other.segments and
                    self.media_type == other.media_type and
                    self.content_service == other.content_service)
        return False

    @classmethod
    def from_str(cls, context: Context, str_data: str, name: str, media_type: str):
        segment = context.content_service.put_str(context.did, str_data)
        return Content(name=name, segments=[segment], media_type=media_type, content_service=context.content_service)

    @classmethod
    def from_bytes(cls, context: Context, byte_data: bytes, name: str, media_type: str):
        segment = context.content_service.put_bytes(context.did, byte_data)
        return Content(name=name, segments=[segment], media_type=media_type, content_service=context.content_service)

    @classmethod
    def from_dict(cls, content: dict, content_service: ContentService):
        if 'name' in content:
            name = content['name']
        else:
            name = None
        segments = [Segment.from_dict(segment) for segment in content['segments']]
        media_type = content['mediaType']
        return Content(name=name,
                       segments=segments,
                       media_type=media_type,
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


class DeltaFileMessage(NamedTuple):
    metadata: Dict[str, str]
    content_list: List[Content]
    domains: List[Domain]
    enrichments: List[Domain]

    @classmethod
    def from_dict(cls, delta_file_message: dict, content_service: ContentService):
        metadata = delta_file_message['metadata']
        content_list = [Content.from_dict(content, content_service) for content in delta_file_message['contentList']]
        domains = [Domain.from_dict(domain) for domain in delta_file_message['domains']] if 'domains' in delta_file_message else []
        enrichments = [Domain.from_dict(domain) for domain in delta_file_message['enrichments']] if 'enrichments' in delta_file_message else []

        return DeltaFileMessage(metadata=metadata,
                                content_list=content_list,
                                domains=domains,
                                enrichments=enrichments)


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
