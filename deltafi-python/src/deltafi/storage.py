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

import io
import uuid
from typing import List, NamedTuple
from urllib.parse import urlparse

import minio
from minio.deleteobjects import DeleteObject

BUCKET = 'storage'


class Segment(NamedTuple):
    uuid: str
    offset: int
    size: int
    did: str

    def json(self):
        return {
            'uuid': str(self.uuid),
            'offset': self.offset,
            'size': self.size,
            'did': self.did
        }

    @classmethod
    def from_dict(cls, segment: dict):
        s_uuid = segment['uuid']
        offset = segment['offset']
        size = segment['size']
        did = segment['did']
        return Segment(uuid=s_uuid,
                       offset=offset,
                       size=size,
                       did=did)

    def id(self):
        return f"{self.did[:3]}/{self.did}/{self.uuid}"


class ContentService:
    def __init__(self, url, access_key, secret_key):
        parsed = urlparse(url)
        self.minio_client = minio.Minio(
            f"{parsed.hostname}:{str(parsed.port)}",
            access_key=access_key,
            secret_key=secret_key,
            secure=False
        )

        found = self.minio_client.bucket_exists(BUCKET)
        if not found:
            raise RuntimeError(f"Minio bucket {BUCKET} not found")

    def get_bytes(self, segments: List[Segment]):
        return b"".join([self.minio_client.get_object(BUCKET, segment.id(), segment.offset,
                                                      segment.size).read() for segment in segments])

    def get_str(self, segments: List[Segment]):
        return self.get_bytes(segments).decode('utf-8')

    def put_bytes(self, did, bytes_data):
        segment = Segment(uuid=str(uuid.uuid4()),
                          offset=0,
                          size=len(bytes_data),
                          did=did)
        self.minio_client.put_object(BUCKET, segment.id(), io.BytesIO(bytes_data), len(bytes_data))
        return segment

    def put_str(self, did, string_data):
        return self.put_bytes(did, string_data.encode('utf-8'))

    def delete_all(self, segments: List[Segment]):
        delete_objects = [DeleteObject(seg.id()) for seg in segments]
        return self.minio_client.remove_objects(BUCKET, delete_objects)
