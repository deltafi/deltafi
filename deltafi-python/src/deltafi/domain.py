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
    metadata: Dict[str, str]
    content_reference: ContentReference

    def json(self):
        return {
            'name': self.name,
            'contentReference': self.content_reference.json(),
            'metadata': self.metadata
        }

    @classmethod
    def from_dict(cls, content: dict):
        name = content['name']
        metadata = content.get('metadata', {})
        content_reference = ContentReference.from_dict(content['contentReference'])
        return Content(name=name,
                       metadata=metadata,
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
        value = domain['value']
        media_type = domain['mediaType']
        return Domain(name=name,
                      value=value,
                      media_type=media_type)


class FormattedData(NamedTuple):
    filename: str
    format_action: str
    content_reference: ContentReference
    metadata: Dict[str, str]

    @classmethod
    def from_dict(cls, formatted_data: dict):
        filename = formatted_data['filename']
        format_action = formatted_data['formatAction']
        content_reference = ContentReference.from_dict(formatted_data['contentReference'])
        metadata = formatted_data.get('metadata', {})
        return FormattedData(filename=filename,
                             format_action=format_action,
                             content_reference=content_reference,
                             metadata=metadata)


class ProtocolLayer(NamedTuple):
    action: str
    content: List[Content]
    metadata: Dict[str, str]

    @classmethod
    def from_dict(cls, layer: dict):
        action = layer['action']
        content = [Content.from_dict(item) for item in layer['content']]
        metadata = layer.get('metadata', {})
        return ProtocolLayer(action=action,
                             content=content,
                             metadata=metadata)


class SourceInfo(NamedTuple):
    filename: str
    flow: str
    metadata: Dict[str, str]

    @classmethod
    def from_dict(cls, source_info: dict):
        filename = source_info['filename']
        flow = source_info['flow']
        metadata = source_info.get('metadata', {})
        return SourceInfo(filename=filename,
                          flow=flow,
                          metadata=metadata)


class DeltaFile(NamedTuple):
    did: str
    source_info: SourceInfo
    protocol_stack: List[ProtocolLayer]
    domains: List[Domain]
    indexed_metadata: Dict[str, str]
    enrichment: List[Domain]
    formatted_data: FormattedData

    @classmethod
    def from_dict(cls, delta_file: dict):
        did = delta_file['did']
        source_info = SourceInfo.from_dict(delta_file['sourceInfo'])
        protocol_stack = [ProtocolLayer.from_dict(layer) for layer in delta_file['protocolStack']]
        domains = [Domain.from_dict(domain) for domain in delta_file['domains']]
        indexed_metadata = delta_file['indexedMetadata']
        enrichment = [Domain.from_dict(domain) for domain in delta_file['enrichment']]
        if len(delta_file['formattedData']) > 0:
            formatted_data = FormattedData.from_dict(delta_file['formattedData'][0])
        else:
            formatted_data = None
        return DeltaFile(did=did,
                         source_info=source_info,
                         protocol_stack=protocol_stack,
                         domains=domains,
                         indexed_metadata=indexed_metadata,
                         enrichment=enrichment,
                         formatted_data=formatted_data)


class Event(NamedTuple):
    delta_file: DeltaFile
    context: Context
    params: dict

    @classmethod
    def create(cls, event: dict, hostname: str, content_service: ContentService, logger: Logger):
        delta_file = DeltaFile.from_dict(event['deltaFile'])
        context = Context.create(event['actionContext'], hostname, content_service, logger)
        params = event['actionParams']
        return Event(delta_file, context, params)
