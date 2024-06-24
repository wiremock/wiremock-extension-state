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
package org.wiremock.extensions.state.compatibility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.store.Store;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.wiremock.extensions.state.CaffeineStore;
import org.wiremock.extensions.state.StateExtension;

import java.net.URI;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Sample test for creating a mock for a listing with java.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(SAME_THREAD)
class StateExtensionListExampleTest {

    private static final String TEST_URL = "/listing";
    private static final Store<String, Object> store = new CaffeineStore();
    private static final ObjectMapper mapper = new ObjectMapper();

    @RegisterExtension
    public static WireMockExtension wm = WireMockExtension.newInstance()
        .options(
            wireMockConfig().dynamicPort().dynamicHttpsPort().templatingEnabled(true).globalTemplating(true)
                .extensions(new StateExtension(store))
                .notifier(new ConsoleNotifier(true))
        )
        .build();


    @BeforeEach
    public void setup() throws JsonProcessingException {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        createGetStub();
        createPostStub();
    }

    @Test
    public void testList() {
        var firstNameOne = RandomStringUtils.randomAlphabetic(5);
        var lastNameOne = RandomStringUtils.randomAlphabetic(5);
        var firstNameTwo = RandomStringUtils.randomAlphabetic(5);
        var lastNameTwo = RandomStringUtils.randomAlphabetic(5);

        var idOne = given()
            .accept(ContentType.JSON)
            .body(Map.of("firstName", firstNameOne, "lastName", lastNameOne))
            .post(assertDoesNotThrow(() -> new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("id", Matchers.notNullValue())
            .body("firstName", Matchers.equalTo(firstNameOne))
            .body("lastName", Matchers.equalTo(lastNameOne))
            .extract()
            .body()
            .jsonPath().get("id");
        var idTwo = given()
            .accept(ContentType.JSON)
            .body(Map.of("firstName", firstNameTwo, "lastName", lastNameTwo))
            .post(assertDoesNotThrow(() -> new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("id", Matchers.notNullValue())
            .body("firstName", Matchers.equalTo(firstNameTwo))
            .body("lastName", Matchers.equalTo(lastNameTwo))
            .extract()
            .body()
            .jsonPath().get("id");

        given()
            .accept(ContentType.JSON)
            .get(assertDoesNotThrow(() -> new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("$", Matchers.hasSize(2))
            .body("[0].id", Matchers.equalTo(idOne))
            .body("[0].firstName", Matchers.equalTo(firstNameOne))
            .body("[0].lastName", Matchers.equalTo(lastNameOne))
            .body("[1].id", Matchers.equalTo(idTwo))
            .body("[1].firstName", Matchers.equalTo(firstNameTwo))
            .body("[1].lastName", Matchers.equalTo(lastNameTwo));
    }


    private void createPostStub() throws JsonProcessingException {
        wm.stubFor(
            post(urlPathMatching(TEST_URL))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withJsonBody(
                            mapper.readTree(
                                mapper.writeValueAsString(
                                    Map.of(
                                        "id", "{{randomValue length=32 type='ALPHANUMERIC' uppercase=false}}",
                                        "firstName", "{{jsonPath request.body '$.firstName'}}",
                                        "lastName", "{{jsonPath request.body '$.lastName'}}"
                                    )
                                )
                            )
                        )
                )
                .withServeEventListener(
                    "recordState",
                    Parameters.from(
                        Map.of(
                            "context", "list",
                            "list", Map.of(
                                "addLast", Map.of(
                                    "id", "{{jsonPath response.body '$.id'}}",
                                    "firstName", "{{jsonPath request.body '$.firstName'}}",
                                    "lastName", "{{jsonPath request.body '$.lastName'}}"
                                )
                            )
                        )
                    )
                )
        );
    }

    private void createGetStub() {
        wm.stubFor(
            get(urlPathMatching(TEST_URL))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody(
                            "[\n" +
                                "{{#each (state context='list' property='list' default='[]') }}" +
                                "  {\n" +
                                "    \"id\": \"{{id}}\",\n" +
                                "    \"firstName\": \"{{firstName}}\",\n" +
                                "    \"lastName\": \"{{lastName}}\"" +
                                "  }{{#unless @last}},{{/unless}}\n" +
                                "{{/each}}" +
                                "]"
                        )
                )
        );
    }
}
