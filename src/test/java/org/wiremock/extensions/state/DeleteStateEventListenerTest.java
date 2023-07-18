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
package org.wiremock.extensions.state;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.wiremock.extensions.state.internal.ContextManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(SAME_THREAD)
class DeleteStateEventListenerTest {
    private static final String TEST_URL = "/test";
    private static final CaffeineStore store = new CaffeineStore();
    private static final ContextManager contextManager = new ContextManager(store);

    @RegisterExtension
    public static WireMockExtension wm = WireMockExtension.newInstance()
        .options(
            wireMockConfig().dynamicPort().dynamicHttpsPort().templatingEnabled(true).globalTemplating(true)
                .extensions(new StateExtension(store))
        )
        .build();

    @BeforeAll
    void setupAll() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void setup() {
        wm.resetAll();
        createGetStub();
        createPostStub();
    }


    @Test
    void test_unknownContext_noOtherContext_ok() throws URISyntaxException {
        var context = RandomStringUtils.randomAlphabetic(5);

        getRequest(context);

        await()
            .pollDelay(Duration.ofSeconds(1))
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(context)).isEmpty());
    }

    @Test
    void test_unknownContext_otherContext_ok() throws URISyntaxException {
        var context = RandomStringUtils.randomAlphabetic(5);
        var otherContext = RandomStringUtils.randomAlphabetic(5);

        postRequest(otherContext);
        getRequest(context);

        await()
            .pollDelay(Duration.ofSeconds(1))
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(context)).isEmpty());
        await()
            .pollDelay(Duration.ofSeconds(1))
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(otherContext)).isPresent());
    }

    @Test
    void test_knownContext_noOtherContext_ok() throws URISyntaxException {
        var context = RandomStringUtils.randomAlphabetic(5);

        postRequest(context);
        await()
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(context)).isPresent());

        getRequest(context);
        await()
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(context)).isEmpty());
    }

    @Test
    void test_knownContext_withOtherContext_ok() throws URISyntaxException {
        var context = RandomStringUtils.randomAlphabetic(5);
        var otherContext = RandomStringUtils.randomAlphabetic(5);

        postRequest(context);
        postRequest(otherContext);

        await()
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(context)).isPresent());
        await()
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(otherContext)).isPresent());

        getRequest(context);
        await()
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(context)).isEmpty());
        await()
            .pollInterval(Duration.ofMillis(10))
            .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contextManager.getContext(otherContext)).isPresent());
    }


    private void getRequest(String context) throws URISyntaxException {
        given()
            .accept(ContentType.JSON)
            .get(new URI(String.format("%s%s/%s", wm.getRuntimeInfo().getHttpBaseUrl(), TEST_URL, context)))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .body()
            .jsonPath().get("value");
    }

    private void postRequest(String context) throws URISyntaxException {
        given()
            .accept(ContentType.JSON)
            .body(Map.of("contextValue", context))
            .post(new URI(wm.getRuntimeInfo().getHttpBaseUrl() + TEST_URL + "/" + context))
            .then()
            .statusCode(HttpStatus.SC_OK);
    }

    private void createPostStub() {
        wm.stubFor(
            WireMock.post(urlPathMatching(TEST_URL + "/[^/]+"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{}")
                )
                .withServeEventListener(
                    "recordState",
                    Parameters.from(
                        Map.of(
                            "context", "{{request.pathSegments.[1]}}",
                            "state", Map.of(
                                "stateValue", "{{jsonPath request.body '$.contextValue'}}"
                            )
                        )
                    )
                )
        );
    }

    private void createGetStub() {
        wm.stubFor(
            get(urlPathMatching(TEST_URL + "/[^/]+"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{}")
                )
                .withServeEventListener(
                    "deleteState",
                    Parameters.from(
                        Map.of(
                            "context", "{{request.pathSegments.[1]}}"
                        )
                    )
                )

        );
    }
}