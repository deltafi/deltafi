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
from deltafi.storage import ContentReference, Segment

TEST_DID = "123"


def make_segment(seg_id):
    segment = Segment(uuid=seg_id, offset=0, size=100, did=TEST_DID)
    return segment


def make_content_reference(seg_id):
    segment = make_segment(seg_id)
    content_reference = ContentReference(segments=[segment], media_type="xml")
    return content_reference
