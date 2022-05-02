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

import java.util.List;

/*
 * This is the codegen generated class, except the generated DeltaFile is replaced with org.deltafi.core.domain.api.types.DeltaFile
 */
public class DeltaFiles {
  private Integer offset;

  private Integer count;

  private Integer totalCount;

  private List<DeltaFile> deltaFiles;

  public DeltaFiles() {
  }

  @SuppressWarnings("unused")
  public DeltaFiles(Integer offset, Integer count, Integer totalCount, List<DeltaFile> deltaFiles) {
    this.offset = offset;
    this.count = count;
    this.totalCount = totalCount;
    this.deltaFiles = deltaFiles;
  }

  public Integer getOffset() {
    return offset;
  }

  public void setOffset(Integer offset) {
    this.offset = offset;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public Integer getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(Integer totalCount) {
    this.totalCount = totalCount;
  }

  public List<DeltaFile> getDeltaFiles() {
    return deltaFiles;
  }

  public void setDeltaFiles(List<DeltaFile> deltaFiles) {
    this.deltaFiles = deltaFiles;
  }

  @Override
  public String toString() {
    return "DeltaFiles{" + "offset='" + offset + "'," +"count='" + count + "'," +"totalCount='" + totalCount + "'," +"deltaFiles='" + deltaFiles + "'" +"}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeltaFiles that = (DeltaFiles) o;
        return java.util.Objects.equals(offset, that.offset) &&
                            java.util.Objects.equals(count, that.count) &&
                            java.util.Objects.equals(totalCount, that.totalCount) &&
                            java.util.Objects.equals(deltaFiles, that.deltaFiles);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(offset, count, totalCount, deltaFiles);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private Integer offset;

    private Integer count;

    private Integer totalCount;

    private List<DeltaFile> deltaFiles;

    public DeltaFiles build() {
                  DeltaFiles result = new DeltaFiles();
                      result.offset = this.offset;
          result.count = this.count;
          result.totalCount = this.totalCount;
          result.deltaFiles = this.deltaFiles;
                      return result;
    }

    public Builder offset(Integer offset) {
      this.offset = offset;
      return this;
    }

    public Builder count(Integer count) {
      this.count = count;
      return this;
    }

    public Builder totalCount(
        Integer totalCount) {
      this.totalCount = totalCount;
      return this;
    }

    public Builder deltaFiles(
        List<DeltaFile> deltaFiles) {
      this.deltaFiles = deltaFiles;
      return this;
    }
  }
}
