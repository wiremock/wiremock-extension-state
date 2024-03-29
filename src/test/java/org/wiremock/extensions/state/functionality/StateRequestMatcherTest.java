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
package org.wiremock.extensions.state.functionality;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.Parameters;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StateRequestMatcherTest extends AbstractTestBase {

    private static final String TEST_URL = "/test";

    private void createGetStub(String check, Object configuration) {
        createGetStub(Map.of(
            "hasContext", "{{request.pathSegments.[2]}}",
            check, configuration
        ));
    }

    private void createGetStub(Map<String, Object> configuration) {
        wm.stubFor(
            get(urlPathMatching(TEST_URL + "/check/[^/]+"))
                .andMatching("state-matcher", Parameters.from(configuration))
                .willReturn(WireMock.ok())
        );
    }

    private void createPostStub() {
        wm.stubFor(
            post(urlEqualTo(TEST_URL))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withJsonBody(
                            Json.node(
                                Json.write(Map.of("id", "{{randomValue length=32 type='ALPHANUMERIC' uppercase=false}}"))
                            )
                        )
                )
                .withServeEventListener(
                    "recordState",
                    Parameters.from(
                        Map.of(
                            "context", "{{jsonPath response.body '$.id'}}",
                            "state", Map.of(
                                "stateValue", "{{jsonPath request.body '$.contextValue'}}"
                            ),
                            "list", Map.of(
                                "addLast", Map.of(
                                    "stateValue", "{{jsonPath request.body '$.contextValue'}}"
                                )
                            )
                        )
                    )
                )
        );

        wm.stubFor(
            post(urlPathMatching(TEST_URL + "/[^/]+"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{{jsonPath request.body '$'}}")
                )
                .withServeEventListener(
                    "recordState",
                    Parameters.from(
                        Map.of(
                            "context", "{{jsonPath response.body '$.id'}}",
                            "state", Map.of(
                                "stateValue", "{{jsonPath request.body '$.contextValue' default='null'}}"
                            ),
                            "list", Map.of(
                                "addLast", Map.of(
                                    "stateValue", "{{jsonPath request.body '$.contextValue'}}"
                                )
                            )
                        )
                    )
                )
        );
    }

    private ValidatableResponse getAndAssertContextMatcher(String context, int httpStatus) {
        return given()
            .accept(ContentType.JSON)
            .accept(ContentType.TEXT)
            .get(assertDoesNotThrow(() -> new URI(String.format("%s%s/check/%s", wm.getRuntimeInfo().getHttpBaseUrl(), TEST_URL, context))))
            .then()
            .statusCode(httpStatus);
    }

    private String postAndAssertContextValue(String contextValue) {
        var context = given()
            .accept(ContentType.JSON)
            .body(Map.of("contextValue", contextValue))
            .post(assertDoesNotThrow(() -> new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("id", Matchers.notNullValue())
            .extract()
            .body()
            .jsonPath().getString("id");

        assertThat(context)
            .isNotNull()
            .isNotEmpty();

        return context;
    }

    private String postAndAssertContextValue(String contextName, String contextValue) {
        var context = given()
            .accept(ContentType.JSON)
            .body(new HashMap<String, String>() {{
                put("contextValue", contextValue);
                put("id", contextName);
            }})
            .post(assertDoesNotThrow(() -> new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL + "/" + contextName)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("id", Matchers.notNullValue())
            .extract()
            .body()
            .jsonPath().getString("id");

        assertThat(context)
            .isNotNull()
            .isNotEmpty();

        return context;
    }

    @DisplayName("with matcher 'hasNotContext'")
    @Nested
    public class HasNotContext {
        private final String contextValue = RandomStringUtils.randomAlphabetic(5);

        @BeforeEach
        public void setup() {
            wm.resetAll();
            createPostStub();
        }

        @DisplayName("succeeds when the context does not exist")
        @Test
        void test_findsContext_doesNotExist_ok() {
            createGetStub(Map.of("hasNotContext", "unknownContext"));

            getAndAssertContextMatcher("unknownContext", HttpStatus.SC_OK);
        }

        @DisplayName("fails when the context exists")
        @Test
        void test_findsContext_exist_fail() {
            var context = postAndAssertContextValue(contextValue);
            createGetStub(Map.of("hasNotContext", context));

            getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
        }

        @DisplayName("with other contexts available")
        @Nested
        public class WithOtherContexts {

            @BeforeEach
            public void setup() {
                postAndAssertContextValue(RandomStringUtils.randomAlphabetic(5));
                postAndAssertContextValue(RandomStringUtils.randomAlphabetic(5));
            }

            @DisplayName("succeeds when the context does not exist")
            @Test
            void test_findsContext_doesNotExist_ok() {
                createGetStub(Map.of("hasNotContext", "unknownContext"));

                getAndAssertContextMatcher("unknownContext", HttpStatus.SC_OK);
            }

            @DisplayName("succeeds when the context exists")
            @Test
            void test_findsContext_exist_fail() {
                var context = postAndAssertContextValue(contextValue);
                createGetStub(Map.of("hasNotContext", context));

                getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    @DisplayName("with matcher 'hasContext'")
    @Nested
    public class hasContext {
        private final String contextValue = RandomStringUtils.randomAlphabetic(5);

        @BeforeEach
        public void setup() {
            wm.resetAll();
            createPostStub();
        }

        @DisplayName("fails when the context does not exist")
        @Test
        void test_findsContext_doesNotExist_fail() {
            createGetStub(Map.of("hasContext", "unknownContext"));

            getAndAssertContextMatcher("unknownContext", HttpStatus.SC_NOT_FOUND);
        }

        @DisplayName("succeeds when the context exists")
        @Test
        void test_findsContext_exist_ok() {
            var context = postAndAssertContextValue(contextValue);
            createGetStub(Map.of("hasContext", context));

            getAndAssertContextMatcher(context, HttpStatus.SC_OK);
        }

        @DisplayName("with other contexts available")
        @Nested
        public class WithOtherContexts {

            @BeforeEach
            public void setup() {
                postAndAssertContextValue(RandomStringUtils.randomAlphabetic(5));
                postAndAssertContextValue(RandomStringUtils.randomAlphabetic(5));
            }

            @DisplayName("fails when the context does not exist")
            @Test
            void test_findsContext_doesNotExist_fail() {
                createGetStub(Map.of("hasContext", "unknownContext"));

                getAndAssertContextMatcher("unknownContext", HttpStatus.SC_NOT_FOUND);
            }

            @DisplayName("succeeds when the context exists")
            @Test
            void test_findsContext_exist_ok() {
                var context = postAndAssertContextValue(contextValue);
                createGetStub(Map.of("hasContext", context));

                getAndAssertContextMatcher(context, HttpStatus.SC_OK);
            }
        }

        @DisplayName("with matcher 'hasProperty'")
        @Nested
        public class HasProperty {
            private final String contextValue = RandomStringUtils.randomAlphabetic(5);
            private String context;

            @BeforeEach
            void setup() {
                createPostStub();
                context = postAndAssertContextValue(contextValue);
            }

            @DisplayName("fails when the property does not exist")
            @Test
            void test_hasProperty_propertyDoesNotExist_fail() {
                createGetStub("hasProperty", "unknownValue");

                getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
            }

            @DisplayName("succeeds when the property exists")
            @Test
            void test_hasProperty_propertyExists_ok() {
                createGetStub("hasProperty", "stateValue");

                getAndAssertContextMatcher(context, HttpStatus.SC_OK);
            }

            @DisplayName("fails when the property got deleted")
            @Test
            void test_hasProperty_propertyGotDeleted_fail() {
                createGetStub("hasProperty", "stateValue");
                postAndAssertContextValue(context, null);

                getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
            }
        }

        @DisplayName("with matcher 'hasNotProperty'")
        @Nested
        public class HasNotProperty {
            private final String contextValue = RandomStringUtils.randomAlphabetic(5);
            private String context;

            @BeforeEach
            void setup() {
                createPostStub();
                context = postAndAssertContextValue(contextValue);
            }

            @DisplayName("fails when the property exists")
            @Test
            void test_propertyExists_fail() {
                createGetStub("hasNotProperty", "stateValue");

                getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
            }

            @DisplayName("succeeds when the property does not exist")
            @Test
            void test_propertyDoesNotExists_ok() {
                createGetStub("hasNotProperty", "unknownValue");

                getAndAssertContextMatcher(context, HttpStatus.SC_OK);
            }
        }

        @DisplayName("with matcher 'property'")
        @Nested
        public class PropertyMatcher {
            private final String contextValue = "abcdefghijklmn";
            private String context;

            @BeforeEach
            void setup() {
                createPostStub();
                context = postAndAssertContextValue(contextValue);
            }

            @DisplayName("fails on invalid built-in matchers")
            @Test
            void test_evaluateBuiltinMatchers_fail() {
                createGetStub("property", Map.of("stateValue", Map.of("contains", Map.of("invalid", "invalid"))));
                getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            @DisplayName("can evaluate built-in matchers")
            @Test
            void test_evaluateBuiltinMatchers_ok() {
                createGetStub("property", Map.of("stateValue", Map.of("contains", "defg")));
                getAndAssertContextMatcher(context, HttpStatus.SC_OK);
            }

            @DisplayName("fails on unmatched built-in matchers")
            @Test
            void test_failingBuiltinMatchers_fail() {
                createGetStub("property", Map.of("stateValue", Map.of("contains", "11111")));
                getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
            }

            @DisplayName("can evaluate nested built-in matchers")
            @Test
            void test_evaluateNestedBuiltinMatchers_ok() {
                createGetStub("property",
                    Map.of(
                        "stateValue",
                        Map.of("or",
                            List.of(
                                Map.of("contains", "xyz"),
                                Map.of("contains", "defg")
                            )
                        )
                    )
                );
                getAndAssertContextMatcher(context, HttpStatus.SC_OK);
            }
        }

        @DisplayName("with matcher 'list'")
        @Nested
        public class ListMatcher {
            private final String contextValueOne = "abcdefg";
            private final String contextValueTwo = "hijklmn";
            private final String contextValueThree = "opqrstu";
            private String context;

            @BeforeEach
            void setup() {
                createPostStub();
                context = postAndAssertContextValue(contextValueOne);
                postAndAssertContextValue(context, contextValueTwo);
                postAndAssertContextValue(context, contextValueThree);
            }

            @DisplayName("fails on invalid configuration")
            @Test
            void test_invalidConfiguration_fail() {
                createGetStub("list", "invalid");
                getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            @DisplayName("fails on invalid built-in matchers")
            @Test
            void test_evaluateBuiltinMatchers_fail() {
                createGetStub("list",
                    Map.of(
                        "0",
                        Map.of("stateValue", Map.of("contains", Map.of("invalid", "invalid")))
                    )
                );
                getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            @DisplayName("can evaluate built-in matchers")
            @Test
            void test_evaluateBuiltinMatchers_ok() {
                createGetStub(
                    "list",
                    Map.of(
                        "0",
                        Map.of("stateValue", Map.of("contains", "defg"))
                    )
                );
                getAndAssertContextMatcher(context, HttpStatus.SC_OK);
            }


            @DisplayName("fails on unmatched built-in matchers")
            @Test
            void test_failingBuiltinMatchers_fail() {
                createGetStub(
                    "list",
                    Map.of(
                        "0",
                        Map.of("stateValue", Map.of("contains", "1111"))
                    )
                );

                getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
            }

            @DisplayName("Can access individual ist elements")
            @TestFactory
            List<DynamicTest> test_accessListElements_ok() {
                return Map.of("first", "defg", "last", "qrs", "0", "defg", "-1", "qrs", "1", "jkl")
                    .entrySet()
                    .stream()
                    .map(entry ->
                        DynamicTest.dynamicTest(entry.getKey(), () -> {
                            createGetStub(
                                "list",
                                Map.of(
                                    entry.getKey(),
                                    Map.of("stateValue", Map.of("contains", entry.getValue()))
                                )
                            );
                            getAndAssertContextMatcher(context, HttpStatus.SC_OK);
                        })
                    ).collect(Collectors.toList());
            }

            @DisplayName("fails when accessing unknown list element")
            @Test
            void test_withInvalidListElement_fail() {
                createGetStub(
                    "list",
                    Map.of(
                        "100",
                        Map.of("stateValue", Map.of("contains", "defg"))
                    )
                );
                getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
            }

            @DisplayName("can evaluate nested built-in matchers")
            @Test
            void test_evaluateNestedBuiltinMatchers_ok() {
                createGetStub(
                    "list",
                    Map.of(
                        "0",
                        Map.of(
                            "stateValue",
                            Map.of("or",
                                List.of(
                                    Map.of("contains", "xyz"),
                                    Map.of("contains", "defg")
                                )
                            )
                        )
                    )
                );
                getAndAssertContextMatcher(context, HttpStatus.SC_OK);
            }
        }

        @DisplayName("with updateCount matchers")
        @Nested
        public class UpdateCount {
            private final String contextValue = RandomStringUtils.randomAlphabetic(5);
            private String context;

            @BeforeEach
            void setup() {
                wm.resetAll();
                createPostStub();
                context = postAndAssertContextValue(contextValue);
                postAndAssertContextValue(context, RandomStringUtils.randomAlphabetic(5));
            }

            @DisplayName("with matcher 'updateCountEqualTo'")
            @Nested
            public class UpdateCountEqualTo {

                @DisplayName("succeeds on matching count")
                @Test
                void test_countMatches_ok() {
                    createGetStub("updateCountEqualTo", "2");

                    getAndAssertContextMatcher(context, HttpStatus.SC_OK);
                }

                @DisplayName("fails on invalid configuration")
                @Test
                void test_countInvalid_fail() {
                    createGetStub("updateCountEqualTo", "invalid");

                    getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }

                @DisplayName("fails on non-matching count")
                @Test
                void test_countDoesNotMatch_fail() {
                    createGetStub("updateCountEqualTo", "1");

                    getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
                }
            }

            @DisplayName("with matcher 'updateCountLessThan'")
            @Nested
            public class UpdateCountLessThan {

                @DisplayName("succeeds on matching count")
                @Test
                void test_countMatches_ok() {
                    createGetStub("updateCountLessThan", "3");

                    getAndAssertContextMatcher(context, HttpStatus.SC_OK);
                }

                @DisplayName("fails on invalid configuration")
                @Test
                void test_countInvalid_fail() {
                    createGetStub("updateCountLessThan", "invalid");

                    getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }

                @DisplayName("fails on non-matching count")
                @Test
                void test_countDoesNotMatch_fail() {
                    createGetStub("updateCountLessThan", "2");

                    getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
                }
            }

            @DisplayName("with matcher 'updateCountMoreThan'")
            @Nested
            public class UpdateCountMoreThan {

                @DisplayName("succeeds on matching count")
                @Test
                void test_countMatches_ok() {
                    createGetStub("updateCountMoreThan", "1");

                    getAndAssertContextMatcher(context, HttpStatus.SC_OK);
                }

                @DisplayName("fails on invalid configuration")
                @Test
                void test_countInvalid_fail() {
                    createGetStub("updateCountMoreThan", "invalid");

                    getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }

                @DisplayName("fails on non-matching count")
                @Test
                void test_countDoesNotMatch_fail() {
                    createGetStub("updateCountMoreThan", "2");

                    getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
                }
            }
        }

        @DisplayName("with listSize matchers")
        @Nested
        public class ListSize {

            private final String contextValue = RandomStringUtils.randomAlphabetic(5);
            private String context;

            @BeforeEach
            void setup() {
                wm.resetAll();
                createPostStub();
                context = postAndAssertContextValue(contextValue);
                postAndAssertContextValue(context, RandomStringUtils.randomAlphabetic(5));
            }

            @DisplayName("with matcher 'listSizeEqualTo'")
            @Nested
            public class UpdateCountEqualTo {

                @DisplayName("succeeds on matching count")
                @Test
                void test_countMatches_ok() {
                    createGetStub("listSizeEqualTo", "2");

                    getAndAssertContextMatcher(context, HttpStatus.SC_OK);
                }

                @DisplayName("fails on invalid configuration")
                @Test
                void test_countInvalid_fail() {
                    createGetStub("listSizeEqualTo", "invalid");

                    getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }

                @DisplayName("fails on non-matching count")
                @Test
                void test_countDoesNotMatch_fail() {
                    createGetStub("listSizeEqualTo", "3");

                    getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
                }
            }

            @DisplayName("with matcher 'listSizeLessThan'")
            @Nested
            public class UpdateCountLessThan {

                @DisplayName("succeeds on matching count")
                @Test
                void test_countMatches_ok() {
                    createGetStub("listSizeLessThan", "3");

                    getAndAssertContextMatcher(context, HttpStatus.SC_OK);
                }

                @DisplayName("fails on invalid configuration")
                @Test
                void test_countInvalid_fail() {
                    createGetStub("listSizeLessThan", "invalid");

                    getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }

                @DisplayName("fails on non-matching count")
                @Test
                void test_countDoesNotMatch_fail() {
                    createGetStub("listSizeLessThan", "2");

                    getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
                }
            }

            @DisplayName("with matcher 'listSizeMoreThan'")
            @Nested
            public class UpdateCountMoreThan {

                @DisplayName("succeeds on matching count")
                @Test
                void test_countMatches_ok() {
                    createGetStub("listSizeMoreThan", "1");

                    getAndAssertContextMatcher(context, HttpStatus.SC_OK);
                }

                @DisplayName("fails on invalid configuration")
                @Test
                void test_countInvalid_fail() {
                    createGetStub("listSizeMoreThan", "invalid");

                    getAndAssertContextMatcher(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }

                @DisplayName("fails on non-matching count")
                @Test
                void test_countDoesNotMatch_fail() {
                    createGetStub("listSizeMoreThan", "2");

                    getAndAssertContextMatcher(context, HttpStatus.SC_NOT_FOUND);
                }
            }
        }
    }
}