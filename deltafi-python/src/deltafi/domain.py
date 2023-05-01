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

from logging import Logger
from typing import Dict, List, NamedTuple

from deltafi.storage import ContentService, ContentReference


class Content(NamedTuple):
    name: str
    content_reference: ContentReference

    def json(self):
        return {
            'name': self.name,
            'contentReference': self.content_reference.json(),
        }

    @classmethod
    def from_dict(cls, content: dict):
        if 'name' in content:
            name = content['name']
        else:
            name = None
        content_reference = ContentReference.from_dict(content['contentReference'])
        return Content(name=name,
                       content_reference=content_reference)


class Context(NamedTuple):
    did: str
    action_name: str
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
        ingress_flow = context['ingressFlow']
        if 'egressFlow' in context:
            egress_flow = context['egressFlow']
        else:
            egress_flow = None
        system = context['systemName']
        return Context(did=did,
                       action_name=action_name,
                       ingress_flow=ingress_flow,
                       egress_flow=egress_flow,
                       system=system,
                       hostname=hostname,
                       content_service=content_service,
                       logger=logger)


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
    source_filename: str
    metadata: Dict[str, str]
    content_list: List[Content]
    domains: List[Domain]
    enrichment: List[Domain]

    @classmethod
    def from_dict(cls, delta_file_message: dict):
        source_filename = delta_file_message['sourceFilename']
        metadata = delta_file_message['metadata']
        content_list = [Content.from_dict(content) for content in delta_file_message['contentList']]
        domains = [Domain.from_dict(domain) for domain in delta_file_message['domains']] if 'domains' in delta_file_message else []
        enrichment = [Domain.from_dict(domain) for domain in delta_file_message['enrichment']] if 'enrichment' in delta_file_message else []

        return DeltaFileMessage(source_filename=source_filename,
                                metadata=metadata,
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
        delta_file_messages = [DeltaFileMessage.from_dict(delta_file_message) for delta_file_message in event['deltaFileMessages']]
        context = Context.create(event['actionContext'], hostname, content_service, logger)
        params = event['actionParams']
        queue_name = None
        if 'queueName' in event:
            queue_name = event['queueName']
        return_address = None
        if 'returnAddress' in event:
            return_address = event['returnAddress']
        return Event(delta_file_messages, context, params, queue_name, return_address)
