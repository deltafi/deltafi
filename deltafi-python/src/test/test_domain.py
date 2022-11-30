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
from deltafi.domain import Content
from deltafi.storage import ContentReference, Segment


def make_segment():
    segment = Segment(uuid="1", offset=0, size=100, did="2")
    return segment


def make_content_reference():
    segment = make_segment()
    content_reference = ContentReference(segments=[segment], media_type="xml")
    return content_reference


def test_context_json():
    content = Content(name="test", metadata={'key1': "value1", 'key2': "value2"},
        content_reference=make_content_reference())
    json = content.json()
    assert json["name"] == "test"

    metakeyval = json["metadata"]
    assert metakeyval[0]["key"] == "key1"
    assert metakeyval[0]["value"] == "value1"
    assert metakeyval[1]["key"] == "key2"
    assert metakeyval[1]["value"] == "value2"
