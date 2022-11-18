"""
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
"""
import io
import minio
from typing import List, NamedTuple
from urllib.parse import urlparse
import uuid

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
        return f"{self.did}/{self.uuid}"


class ContentReference(NamedTuple):
    segments: List[Segment]
    media_type: str

    def json(self):
        return {
            'segments': [segment.json() for segment in self.segments],
            'mediaType': self.media_type
        }

    @classmethod
    def from_dict(cls, content_reference: dict):
        segments = [Segment.from_dict(segment) for segment in content_reference['segments']]
        media_type = content_reference['mediaType']
        return ContentReference(segments=segments,
                                media_type=media_type)


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

    def get_bytes(self, content_reference: ContentReference):
        return b"".join([self.minio_client.get_object(BUCKET, segment.id(), segment.offset,
                                            segment.size).read() for segment in content_reference.segments])

    def get_str(self, content_reference: ContentReference):
        return self.get_bytes(content_reference).decode('utf-8')

    def put_bytes(self, did, bytes_data, media_type):
        segment = Segment(uuid=str(uuid.uuid4()),
                          offset=0,
                          size=len(bytes_data),
                          did=did)
        content_reference = ContentReference(segments=[segment],
                                             media_type=media_type)
        self.minio_client.put_object(BUCKET, segment.id(), io.BytesIO(bytes_data), len(bytes_data))
        return content_reference

    def put_str(self, did, string_data, media_type):
        return self.put_bytes(did, string_data.encode('utf-8'), media_type)
