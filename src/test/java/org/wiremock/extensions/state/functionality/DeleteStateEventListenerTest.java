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
import com.github.tomakehurst.wiremock.extension.Parameters;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DeleteStateEventListenerTest extends AbstractTestBase {
    private void createPostStub(Map<String, Object> configuration) {
        wm.stubFor(
            WireMock.post(urlPathMatching("/state"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{}")
                )
                .withServeEventListener(
                    "recordState",
                    Parameters.from(
                        Map.of(
                            "context", "{{jsonPath request.body '$.contextName'}}",
                            "state", configuration
                        )
                    )
                )
        );
    }

    private void createPostStubList(Map<String, Object> configuration) {
        wm.stubFor(
            WireMock.post(urlPathMatching("/state"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{}")
                )
                .withServeEventListener(
                    "recordState",
                    Parameters.from(
                        Map.of(
                            "context", "{{jsonPath request.body '$.contextName'}}",
                            "list", Map.of("addLast", configuration)
                        )
                    )
                ));
    }

    private void createGetStub(Map<String, Object> configuration) {
        wm.stubFor(
            get(urlPathMatching("/state/[^/]+"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{}")
                )
                .withServeEventListener(
                    "deleteState",
                    Parameters.from(configuration)
                )
        );
    }

    private void createGetStubList(Map<String, Object> configuration) {
        wm.stubFor(
            get(urlPathMatching("/state/[^/]+(/[^/]+)?"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{}")
                )
                .withServeEventListener(
                    "deleteState",
                    Parameters.from(
                        Map.of(
                            "context", "{{request.pathSegments.[1]}}",
                            "list", configuration
                        )
                    )
                )
        );
    }

    private void postContext(String contextName, Map<String, Object> body) {
        var preparedBody = new HashMap<>(body);
        preparedBody.put("contextName", contextName);
        given()
            .contentType(ContentType.JSON)
            .body(preparedBody)
            .post(assertDoesNotThrow(() -> new URI(String.format("%s/%s", wm.getRuntimeInfo().getHttpBaseUrl(), "state"))))
            .then()
            .statusCode(HttpStatus.SC_OK);
    }

    private void getContext(String contextName, int status, Consumer<Map<String, Object>> assertion) {
        var response = given()
            .accept(ContentType.JSON)
            .get(assertDoesNotThrow(() -> new URI(String.format("%s/%s/%s", wm.getRuntimeInfo().getHttpBaseUrl(), "state", contextName))))
            .then()
            .statusCode(status);
        if (assertion != null) {
            Map<String, Object> result = response.extract().body().as(mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
            assertion.accept(result);
        }
    }

    @DisplayName("with existing contexts")
    @Nested
    public class existingContext {

        @DisplayName("when deleting list entries")
        @Nested
        public class DeletingList {

            private final String contextName = "aContextOne";
            private final String property = "listValue";
            private final String valueOne = "listValueOne";
            private final String valueTwo = "listValueTwo";
            private final String valueThree = "listValueThree";

            @BeforeEach
            public void setup() {
                createPostStubList(Map.of(property, "{{jsonPath request.body '$.listValue'}}"));
                postContext(contextName, Map.of(property, valueOne));
                postContext(contextName, Map.of(property, valueTwo));
                postContext(contextName, Map.of(property, valueThree));
                assertThat(contextManager.getContextCopy(contextName))
                    .isPresent()
                    .hasValueSatisfying((context) -> {
                        assertThat(context.getList()).hasSize(3);
                        assertThat(context.getList().get(0)).containsEntry(property, valueOne);
                        assertThat(context.getList().get(1)).containsEntry(property, valueTwo);
                        assertThat(context.getList().get(2)).containsEntry(property, valueThree);
                        assertThat(context.getUpdateCount()).isEqualTo(3);
                    });
            }

            @DisplayName("with deleteFirst")
            @Nested
            public class DeleteFirst {

                @BeforeEach
                public void setup() {
                    createGetStubList(Map.of("deleteFirst", true));
                }

                @DisplayName("deletes first entry")
                @Test
                void test_deleteFirstOne() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueOne)));
                }

                @DisplayName("updates numUpdates")
                @Test
                void test_updatesCounter() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getUpdateCount()).isEqualTo(4));
                }

                @DisplayName("does not delete other entries")
                @Test
                void test_doesNotDeleteOther() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueTwo);
                            assertThat(context.getList().get(1)).containsEntry(property, valueThree);
                        });
                }

                @DisplayName("can delete all entries")
                @Test
                void test_canDeleteAll() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).isEmpty());
                }

                @DisplayName("can delete re-added entries")
                @Test
                void test_canDeleteReAdded() {
                    String valueFour = "listValueFour";
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    postContext(contextName, Map.of(property, valueTwo));
                    postContext(contextName, Map.of(property, valueFour));

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueTwo);
                            assertThat(context.getList().get(1)).containsEntry(property, valueFour);
                        });
                }
            }

            @DisplayName("with deleteLast")
            @Nested
            public class DeleteLast {

                @BeforeEach
                public void setup() {
                    createGetStubList(Map.of("deleteLast", true));
                }

                @DisplayName("deletes last entry")
                @Test
                void test_deleteLastOne() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueThree)));
                }

                @DisplayName("updates numUpdates")
                @Test
                void test_updatesCounter() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getUpdateCount()).isEqualTo(4));
                }

                @DisplayName("does not delete other entries")
                @Test
                void test_doesNotDeleteOther() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueOne);
                            assertThat(context.getList().get(1)).containsEntry(property, valueTwo);
                        });
                }

                @DisplayName("can delete all entries")
                @Test
                void test_canDeleteAll() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).isEmpty());
                }

                @DisplayName("can delete re-added entries")
                @Test
                void test_canDeleteReAdded() {
                    String valueFour = "listValueFour";
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    postContext(contextName, Map.of(property, valueTwo));
                    postContext(contextName, Map.of(property, valueFour));

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueTwo);
                            assertThat(context.getList().get(1)).containsEntry(property, valueFour);
                        });
                }
            }

            @DisplayName("with deleteIndex")
            @Nested
            public class DeleteIndex {

                @BeforeEach
                public void setup() {
                    createGetStubList(Map.of("deleteIndex", "{{request.pathSegments.[2]}}"));
                }

                @DisplayName("can delete first entry")
                @Test
                void test_deleteIndexZero() {
                    getContext(contextName + "/0", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueOne)));
                }

                @DisplayName("updates numUpdates")
                @Test
                void test_updatesCounter() {
                    getContext(contextName + "/0", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getUpdateCount()).isEqualTo(4));
                }


                @DisplayName("can delete middle entry")
                @Test
                void test_deleteIndexOne() {
                    getContext(contextName + "/1", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueTwo)));
                }

                @DisplayName("can delete last entry")
                @Test
                void test_deleteIndexTwo() {
                    getContext(contextName + "/2", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueThree)));
                }


                @DisplayName("does not delete other entries")
                @Test
                void test_doesNotDeleteOther() {
                    getContext(contextName + "/1", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());


                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueOne);
                            assertThat(context.getList().get(1)).containsEntry(property, valueThree);
                        });
                }

                @DisplayName("can delete all entries")
                @Test
                void test_canDeleteAll() {
                    getContext(contextName + "/2", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/1", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/0", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).isEmpty());
                }

                @DisplayName("can delete re-added entries")
                @Test
                void test_canDeleteReAdded() {
                    String valueFour = "listValueFour";
                    getContext(contextName + "/2", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/1", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/0", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    postContext(contextName, Map.of(property, valueTwo));
                    postContext(contextName, Map.of(property, valueFour));

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueTwo);
                            assertThat(context.getList().get(1)).containsEntry(property, valueFour);
                        });
                }
            }

            @DisplayName("with deleteWhere")
            @Nested
            public class DeleteWhere {

                @BeforeEach
                public void setup() {
                    createGetStubList(Map.of("deleteWhere", Map.of("property", property, "value", "{{request.pathSegments.[2]}}")));
                }

                @DisplayName("can delete first entry")
                @Test
                void test_deleteIndexZero() {
                    getContext(contextName + "/" + valueOne, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueOne)));
                }

                @DisplayName("updates numUpdates")
                @Test
                void test_updatesCounter() {
                    getContext(contextName + "/" + valueOne, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getUpdateCount()).isEqualTo(4));
                }


                @DisplayName("can delete middle entry")
                @Test
                void test_deleteIndexOne() {
                    getContext(contextName + "/" + valueTwo, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueTwo)));
                }

                @DisplayName("can delete last entry")
                @Test
                void test_deleteIndexTwo() {
                    getContext(contextName + "/" + valueThree, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).noneSatisfy(it -> assertThat(it).containsEntry(property, valueThree)));
                }


                @DisplayName("does not delete other entries")
                @Test
                void test_doesNotDeleteOther() {
                    getContext(contextName + "/" + valueTwo, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());


                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueOne);
                            assertThat(context.getList().get(1)).containsEntry(property, valueThree);
                        });
                }

                @DisplayName("can delete all entries")
                @Test
                void test_canDeleteAll() {
                    getContext(contextName + "/" + valueTwo, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/" + valueOne, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/" + valueThree, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> assertThat(context.getList()).isEmpty());
                }

                @DisplayName("can delete re-added entries")
                @Test
                void test_canDeleteReAdded() {
                    String valueFour = "listValueFour";
                    getContext(contextName + "/" + valueTwo, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/" + valueOne, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName + "/" + valueThree, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    postContext(contextName, Map.of(property, valueTwo));
                    postContext(contextName, Map.of(property, valueFour));

                    assertThat(contextManager.getContextCopy(contextName))
                        .isPresent()
                        .hasValueSatisfying((context) -> {
                            assertThat(context.getList()).hasSize(2);
                            assertThat(context.getList().get(0)).containsEntry(property, valueTwo);
                            assertThat(context.getList().get(1)).containsEntry(property, valueFour);
                        });
                }
            }
        }

        @DisplayName("when deleting contexts")
        @Nested
        public class DeletingContext {

            @DisplayName("with single exact match")
            @Nested
            public class ExactMatchSingle {
                private final String contextName = "knownContext";
                private final String otherContextName = "otherKnownContext";

                @BeforeEach
                void setup() {
                    createPostStub(Map.of("stateValue", "aValue"));
                    createGetStub(Map.of("context", "{{request.pathSegments.[1]}}"));

                    postContext(contextName, Map.of());
                    postContext(otherContextName, Map.of());
                }

                @DisplayName("deletes context")
                @Test
                void test_deleteContext() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName)).isEmpty();
                }

                @DisplayName("double deletion does not cause an error")
                @Test
                void test_deleteContextTwice() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextName)).isEmpty();
                }

                @DisplayName("does not delete other contexts")
                @Test
                void test_doesNotDeleteOther() {
                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(otherContextName)).isPresent();
                }
            }

            @DisplayName("with array exact match")
            @Nested
            public class ExactMatchArray {
                private final String contextNameOne = "knownContextOne";
                private final String contextNameTwo = "knownContextTwo";
                private final String contextNameThree = "knownContextThree";

                @BeforeEach
                void setup() {
                    createPostStub(Map.of("stateValue", "aValue"));

                    postContext(contextNameOne, Map.of());
                    postContext(contextNameTwo, Map.of());
                    postContext(contextNameThree, Map.of());
                    assertThat(contextManager.getContextCopy(contextNameOne)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameTwo)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isPresent();

                }

                @DisplayName("deletes single context")
                @Test
                void test_deleteContextsSingle() {
                    createGetStub(Map.of("contexts", List.of(contextNameTwo)));

                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameTwo)).isEmpty();
                }

                @DisplayName("deletes multiple context")
                @Test
                void test_deleteContextsMultiple() {
                    createGetStub(Map.of("contexts", List.of(contextNameOne, contextNameThree)));

                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameOne)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameTwo)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isEmpty();
                }

                @DisplayName("deletes all context")
                @Test
                void test_deleteContextsAll() {
                    createGetStub(Map.of("contexts", List.of(contextNameOne, contextNameTwo, contextNameThree)));

                    getContext(contextNameTwo, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameTwo)).isEmpty();
                }

                @DisplayName("double deletion does not cause an error")
                @Test
                void test_deleteContextsTwice() {
                    createGetStub(Map.of("contexts", List.of(contextNameTwo)));
                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameTwo)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameOne)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isPresent();
                }

                @DisplayName("does not delete other contexts")
                @Test
                void test_doesNotDeleteOther() {
                    createGetStub(Map.of("contexts", List.of(contextNameTwo)));

                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameTwo)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameOne)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isPresent();
                }
            }

            @DisplayName("with regex match")
            @Nested
            public class RegexMatch {
                private final String contextNameOne = "knownContextOne";
                private final String contextNameTwo = "knownContextTwo";
                private final String contextNameThree = "knownContextThree";

                @BeforeEach
                void setup() {
                    createPostStub(Map.of("stateValue", "aValue"));

                    postContext(contextNameOne, Map.of());
                    postContext(contextNameTwo, Map.of());
                    postContext(contextNameThree, Map.of());
                    assertThat(contextManager.getContextCopy(contextNameOne)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameTwo)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isPresent();

                }

                @DisplayName("deletes single context")
                @Test
                void test_deleteContextsSingle() {
                    createGetStub(Map.of("contextsMatching", ".*extOn.*"));

                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameOne)).isEmpty();
                }

                @DisplayName("deletes multiple context")
                @Test
                void test_deleteContextsMultiple() {
                    createGetStub(Map.of("contextsMatching", ".*extT.*"));

                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameOne)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameTwo)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isEmpty();
                }

                @DisplayName("deletes all context")
                @Test
                void test_deleteContextsAll() {
                    createGetStub(Map.of("contextsMatching", ".*ext.*"));

                    getContext(contextNameTwo, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameOne)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameTwo)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isEmpty();
                }

                @DisplayName("double deletion does not cause an error")
                @Test
                void test_deleteContextsTwice() {
                    createGetStub(Map.of("contextsMatching", ".*extOn.*"));
                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameOne)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameTwo)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isPresent();
                }

                @DisplayName("does not delete other contexts")
                @Test
                void test_doesNotDeleteOther() {
                    createGetStub(Map.of("contextsMatching", ".*extTw.*"));

                    getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                    assertThat(contextManager.getContextCopy(contextNameTwo)).isEmpty();
                    assertThat(contextManager.getContextCopy(contextNameOne)).isPresent();
                    assertThat(contextManager.getContextCopy(contextNameThree)).isPresent();
                }
            }
        }
    }

    @DisplayName("with configuration errors")
    @Nested
    public class ConfigurationErrors {

        private final String contextName = "aContextName";

        @BeforeEach
        public void setup() {
            createPostStub(Map.of("stateValue", "aValue"));

            postContext(contextName, Map.of());
        }

        @DisplayName("ignores unknown properties")
        @Test
        public void test_ignoreUnknownExtraProperty() {
            createGetStub(Map.of(
                    "context", "{{request.pathSegments.[1]}}",
                    "extraProperty", "extraValue"
                )
            );

            getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
            assertThat(contextManager.getContextCopy(contextName)).isEmpty();
        }

        private void assertConfigurationError(String errorMessage) {
            getContext(contextName, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                (result) -> assertThat(result)
                    .hasEntrySatisfying(
                        "message",
                        message ->
                            assertThat(message)
                                .asInstanceOf(STRING)
                                .contains(errorMessage)
                    )
            );
        }

        private void assertListConfigurationError() {
            assertConfigurationError("Missing/invalid configuration for list");
        }

        private void assertContextDeletionConfigurationError() {
            assertConfigurationError("Missing/invalid configuration for context deletion");
        }

        @Nested
        public class ContextName {

            @DisplayName("fails on missing context name")
            @Test
            public void test_missingContextName_fail() {
                createGetStub(Map.of());

                assertConfigurationError("Missing/invalid configuration for context deletion");
            }

            @DisplayName("fails on empty context name")
            @Test
            public void test_emptyContextName_fail() {
                createGetStub(Map.of("context", ""));

                assertConfigurationError("No context specified");
            }

            @DisplayName("fails on template resulting in empty context name")
            @Test
            public void test_templateToEmptyContextName_fail() {
                createGetStub(Map.of("context", "{{jsonPath '{}' '$.empty'}}"));

                assertConfigurationError("Context cannot be blank");
            }
        }

        @DisplayName("with array exact match")
        @Nested
        public class ExactMatchArray {
            @DisplayName("fails on missing array")
            @Test
            public void test_missingArray() {
                HashMap<String, Object> config = new HashMap<>() {{
                    put("contexts", null);
                }};
                createGetStub(config);

                assertContextDeletionConfigurationError();

                assertThat(contextManager.getContextCopy(contextName)).isPresent();
            }

            @DisplayName("ignores empty array")
            @Test
            public void test_emptyArray() {
                createGetStub(Map.of("contexts", List.of()));

                getContext("any", HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());

                assertThat(contextManager.getContextCopy(contextName)).isPresent();
            }
        }

        @DisplayName("with regex match")
        @Nested
        public class RegexMatch {
            @DisplayName("fails on missing configuration")
            @Test
            public void test_missingConfig() {
                HashMap<String, Object> config = new HashMap<>() {{
                    put("contextsMatching", null);
                }};
                createGetStub(config);

                assertContextDeletionConfigurationError();

                assertThat(contextManager.getContextCopy(contextName)).isPresent();
            }

            @DisplayName("fails on invalid regex")
            @Test
            public void test_invalidRegex() {
                HashMap<String, Object> config = new HashMap<>() {{
                    put("contextsMatching", "\\");
                }};
                createGetStub(config);

                assertContextDeletionConfigurationError();

                assertThat(contextManager.getContextCopy(contextName)).isPresent();
            }
        }

        @DisplayName("with list configuration")
        @Nested
        public class ListConfiguration {

            @DisplayName("reports error when no context is specified")
            @TestFactory
            public List<DynamicTest> test_missingContextForLists() {
                Map<String, Object> body = Map.of("listValue", "aListValue");
                var checks = Map.of(
                    "deleteFirst", true,
                    "deleteLast", true,
                    "deleteIndex", 100,
                    "deleteWhere", Map.of(
                        "property", "listValue",
                        "value", "{{request.pathSegments.[1]}}"
                    )
                );

                return checks.entrySet().stream().map(entry ->
                    DynamicTest.dynamicTest(entry.getKey(), () -> {
                        wm.resetAll();
                        createGetStub(Map.of(entry.getKey(), entry.getValue()));

                        assertContextDeletionConfigurationError();
                    })
                ).collect(Collectors.toList());
            }

            @DisplayName("ignores list config errors for")
            @TestFactory
            public List<DynamicTest> test_listConfigErrors() {
                var checks = Map.of(
                    "deleteFirst", 1,
                    "deleteLast", 1,
                    "deleteIndex", "a string"
                );

                return checks.entrySet().stream().map(entry ->
                    DynamicTest.dynamicTest(entry.getKey(), () -> {
                        wm.resetAll();
                        createGetStubList(Map.of(entry.getKey(), entry.getValue()));

                        getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    })
                ).collect(Collectors.toList());
            }

            @DisplayName("with deleteWhere")
            @Nested
            public class DeleteWhere {

                @DisplayName("reports missing configuration")
                @Test
                public void test_noConfiguration() {
                    createGetStubList(Map.of("deleteWhere", Map.of()));

                    assertListConfigurationError();
                }

                @DisplayName("ignores extra properties")
                @Test
                public void test_extraProperties() {
                    createGetStubList(
                        Map.of(
                            "deleteWhere",
                            Map.of(
                                "property", "aProperty",
                                "value", "aValue",
                                "extraProperty", "aExtraProperty"
                            )
                        )
                    );

                    getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                }

                @DisplayName("reports missing property")
                @Test
                public void test_noProperty() {
                    createGetStubList(Map.of("deleteWhere", Map.of("value", "aValue")));

                    assertListConfigurationError();
                }

                @DisplayName("reports missing value")
                @Test
                public void test_noValue() {
                    createGetStubList(Map.of("deleteWhere", Map.of("property", "aProperty")));
                    assertListConfigurationError();
                }
            }
        }
    }

    @DisplayName("with missing context")
    @Nested
    public class MissingContext {

        @DisplayName("when deleting list entries")
        @Nested
        public class DeletingList {

            private final String contextName = "unknownContext";
            private final String otherContextName = "knownContext";

            @DisplayName("causes no error")
            @TestFactory
            public List<DynamicTest> test_noError() {
                var checks = Map.of(
                    "deleteFirst", true,
                    "deleteLast", true,
                    "deleteIndex", 100,
                    "deleteWhere", Map.of(
                        "property", "listValue",
                        "value", "{{request.pathSegments.[1]}}"
                    )
                );

                return checks.entrySet().stream().map(entry ->
                    DynamicTest.dynamicTest(entry.getKey(), () -> {
                        wm.resetAll();
                        createPostStubList(Map.of("listValue", "{{jsonPath request.body '$.listValue'}}"));
                        createGetStubList(Map.of(entry.getKey(), entry.getValue()));

                        getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                    })
                ).collect(Collectors.toList());
            }

            @DisplayName("does not delete entries in other lists")
            @TestFactory
            public List<DynamicTest> test_doesNotDeleteOther() {
                Map<String, Object> body = Map.of("listValue", "aListValue");
                var checks = Map.of(
                    "deleteFirst", true,
                    "deleteLast", true,
                    "deleteIndex", 100,
                    "deleteWhere", Map.of(
                        "property", "listValue",
                        "value", "{{request.pathSegments.[1]}}"
                    )
                );

                return checks.entrySet().stream().map(entry ->
                    DynamicTest.dynamicTest(entry.getKey(), () -> {
                        wm.resetAll();
                        contextManager.deleteAllContexts(UUID.randomUUID().toString());
                        createPostStubList(Map.of("listValue", "{{jsonPath request.body '$.listValue'}}"));
                        createGetStubList(Map.of(entry.getKey(), entry.getValue()));
                        postContext(otherContextName, body);
                        assertThat(contextManager.getContextCopy(otherContextName)).isPresent();

                        getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                        assertThat(contextManager.getContextCopy(otherContextName)).isPresent().hasValueSatisfying(it -> {
                            assertThat(it.getList()).hasSize(1).first().asInstanceOf(MAP).containsAllEntriesOf(body);
                        });
                    })
                ).collect(Collectors.toList());
            }

        }

        @DisplayName("when deleting a context")
        @Nested
        public class DeletingContexts {

            private final String contextName = "unknownContext";

            @DisplayName("causes no error")
            @Test
            public void test_noError() {
                createGetStub(Map.of("context", "{{request.pathSegments.[1]}}"));

                getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
            }

            @DisplayName("does not delete other contexts")
            @Test
            public void test_doesNotDeleteOther() {
                createPostStub(Map.of("stateValue", "aValue"));
                createGetStub(Map.of("context", "{{request.pathSegments.[1]}}"));

                String otherContextName = "knownContext";
                postContext(otherContextName, Map.of());

                getContext(contextName, HttpStatus.SC_OK, (result) -> assertThat(result).isEmpty());
                assertThat(contextManager.getContextCopy(otherContextName)).isPresent();
            }
        }
    }

}