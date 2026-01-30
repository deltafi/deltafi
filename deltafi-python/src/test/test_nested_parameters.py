#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

from typing import List, Optional

from pydantic import BaseModel, Field


class AuthConfig(BaseModel):
    auth_type: str = Field(default="NONE", description="Authentication type: NONE, BASIC, or BEARER")
    username: Optional[str] = Field(default=None, description="Username for BASIC auth")


class EndpointConfig(BaseModel):
    url: str = Field(description="The URL to connect to")
    timeout_ms: int = Field(default=30000, description="Connection timeout in milliseconds")
    auth: Optional[AuthConfig] = Field(default=None, description="Authentication settings")


class NestedParameters(BaseModel):
    primary_endpoint: EndpointConfig = Field(description="Primary endpoint configuration")
    fallback_endpoints: List[EndpointConfig] = Field(default=[], description="Fallback endpoints")


def test_nested_parameter_schema_has_descriptions():
    """Verify nested parameter structures include descriptions in JSON schema."""
    schema = NestedParameters.model_json_schema()

    # Top-level properties should have descriptions
    assert schema["properties"]["primary_endpoint"]["description"] == "Primary endpoint configuration"
    assert schema["properties"]["fallback_endpoints"]["description"] == "Fallback endpoints"

    # Nested EndpointConfig should be in $defs and have descriptions
    endpoint_schema = schema["$defs"]["EndpointConfig"]
    assert endpoint_schema["properties"]["url"]["description"] == "The URL to connect to"
    assert endpoint_schema["properties"]["timeout_ms"]["description"] == "Connection timeout in milliseconds"
    assert endpoint_schema["properties"]["auth"]["description"] == "Authentication settings"

    # Deeply nested AuthConfig should also have descriptions
    auth_schema = schema["$defs"]["AuthConfig"]
    assert auth_schema["properties"]["auth_type"]["description"] == "Authentication type: NONE, BASIC, or BEARER"
    assert auth_schema["properties"]["username"]["description"] == "Username for BASIC auth"


def test_nested_parameter_schema_structure():
    """Verify the overall structure of nested parameter schema."""
    schema = NestedParameters.model_json_schema()

    # Should have $defs for nested models
    assert "$defs" in schema
    assert "EndpointConfig" in schema["$defs"]
    assert "AuthConfig" in schema["$defs"]

    # primary_endpoint should reference EndpointConfig
    assert "$ref" in schema["properties"]["primary_endpoint"]
    assert schema["properties"]["primary_endpoint"]["$ref"] == "#/$defs/EndpointConfig"

    # fallback_endpoints should be an array of EndpointConfig references
    assert schema["properties"]["fallback_endpoints"]["type"] == "array"
    assert "$ref" in schema["properties"]["fallback_endpoints"]["items"]
