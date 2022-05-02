/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.api.types;

import org.deltafi.common.content.ContentReference;

import java.util.List;

public class Content {
  private String name;

  private List<KeyValue> metadata;

  private ContentReference contentReference;

  public Content() {
  }

  public Content(String name, List<KeyValue> metadata, ContentReference contentReference) {
    this.name = name;
    this.metadata = metadata;
    this.contentReference = contentReference;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<KeyValue> getMetadata() {
    return metadata;
  }

  public void setMetadata(List<KeyValue> metadata) {
    this.metadata = metadata;
  }

  public ContentReference getContentReference() {
    return contentReference;
  }

  public void setContentReference(ContentReference contentReference) {
    this.contentReference = contentReference;
  }

  @Override
  public String toString() {
    return "Content{" + "name='" + name + "'," +"metadata='" + metadata + "'," +"contentReference='" + contentReference + "'" +"}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content that = (Content) o;
        return java.util.Objects.equals(name, that.name) &&
                            java.util.Objects.equals(metadata, that.metadata) &&
                            java.util.Objects.equals(contentReference, that.contentReference);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(name, metadata, contentReference);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String name;

    private List<KeyValue> metadata;

    private ContentReference contentReference;

    public Content build() {
                  Content result = new Content();
                      result.name = this.name;
          result.metadata = this.metadata;
          result.contentReference = this.contentReference;
                      return result;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder metadata(
        List<KeyValue> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder contentReference(
        ContentReference contentReference) {
      this.contentReference = contentReference;
      return this;
    }
  }
}
