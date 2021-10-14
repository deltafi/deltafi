import { ref } from 'vue';

    const errors = ref([
        {
            "did": "181937c8-c84d-425c-8717-4a81bd78f9c6",
            "context": "blah",
            "cause": "java.lang.NullPointerException",
            "timestamp": "2021-10-14T14:36:31Z",
            "parent": {
                "flow": "stix2_1",
                "action": "load_action",
                "state": "ERROR",
                "did": "ef2d3b93-5a60-4251-a3f5-5802b5844a20"
            }
        },
        {
            "did": "6ba48360-2d2d-11ec-8e70-080027d2d9fa",
            "context": "blah1",
            "cause": "java.lang.NullPointerException",
            "timestamp": "2021-10-13T14:36:31Z",
            "parent": {
                "flow": "stix2_1",
                "action": "load_action",
                "state": "ERROR",
                "did": "94e1c4c2-2d2d-11ec-94ef-080027d2d9fa"
            }
        },
        {
            "did": "ae00ed20-2d2d-11ec-86a8-080027d2d9fa",
            "context": "blah",
            "cause": "java.lang.NullPointerException",
            "timestamp": "2021-10-14T14:36:31Z",
            "parent": {
                "flow": "stix2_1",
                "action": "load_action",
                "state": "ERROR",
                "did": "e38c3f62-2d2d-11ec-a0bf-080027d2d9fa"
            }
        }
    ]);
    export default errors;
    