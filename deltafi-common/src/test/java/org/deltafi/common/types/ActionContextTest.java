/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.common.types;

import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

class ActionContextTest {

    // the expected number of member variables in 'ActionContext'
    private static final int EXPECTED_NUMBER_FIELDS = 14;

    private static final UUID PARENT_DID = UUID.fromString("d5b41c6b-7fce-43b7-b6bf-5cd0a3aa95b9");
    private static final UUID CHILD_DID = UUID.fromString("23463e0b-5843-4fd5-b67e-caeea5c2a2fe");

    private static final String DELTA_FILE_NAME = "the-delta-file-name";
    private static final String DATA_SOURCE = "the-data-source";
    private static final String FLOW_NAME = "the-flow-name";
    private static final UUID FLOW_ID = UUID.fromString("d5b41c6b-7fce-43b7-b6bf-5cd0a3aa1111");
    private static final String ACTION_NAME = "the-action-name";
    private static final String ACTION_VERSION = "the-action-version";
    private static final String HOSTNAME = "the-hostname";
    private static final OffsetDateTime START_TIME = OffsetDateTime.parse("2019-08-31T15:20:30+08:00");
    private static final String SYSTEM_NAME = "the-system-name";
    private static final ActionContentStorageService CONTENT_STORAGE_SERVICE =
            new ActionContentStorageService(new MyObjectStorageService());
    private static final JoinConfiguration JOIN = new JoinConfiguration(Duration.between(
            Instant.parse("2017-10-03T10:15:30.00Z"), Instant.parse("2017-10-03T10:16:30.00Z")),
            0, 1, "the-key");
    private static final List<UUID> JOINED_DIDS = new LinkedList<UUID>();
    private static final String MEMO = "the-memo";


    public static class MyObjectStorageService implements ObjectStorageService {

        public MyObjectStorageService() {}

        public InputStream getObject(ObjectReference objectReference) {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return 0;
                }
            };
        }

        public ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) {
            return new ObjectReference("bucket", "name");
        }

        public void putObjects(String bucket, Map<ObjectReference, InputStream> objectsToSave) {}

        public void removeObject(ObjectReference objectReference) {}

        public boolean removeObjects(String bucket, List<String> objectNames) {return true;}
    }

    private void check(ActionContext actionContext, UUID did) {
        assertThat(actionContext.getDid().toString(), equalTo(did.toString()));
        assertThat(actionContext.getDeltaFileName(), equalTo(DELTA_FILE_NAME));
        assertThat(actionContext.getDataSource(), equalTo(DATA_SOURCE));
        assertThat(actionContext.getFlowName(), equalTo(FLOW_NAME));
        assertThat(actionContext.getFlowId(), equalTo(FLOW_ID));
        assertThat(actionContext.getActionName(), equalTo(ACTION_NAME));
        assertThat(actionContext.getActionVersion(), equalTo(ACTION_VERSION));
        assertThat(actionContext.getHostname(), equalTo(HOSTNAME));
        assertThat(actionContext.getStartTime().toString(), equalTo(START_TIME.toString()));
        assertThat(actionContext.getSystemName(), equalTo(SYSTEM_NAME));
        assertThat(actionContext.getContentStorageService(), sameInstance(CONTENT_STORAGE_SERVICE));
        assertThat(actionContext.getJoin(), sameInstance(JOIN));
        assertThat(actionContext.getJoinedDids(), sameInstance(JOINED_DIDS));
        assertThat(actionContext.getMemo(), equalTo(MEMO));

        // Check the number of expected member variables in 'ActionContext'.  If any are added or removed, then the
        // assertions above need to abe adjusted.
        Field[] fieldArray = ActionContext.class.getDeclaredFields();
        assertThat(fieldArray.length, equalTo(EXPECTED_NUMBER_FIELDS));
    }


    @Test
    void testConstructor() {

        ActionContext actionContext = new ActionContext(
                PARENT_DID,
                DELTA_FILE_NAME,
                DATA_SOURCE,
                FLOW_NAME,
                FLOW_ID,
                ACTION_NAME,
                ACTION_VERSION,
                HOSTNAME,
                START_TIME,
                SYSTEM_NAME,
                CONTENT_STORAGE_SERVICE,
                JOIN,
                JOINED_DIDS,
                MEMO
        );

        check(actionContext, PARENT_DID);
    }

    @Test
    void testCopy() {

        ActionContext parentActionContext = new ActionContext(
                PARENT_DID,
                DELTA_FILE_NAME,
                DATA_SOURCE,
                FLOW_NAME,
                FLOW_ID,
                ACTION_NAME,
                ACTION_VERSION,
                HOSTNAME,
                START_TIME,
                SYSTEM_NAME,
                CONTENT_STORAGE_SERVICE,
                JOIN,
                JOINED_DIDS,
                MEMO
        );

        ActionContext childActionContext = parentActionContext.copy(CHILD_DID);
        check(childActionContext, CHILD_DID);
    }

}