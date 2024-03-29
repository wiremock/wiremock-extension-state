/*
 * Copyright (C) 2023 Dirk Bolte
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wiremock.extensions.state.internal.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes(value = {
    @JsonSubTypes.Type(StateRequestMatcherParameters.HasContext.class),
    @JsonSubTypes.Type(StateRequestMatcherParameters.HasNotContext.class)
})
public abstract class StateRequestMatcherParameters {

    public static class HasContext extends StateRequestMatcherParameters {
        private String hasContext;
        private String hasProperty;
        private String hasNotProperty;
        private Map<String, Object> property;
        private ContextList list;
        private Integer updateCountEqualTo;
        private Integer updateCountLessThan;
        private Integer updateCountMoreThan;
        private Integer listSizeEqualTo;
        private Integer listSizeLessThan;
        private Integer listSizeMoreThan;

        public @NotNull String getHasContext() {
            return hasContext;
        }

        public void setHasContext(@NotNull String hasContext) {
            this.hasContext = hasContext;
        }

        public @Nullable String getHasProperty() {
            return hasProperty;
        }

        public void setHasProperty(@NotNull String hasProperty) {
            this.hasProperty = hasProperty;
        }
        public @Nullable String getHasNotProperty() {
            return hasNotProperty;
        }

        public void setHasNotProperty(@NotNull String hasNotProperty) {
            this.hasNotProperty = hasNotProperty;
        }

        public @Nullable Map<String, Object> getProperty() {
            return property;
        }

        public void setProperty(@NotNull Map<String, Object> property) {
            this.property = property;
        }

        public @Nullable ContextList getList() {
            return list;
        }

        public void setList(@NotNull ContextList list) {
            this.list = list;
        }

        public @Nullable Integer getUpdateCountEqualTo() {
            return updateCountEqualTo;
        }

        public void setUpdateCountEqualTo(@NotNull Integer updateCountEqualTo) {
            this.updateCountEqualTo = updateCountEqualTo;
        }

        public @Nullable Integer getUpdateCountLessThan() {
            return updateCountLessThan;
        }

        public void setUpdateCountLessThan(@NotNull Integer updateCountLessThan) {
            this.updateCountLessThan = updateCountLessThan;
        }

        public @Nullable Integer getUpdateCountMoreThan() {
            return updateCountMoreThan;
        }

        public void setUpdateCountMoreThan(@NotNull Integer updateCountMoreThan) {
            this.updateCountMoreThan = updateCountMoreThan;
        }

        public @Nullable Integer getListSizeEqualTo() {
            return listSizeEqualTo;
        }

        public void setListSizeEqualTo(@NotNull Integer listSizeEqualTo) {
            this.listSizeEqualTo = listSizeEqualTo;
        }

        public @Nullable Integer getListSizeLessThan() {
            return listSizeLessThan;
        }

        public void setListSizeLessThan(@NotNull Integer listSizeLessThan) {
            this.listSizeLessThan = listSizeLessThan;
        }

        public @Nullable Integer getListSizeMoreThan() {
            return listSizeMoreThan;
        }

        public void setListSizeMoreThan(@NotNull Integer listSizeMoreThan) {
            this.listSizeMoreThan = listSizeMoreThan;
        }

        public static class ContextList {
            public Map<String, Map<String, Object>> first;
            public Map<String, Map<String, Object>> last;

            @JsonIgnore
            public Map<Integer, Map<String, Map<String, Object>>> indexed = new HashMap<>();

            public @Nullable Map<String, Map<String, Object>> getLast() {
                return last;
            }

            @JsonAlias("-1")
            public void setLast(@NotNull Map<String, Map<String, Object>> last) {
                this.last = last;
            }

            public @Nullable Map<String, Map<String, Object>> getFirst() {
                return first;
            }

            public void setFirst(@NotNull Map<String, Map<String, Object>> first) {
                this.first = first;
            }

            @JsonAnySetter
            public void setIndexed(@NotNull String key, @NotNull Map<String, Map<String, Object>> value) {
                indexed.put(Integer.parseUnsignedInt(key), value);
            }

            @JsonAnyGetter
            public Map<String, Map<String, Map<String, Object>>> getIndexed() {
                return indexed
                    .entrySet()
                    .stream()
                    .collect(toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
            }
        }
    }

    public static class HasNotContext extends StateRequestMatcherParameters {
        private String hasNotContext;

        public @NotNull String getHasNotContext() {
            return hasNotContext;
        }

        public void setHasNotContext(@NotNull String hasNotContext) {
            this.hasNotContext = hasNotContext;
        }
    }
}
