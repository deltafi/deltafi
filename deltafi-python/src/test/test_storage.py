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

import io

import minio
import pytest
from deltafi.storage import Segment, ContentService
from mockito import when, mock, unstub, verifyStubbedInvocationsAreUsed, ANY

BUCKET = 'storage'
SEG_1_DATA = "one"
SEG_2_DATA = "twotwo"
TEST_DID = "123did"


def make_segment(segment_id, data):
    segment = Segment(uuid=segment_id, offset=0, size=len(data), did=TEST_DID)
    return segment


def make_segments():
    s1 = make_segment("seg1", SEG_1_DATA)
    s2 = make_segment("seg2", SEG_2_DATA)
    return [s1, s2]


def faux_content_service():
    return ContentService("http://127.0.0.1:6543", "access_key", "secret_key", "storage")


def test_content_service():
    unstub()
    minio_mock = mock(minio.Minio)
    when(minio_mock).bucket_exists(...).thenReturn(True)
    when(minio).Minio(...).thenReturn(minio_mock)
    service = faux_content_service()


def test_no_bucket():
    unstub()
    minio_mock = mock(minio.Minio)
    when(minio_mock).bucket_exists(...).thenReturn(False)
    when(minio).Minio(...).thenReturn(minio_mock)
    with pytest.raises(Exception):
        service = faux_content_service()


def test_put_str():
    unstub()

    minio_mock = mock(minio.Minio)
    when(minio_mock).bucket_exists(...).thenReturn(True)
    # return value is ignored in ContentService
    when(minio_mock).put_object(BUCKET, ANY(str), ANY(io.BytesIO), 8).thenReturn(None)
    when(minio).Minio(...).thenReturn(minio_mock)
    service = faux_content_service()

    segment = service.put_str("123did", "the-data")
    verifyStubbedInvocationsAreUsed(minio_mock)

    assert segment.offset == 0
    assert segment.size == 8
    assert segment.did == "123did"
    assert len(segment.uuid) == 36


def test_gett_str():
    unstub()

    bytes_one = SEG_1_DATA.encode('utf-8')
    bytes_two = SEG_2_DATA.encode('utf-8')

    minio_mock = mock(minio.Minio)
    when(minio_mock).bucket_exists(...).thenReturn(True)
    when(minio_mock).get_object(BUCKET, "123/123did/seg1", 0, len(SEG_1_DATA)).thenReturn(io.BytesIO(bytes_one))
    when(minio_mock).get_object(BUCKET, "123/123did/seg2", 0, len(SEG_2_DATA)).thenReturn(io.BytesIO(bytes_two))
    when(minio).Minio(...).thenReturn(minio_mock)
    service = faux_content_service()

    content = service.get_str(make_segments())
    verifyStubbedInvocationsAreUsed(minio_mock)

    assert content == SEG_1_DATA + SEG_2_DATA
    assert len(content) == 9


def test_delete_all():
    unstub()

    minio_mock = mock(minio.Minio)
    when(minio).Minio(...).thenReturn(minio_mock)
    when(minio_mock).bucket_exists(...).thenReturn(True)
    when(minio_mock).remove_objects(BUCKET, ANY()).thenReturn([])
    service = faux_content_service()
    service.delete_all(make_segments())

    verifyStubbedInvocationsAreUsed(minio_mock)
