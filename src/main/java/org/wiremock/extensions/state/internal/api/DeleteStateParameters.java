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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteStateParameters {
    private String context;
    private String contextsMatching;

    private List<String> contexts;
    private ListParameters list;

    public ListParameters getList() {
        return list;
    }

    public void setList(ListParameters list) {
        this.list = list;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public List<String> getContexts() {
        return contexts;
    }

    public void setContexts(List<String> contexts) {
        this.contexts = contexts;
    }

    public String getContextsMatching() {
        return contextsMatching;
    }

    public void setContextsMatching(String contextsMatching) {
        this.contextsMatching = contextsMatching;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListParameters {
        private Boolean deleteFirst;
        private Boolean deleteLast;
        private String deleteIndex;
        private Where deleteWhere;

        public Boolean getDeleteFirst() {
            return deleteFirst;
        }

        public void setDeleteFirst(Boolean deleteFirst) {
            this.deleteFirst = deleteFirst;
        }

        public Boolean getDeleteLast() {
            return deleteLast;
        }

        public void setDeleteLast(Boolean deleteLast) {
            this.deleteLast = deleteLast;
        }

        public String getDeleteIndex() {
            return deleteIndex;
        }

        public void setDeleteIndex(String deleteIndex) {
            this.deleteIndex = deleteIndex;
        }

        public Where getDeleteWhere() {
            return deleteWhere;
        }

        public void setDeleteWhere(Where deleteWhere) {
            this.deleteWhere = deleteWhere;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Where {
            private String property;
            private String value;

            public Where() {
            }
            public Where(String property, String value) {
                this.property = property;
                this.value = value;
            }

            public String getProperty() {
                return property;
            }

            public void setProperty(String property) {
                this.property = property;
            }

            public String getValue() {
                return value;
            }

            public void setValue(String value) {
                this.value = value;
            }
        }
    }
}
