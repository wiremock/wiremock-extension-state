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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Sample test for using this extension to record a state in java.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(SAME_THREAD)
class StateExtensionStateExampleTest {

    private static final String TEST_URL = "/test";
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
    public void testCrud() {
        var firstName = RandomStringUtils.randomAlphabetic(5);
        var lastName = RandomStringUtils.randomAlphabetic(5);

        var id = given()
            .accept(ContentType.JSON)
            .body(Map.of(
                    "firstName", firstName,
                    "lastName", lastName
                )
            )
            .post(assertDoesNotThrow(() -> new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("id", Matchers.notNullValue())
            .body("firstName", Matchers.equalTo(firstName))
            .body("lastName", Matchers.equalTo(lastName))
            .extract()
            .body()
            .jsonPath().get("id");

        given()
            .accept(ContentType.JSON)
            .get(assertDoesNotThrow(() -> new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL + "/" + id)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("id", Matchers.equalTo(id))
            .body("firstName", Matchers.equalTo(firstName))
            .body("lastName", Matchers.equalTo(lastName));
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
                            "context", "{{jsonPath response.body '$.id'}}",
                            "state", Map.of(
                                "id", "{{jsonPath response.body '$.id'}}",
                                "firstName", "{{jsonPath request.body '$.firstName'}}",
                                "lastName", "{{jsonPath request.body '$.lastName'}}"
                            )
                        )
                    )
                )
        );
    }

    private void createGetStub() throws JsonProcessingException {
        wm.stubFor(
            get(urlPathMatching(TEST_URL + "/[^/]+"))
                .andMatching("state-matcher", Parameters.from(
                    Map.of("hasContext", "{{request.pathSegments.[1]}}"))
                )
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withJsonBody(
                            mapper.readTree(
                                mapper.writeValueAsString(Map.of(
                                        "id", "{{state context=request.pathSegments.[1] property='id'}}",
                                        "firstName", "{{state context=request.pathSegments.[1] property='firstName'}}",
                                        "lastName", "{{state context=request.pathSegments.[1] property='lastName'}}"
                                    )
                                )
                            )
                        )
                )
        );
    }
}
