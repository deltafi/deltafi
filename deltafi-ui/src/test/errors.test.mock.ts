import { ref } from 'vue';

    const errors = ref([
        {
            "did": "368b4a35-3c5e-491f-bd0f-909f884d1038",
            "filename": "test-file.json",
            "flow": "stix2_1",
            "stage": "ERROR",
            "created": "2021-10-15T19:53:39.825Z",
            "modified": "2021-10-15T19:53:39.874Z",
            "errors": [
                {
                    "action": "Stix2_1LoadAction",
                    "state": "ERROR",
                    "created": "2021-10-15T19:53:39.874Z",
                    "cause": "Failed to convert json to StixInputObject",
                    "context": "com.fasterxml.jackson.core.JsonParseException: Unexpected character ('-' (code 45)) in numeric value: expected digit (0-9) to follow minus sign, for valid numeric value\n at [Source: (String)\"---\n\"; line: 1, column: 3]\ncom.fasterxml.jackson.core.JsonParseException: Unexpected character ('-' (code 45)) in numeric value: expected digit (0-9) to follow minus sign, for valid numeric value\n at [Source: (String)\"---\n\"; line: 1, column: 3]\n\tat com.fasterxml.jackson.core.JsonParser._constructError(JsonParser.java:2337)\n\tat com.fasterxml.jackson.core.base.ParserMinimalBase._reportError(ParserMinimalBase.java:710)\n\tat com.fasterxml.jackson.core.base.ParserMinimalBase.reportUnexpectedNumberChar(ParserMinimalBase.java:539)\n\tat com.fasterxml.jackson.core.json.ReaderBasedJsonParser._handleInvalidNumberStart(ReaderBasedJsonParser.java:1684)\n\tat com.fasterxml.jackson.core.json.ReaderBasedJsonParser._parseNegNumber(ReaderBasedJsonParser.java:1438)\n\tat com.fasterxml.jackson.core.json.ReaderBasedJsonParser.nextToken(ReaderBasedJsonParser.java:763)\n\tat com.fasterxml.jackson.databind.ObjectMapper._initForReading(ObjectMapper.java:4684)\n\tat com.fasterxml.jackson.databind.ObjectMapper._readMapAndClose(ObjectMapper.java:4586)\n\tat com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3548)\n\tat com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3531)\n\tat org.deltafi.stix.domain.api.StixConverter.toStixInputObject(StixConverter.java:40)\n\tat org.deltafi.stix.actions.StixLoadAction.jsonToGraphQuery(StixLoadAction.java:60)\n\tat org.deltafi.stix.actions.StixLoadAction.execute(StixLoadAction.java:43)\n\tat org.deltafi.actionkit.action.Action.executeAction(Action.java:111)\n\tat org.deltafi.actionkit.action.Action.startListening(Action.java:102)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)\n\tat java.base/java.util.concurrent.FutureTask.runAndReset(FutureTask.java:305)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:305)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)\n\tat java.base/java.lang.Thread.run(Thread.java:829)\n"
                },
                {
                    "action": "Stix2_1LoadAction",
                    "state": "ERROR",
                    "created": "2021-10-15T19:53:06.436Z",
                    "cause": "Failed to convert json to StixInputObject",
                    "context": "com.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (String)\"<xml></xml>\"; line: 1, column: 2]\ncom.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (String)\"<xml></xml>\"; line: 1, column: 2]\n\tat com.fasterxml.jackson.core.JsonParser._constructError(JsonParser.java:2337)\n\tat com.fasterxml.jackson.core.base.ParserMinimalBase._reportError(ParserMinimalBase.java:710)\n\tat com.fasterxml.jackson.core.base.ParserMinimalBase._reportUnexpectedChar(ParserMinimalBase.java:635)\n\tat com.fasterxml.jackson.core.json.ReaderBasedJsonParser._handleOddValue(ReaderBasedJsonParser.java:1952)\n\tat com.fasterxml.jackson.core.json.ReaderBasedJsonParser.nextToken(ReaderBasedJsonParser.java:781)\n\tat com.fasterxml.jackson.databind.ObjectMapper._initForReading(ObjectMapper.java:4684)\n\tat com.fasterxml.jackson.databind.ObjectMapper._readMapAndClose(ObjectMapper.java:4586)\n\tat com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3548)\n\tat com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3531)\n\tat org.deltafi.stix.domain.api.StixConverter.toStixInputObject(StixConverter.java:40)\n\tat org.deltafi.stix.actions.StixLoadAction.jsonToGraphQuery(StixLoadAction.java:60)\n\tat org.deltafi.stix.actions.StixLoadAction.execute(StixLoadAction.java:43)\n\tat org.deltafi.actionkit.action.Action.executeAction(Action.java:111)\n\tat org.deltafi.actionkit.action.Action.startListening(Action.java:102)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)\n\tat java.base/java.util.concurrent.FutureTask.runAndReset(FutureTask.java:305)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:305)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)\n\tat java.base/java.lang.Thread.run(Thread.java:829)\n"
                }
            ]
        },
        {
        "did": "33029cc3-efba-4f50-9706-d9c0563ddf0e",
        "filename": "test-file.xml",
        "flow": "stix2_1",
        "stage": "ERROR",
        "created": "2021-10-15T19:53:06.397Z",
        "modified": "2021-10-15T19:53:06.436Z",
        "errors": [
            {
            "action": "Stix2_1LoadAction",
            "state": "ERROR",
            "created": "2021-10-15T19:53:06.436Z",
            "cause": "Failed to convert json to StixInputObject",
            "context": "com.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (String)\"<xml></xml>\"; line: 1, column: 2]\ncom.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (String)\"<xml></xml>\"; line: 1, column: 2]\n\tat com.fasterxml.jackson.core.JsonParser._constructError(JsonParser.java:2337)\n\tat com.fasterxml.jackson.core.base.ParserMinimalBase._reportError(ParserMinimalBase.java:710)\n\tat com.fasterxml.jackson.core.base.ParserMinimalBase._reportUnexpectedChar(ParserMinimalBase.java:635)\n\tat com.fasterxml.jackson.core.json.ReaderBasedJsonParser._handleOddValue(ReaderBasedJsonParser.java:1952)\n\tat com.fasterxml.jackson.core.json.ReaderBasedJsonParser.nextToken(ReaderBasedJsonParser.java:781)\n\tat com.fasterxml.jackson.databind.ObjectMapper._initForReading(ObjectMapper.java:4684)\n\tat com.fasterxml.jackson.databind.ObjectMapper._readMapAndClose(ObjectMapper.java:4586)\n\tat com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3548)\n\tat com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3531)\n\tat org.deltafi.stix.domain.api.StixConverter.toStixInputObject(StixConverter.java:40)\n\tat org.deltafi.stix.actions.StixLoadAction.jsonToGraphQuery(StixLoadAction.java:60)\n\tat org.deltafi.stix.actions.StixLoadAction.execute(StixLoadAction.java:43)\n\tat org.deltafi.actionkit.action.Action.executeAction(Action.java:111)\n\tat org.deltafi.actionkit.action.Action.startListening(Action.java:102)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)\n\tat java.base/java.util.concurrent.FutureTask.runAndReset(FutureTask.java:305)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:305)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)\n\tat java.base/java.lang.Thread.run(Thread.java:829)\n"
            }
        ]
        } 
    ]);
    export default errors;
    