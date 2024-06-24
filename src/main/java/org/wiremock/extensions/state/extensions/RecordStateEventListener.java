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
package org.wiremock.extensions.state.extensions;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.core.ConfigurationException;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.apache.commons.lang3.StringUtils;
import org.wiremock.extensions.state.internal.ContextManager;
import org.wiremock.extensions.state.internal.StateExtensionMixin;
import org.wiremock.extensions.state.internal.api.RecordStateParameters;
import org.wiremock.extensions.state.internal.model.ResponseTemplateModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.wiremock.extensions.state.internal.ExtensionLogger.logger;

/**
 * Event listener to trigger state context recording.
 * <p>
 * DO NOT REGISTER directly. Use {@link org.wiremock.extensions.state.StateExtension} instead.
 *
 * @see org.wiremock.extensions.state.StateExtension
 */
public class RecordStateEventListener implements ServeEventListener, StateExtensionMixin {

    private final WireMockServices wireMockServices;
    private final ContextManager contextManager;

    public RecordStateEventListener(ContextManager contextManager, WireMockServices services) {
        this.contextManager = contextManager;
        this.wireMockServices = services;
    }

    public void beforeResponseSent(ServeEvent serveEvent, Parameters parameters) {
        final Map<String, Object> model = new HashMap<>();
        model.putAll(wireMockServices.getTemplateEngine().buildModelForRequest(serveEvent));
        model.put("response", ResponseTemplateModel.from(serveEvent.getResponse()));
        var configuration = Json.mapToObject(parameters, RecordStateParameters.class);
        new ListenerInstance(serveEvent.getId().toString(), model, configuration).run();
    }

    @Override
    public String getName() {
        return "recordState";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }


    private String renderTemplate(Object context, String value) {
        return wireMockServices.getTemplateEngine().getUncachedTemplate(value).apply(context);
    }

    private class ListenerInstance {
        private final String requestId;
        private final RecordStateParameters parameters;
        private final Map<String, Object> model;
        private final String contextName;

        ListenerInstance(String requestId, Map<String, Object> model, RecordStateParameters parameters) {
            this.requestId = requestId;
            this.model = model;
            this.parameters = parameters;
            this.contextName = createContextName();
        }

        void run() {
            handleState();
            handleList();
        }

        private String createContextName() {
            var rawContext = Optional.ofNullable(parameters.getContext())
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new ConfigurationException("no context specified"));
            String context = renderTemplate(model, rawContext);
            if (StringUtils.isBlank(context)) {
                throw createConfigurationError("context cannot be blank");
            }
            return context;
        }

        private void handleState() {
            Optional.ofNullable(parameters.getState())
                .ifPresent(configuration ->
                    contextManager.createOrUpdateContextState(requestId, contextName, getPropertiesFromConfiguration(configuration))
                );
        }

        private Map<String, String> getPropertiesFromConfiguration(Map<String, String> configuration) {
            return configuration.entrySet()
                .stream()
                .map(entry -> Map.entry(entry.getKey(), renderTemplate(model, entry.getValue())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        private void handleList() {
            Optional.ofNullable(parameters.getList())
                .ifPresent(listConfiguration -> {
                        Optional.ofNullable(listConfiguration.getAddFirst())
                            .ifPresent(this::addFirst);
                        Optional.ofNullable(listConfiguration.getAddLast())
                            .ifPresent(this::addLast);
                    }
                );
        }

        private void addFirst(Map<String, String> configuration) {
            contextManager.createOrUpdateContextList(requestId, contextName, list -> {
                list.addFirst(getPropertiesFromConfiguration(configuration));
                logger().info(contextName, "list::addFirst");
            });
        }

        private void addLast(Map<String, String> configuration) {
            contextManager.createOrUpdateContextList(requestId, contextName, list -> {
                list.addLast(getPropertiesFromConfiguration(configuration));
                logger().info(contextName, "list::addLast");
            });
        }
    }
}
