/*
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
*/

const generateData = () => {
  return {
    replaceSourceMetadata: [
      {
        message: "Unable to get object from content storage",
        flows: [
          {
            flow: "smoke",
            totalCount: 1,
            dids: ["54bc2ef3-9851-4c79-879a-876b8d6226b2"],
          },
        ],
        totalCount: 1,
      },
      {
        message: "No egress flow configured",
        flows: [
          {
            flow: "stix1_x",
            totalCount: 1,
            dids: ["9ca12462-7112-433d-9049-61dbe25ab190"],
          },
          {
            flow: "stix2_1",
            totalCount: 2,
            dids: ["7199c1cc-27ca-48a5-a967-7b59d8c1885b", "bc86b7a7-2789-42fa-84f8-4d3497a2330c"],
          },
          {
            flow: "passthrough",
            totalCount: 170,
            dids: [
              "24850731-aeb2-4cbd-a67c-8e5ec70bbcb8",
              "1a00b0e4-2b65-43d5-8fff-fe2ddd444b1b",
              "67f84366-a7f4-4c1d-8c67-49fc2e08aab5",
              "1d9eb25d-f092-49af-a622-fa601b8b0130",
              "762b6665-b49a-4bf2-bb66-e55e46a1939f",
              "fa25005a-0904-462f-b9ee-c951e5188332",
              "5efaad29-7f9d-40b1-9d91-c32d500cf669",
              "93b9a162-7a78-4c2c-a31e-4f2dee2dd629",
              "32133cc8-e4cd-48e9-8a0c-41cbad8a2fd7",
              "0345a8f0-1d43-44cc-8038-dd775918b441",
              "0474209d-e25c-4585-892e-74e2e9bdf1ab",
              "aa4be293-8673-4ca1-b4b4-cdaea07297f6",
              "4aac10aa-bf4e-4104-b757-6d28b9bcfe42",
              "49ea7b42-c586-4bf8-adee-daefc779d0df",
              "2f5c6976-8370-430f-a3ca-50e1dce0eddc",
              "c198e0f5-d131-45e6-893e-b787a5cca955",
              "27767f73-44b4-467b-bd4d-872614f1a03f",
              "7c201d6d-edc7-4f6d-8667-6d8fcc2de81c",
              "5ff8a4ed-e5c3-4153-a449-ec1b86590baa",
              "4cad2c07-1b20-4343-872d-388ce947f87e",
              "17b430dc-fa9c-4872-ace3-0a2fc04f552d",
              "f5bb3aec-a2be-4276-b063-9f86d89b8d70",
              "6b64b0ba-130f-436e-bfd7-dd4a82f64ea6",
              "c78bcb7c-6272-442f-aa39-fa42b629b168",
              "a373d9b2-5380-46d7-9593-ebda1e67d624",
              "c544895f-e149-4b0d-a540-98710baac9ec",
              "870bac23-b86d-42cb-bb1f-dfcbc544cff4",
              "21ad23ff-dfcb-43b0-9ac9-85d694dc3668",
              "734fe2b2-9e1d-4bad-9a31-ce70a2174495",
              "b7eda60e-5bcc-4e18-ae19-c3e78ba8f5f5",
              "6eb8e0b7-1fe6-47e2-bf3b-928cfca4d156",
              "dc56a8de-a238-452d-961b-625719bfd57d",
              "bd50a343-c7e0-42cd-b452-1062408e6cd2",
              "40c69b91-675e-4d6f-971f-955bb86f5cce",
              "d2fefd21-cd22-4dcb-aa9e-4369bd71d6d2",
              "90741b25-6247-49cb-b424-66b7ab93a7ec",
              "4ec7622e-2675-4745-a2c7-52413a6a80c9",
              "e84663a4-0e46-425f-844f-abbba6cc05b1",
              "a221d615-21f0-4e0e-b56d-e4c56fc709a4",
              "3b9e8203-f2e0-4e7d-83cb-77ea409334f8",
              "ae52dc6f-a346-4dd5-90cc-01d23df53f0a",
              "796ca30f-e15b-47b0-8322-e01f4fd3afde",
              "414ed2ec-e699-433a-bec2-8cc7a30fd2da",
              "9ac2aaa9-c1ae-4f15-859e-a130e3cfaf20",
              "eeb151ed-e4d5-4998-a084-8c075695bf7c",
              "02efdb24-b69f-47cd-8be3-2ba131f02cda",
              "c2f2a882-ef41-4044-aac5-25441b93db37",
              "3fe89fcd-1b42-4804-8235-4e994714ab52",
              "4aac52d5-46aa-48dc-9f18-4f0e531dada5",
              "0d8e2fea-7f55-4d2d-9ac4-bca2cb66302c",
              "ae5ffd20-98e9-46ad-8e97-281068f13710",
              "a252676a-694b-42d4-afea-82df2ff16042",
              "19f380dd-33ab-46f6-bce8-a71be073cb6d",
              "79c9d1ba-39d6-47cb-b5ce-a525d80d6c15",
              "7ff6089d-1a65-4b92-a4b5-c76330260d5f",
              "bfab137f-aa07-4d73-adf3-4b91e7d35f1b",
              "bac3439b-2d89-44f4-8b4d-16cb7a6db19d",
              "7b25ef94-2703-4867-ae88-93e06e08567b",
              "4b6855ca-b8f8-46a1-8884-279e83ce528b",
              "3a7ad831-dde1-446c-900d-c0670da26a7f",
              "4bfe5054-249f-4b22-84fe-bdd39c2e3ff3",
              "74359dba-bac4-4278-872f-14aa2ffdde52",
              "92c75215-2ee1-41e2-bc52-5c7de85574ac",
              "b76395a6-7e5f-4bd7-bd1b-b36473038ceb",
              "c4672989-737c-437b-9de9-5696bb5f1ad1",
              "a285d15b-c908-45e2-97ae-6d844bf3186f",
              "bc879932-60f3-413a-80c9-b7b8ff305beb",
              "02fd7d20-9f7f-4542-b445-3df46f124089",
              "a4da4e77-a926-4bb0-ba08-52a124966c49",
              "deaf190a-d588-434f-9258-3f9cfdf2e0db",
              "9294465d-88b8-49b9-ac74-7d1f34ae5072",
              "a550732d-d67d-40b8-ace3-10e3761cc693",
              "2e8d323c-0815-4a1a-9349-33d958d44d7c",
              "77d1cab8-6693-49b6-bcee-9be508d3602e",
              "0ba3a4bb-0240-453a-b813-4db0d43c7c77",
              "8cfe4a8e-0d89-4e04-a2e0-92b651b3320b",
              "22e4affd-1933-4eff-93ef-081da8769a4d",
              "03e1a0c9-6916-4e50-8fc0-be269d2742a6",
              "0305bdcb-dba4-4c09-928c-81500a8ebcc2",
              "a1e64cfb-5f7f-49b5-95c8-54082c66b1da",
              "39252518-cd70-4761-bc64-d4e5d612f255",
              "538c69c1-e22a-422d-9650-8f176bdfcb2d",
              "200b9e50-f0b4-4a84-8e6d-09816dae56c1",
              "089bce92-71d7-4382-b4ec-46669427ec0a",
              "2a1ae1a4-8c3f-43a0-bb75-ec2b84ca567c",
              "587d5a5f-12c6-414f-b13b-d02f80d2378a",
              "118af3e8-403c-4e4a-978b-9a9a286f30dc",
              "a1d8b587-66b4-47aa-bc00-a4bae3129263",
              "6ad5b560-d3e6-4730-b658-d460fec86002",
              "3e78d0ff-3a88-41b1-a9d9-de5b30a5ae1c",
              "acde2cb4-f110-4770-9e48-f033c3b50f11",
              "5e6b94e9-7099-44f4-908b-4f3b97c82955",
              "21baa7af-d73e-4d2f-aea2-c9d28f43a490",
              "ad6cc9f5-142a-4cd6-ad80-6cee1b9782a5",
              "e4ea1f4c-9e6a-4cc7-ac3c-c65878a86ea9",
              "6ec5225a-0f86-41aa-9739-0a198f723267",
              "260c1819-6903-41c8-bc49-9ddd57c52a26",
              "ccf04d1e-52b9-4dad-9110-a47b30fd8d49",
              "f72d09bd-6ab9-4af3-bb5f-7a0273bfd12a",
              "db4c0ec9-e2d8-4eae-a0e7-fc4a2cd3667b",
              "66a759e9-f6c9-4141-a81d-14f4a375fbf9",
              "84b36cea-66f4-42e1-bfa7-79386521ed77",
              "ddb07830-958c-494b-b647-1e81e1c26c8f",
              "76f18e67-982d-4e58-bf46-61fb63bb651e",
              "36557a81-ff5c-4f1c-b732-9dec881290fa",
              "e639084a-ea24-450b-9778-82dbca25234d",
              "0e5a7e09-e154-4e7b-9a31-5a85efb05fa1",
              "c6e0a304-7227-498d-a32c-04525f8dab8c",
              "a3d7ee80-d5a5-4314-a90a-6632f8c32c11",
              "0073b9f5-3147-4bf4-8e69-9c8801421e4c",
              "4853d664-82b1-41bd-8f11-cf339fe29b5f",
              "84a4082a-3f01-4dfb-bdec-37953ff6e9a7",
              "4456a03f-7426-4838-a7fb-a782c2947987",
              "35feab80-20be-4972-82d3-62ded9762ad2",
              "feb9fd70-fead-47a8-adf0-3c3e25110ebf",
              "086eb0b5-2d6f-4824-88c3-e039713b6505",
              "dc2e7d35-8b33-45f2-ab2e-39204fca1a52",
              "49ab2b81-5335-4219-a3aa-ce8cefcf4a4d",
              "5801fdd5-ec31-47a5-8629-01c86d9733b6",
              "a24f4553-e45d-425c-b993-68c11ff479ba",
              "4269121a-35a2-48c0-b1bd-9d85f2c8f09f",
              "11c40c2f-7591-4799-bb26-378357728a65",
              "5a705e19-9df4-41ce-bca1-25aca09add7c",
              "bc374543-38bb-4df8-acb3-a9164eaceb6a",
              "5d3bdb58-b80f-48c6-8141-69fd7941539d",
              "e7703a46-4220-4350-be18-c39ec144e8b5",
              "04216cee-b100-4c5b-9c3c-c216fbe6d2b6",
              "5c235dc9-de94-47c6-b636-a678d3ce53bf",
              "b7565c8a-db5b-4f0b-a3fc-d2f4ebae5804",
              "06865e93-d403-4266-a8ad-39df1b01c6b3",
              "101d6776-119e-48a6-9025-0c289451fe42",
              "3af3b52d-2d56-4476-9638-60c8f3ca6e01",
              "5ab22ccb-10d4-4bde-b3ee-4e06375a6cf6",
              "fa99efe1-cf0a-45d2-beb4-a20e58bcd586",
              "851a5759-d4c3-46ac-81b6-1005d36f9700",
              "d3c649cc-c007-440f-a400-e56022baa34d",
              "ce9d522f-c94f-456f-bf39-fe5166863559",
              "c520d49b-4fe7-4b45-afbb-b2090beb4358",
              "af67152f-d364-4a7d-9502-0645084edf8e",
              "7ca997b9-08f0-4757-957c-04a0877aade6",
              "618ed61e-5a3c-4040-9fa8-c9156702f5c9",
              "a7cfb61d-a8fa-4154-8bfb-d7a811a7a20f",
              "b9c5ac74-af93-44d6-865b-2f5fdf3a4c7e",
              "7b18ced6-c0e1-40d0-bead-1a739f9a425f",
              "809b9ccc-82d4-4b3c-9a1f-8e15f1797743",
              "c6972dc7-a3db-4399-b93b-f0502db15fad",
              "6d782bff-1f7e-49c3-9d1a-877dfd237a32",
              "3eae44e8-b954-44f1-b633-130a5b26ef3b",
              "d4359bf8-6a73-4e1d-865c-8932d89505ce",
              "e59ad04d-6c8c-4561-ab84-10ce1e845bcd",
              "02e539d9-35a6-48f7-ba3a-005165dbc02b",
              "fe8b055d-b84e-4a9f-a4e7-2b7567c9e621",
              "3342a5dc-d11b-435e-9eb4-c6627ccee16f",
              "a49d21db-7706-4b36-b15c-e624a779b382",
              "af83ae92-4762-4d25-92bc-5b09892cbd71",
              "18d27eeb-3549-492f-a1d0-76664777b3e1",
              "781db110-0fa5-495e-84f0-bbed9109cd95",
              "b2fff704-45e1-4c0c-a2c3-fd5150471bcb",
              "29a2b3a6-ea18-4fb4-add4-145ea4daaa42",
              "861e9c99-f615-41b9-9181-8f0981a558e7",
              "a60b9e6a-6e2e-4617-b305-b49800cd12b8",
              "39b3ac85-315e-40a2-8717-1a106d994444",
              "3183389b-4464-48d0-bff9-70116a147aad",
              "c2543c25-bb01-4a76-aa16-9d921e4992d9",
              "dd8225e7-7275-4026-b645-d72b22fb42dc",
              "a5311085-689d-43fa-a06f-5780aa57a73f",
              "ad0e3212-a0a7-4d91-a13b-17b232a7f13d",
              "9696d54d-2b87-4f3e-bfc9-b44a1b796ddd",
              "53c70bcb-300d-416e-b2a7-8349349736ec",
              "122d1c99-d37a-4e45-b86b-6e6c92845b46",
            ],
          },
        ],
        totalCount: 173,
      },
      {
        message: "Unable to convert STIX 1.x XML to STIX 2.1 JSON",
        flows: [
          {
            flow: "stix1_x",
            totalCount: 9,
            dids: ["95a28b19-96fd-4e72-8362-1db3fa5c6ee6", "86092a55-7467-4337-be03-2a0fc2c2e6c6", "fbe95709-9527-4bed-8114-c258c277f7fd", "18acdb55-5a1d-4b63-b97f-8b789abcb81e", "249f1928-427f-4c7c-84a7-6d42d2da9fa2", "094ed450-ab2d-4c36-939e-bbcf9c74530e", "b5a7ef8d-8ff2-454a-8a78-3a85b7a563c0", "81b4fb89-143d-4ded-98d0-12c559f8dbb5", "262752bd-63bb-42b9-9067-70e60426122c"],
          },
        ],
        totalCount: 9,
      },
    ],
  };
};

export default generateData();
