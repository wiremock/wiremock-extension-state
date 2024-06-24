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
import org.wiremock.extensions.state.internal.api.DeleteStateParameters;
import org.wiremock.extensions.state.internal.model.ResponseTemplateModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static org.wiremock.extensions.state.internal.ExtensionLogger.logger;

/**
 * Event listener to trigger state context deletion.
 * <p>
 * DO NOT REGISTER directly. Use {@link org.wiremock.extensions.state.StateExtension} instead.
 *
 * @see org.wiremock.extensions.state.StateExtension
 */
public class DeleteStateEventListener implements ServeEventListener, StateExtensionMixin {

    private final WireMockServices wireMockServices;
    private final ContextManager contextManager;


    public DeleteStateEventListener(ContextManager contextManager, WireMockServices services) {
        this.contextManager = contextManager;
        this.wireMockServices = services;
    }

    @Override
    public String getName() {
        return "deleteState";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    public void beforeResponseSent(ServeEvent serveEvent, Parameters parameters) {
        final Map<String, Object> model = new HashMap<>();
        model.putAll(wireMockServices.getTemplateEngine().buildModelForRequest(serveEvent));
        model.put("response", ResponseTemplateModel.from(serveEvent.getResponse()));
        var configuration = Json.mapToObject(parameters, DeleteStateParameters.class);
        new ListenerInstance(serveEvent.getId().toString(), model, configuration).run();
    }

    private String renderTemplate(Object context, String value) {
        return wireMockServices.getTemplateEngine().getUncachedTemplate(value).apply(context);
    }

    private class ListenerInstance {
        private final String requestId;
        private final DeleteStateParameters configuration;
        private final Map<String, Object> model;

        ListenerInstance(String requestId, Map<String, Object> model, DeleteStateParameters configuration) {
            this.requestId = requestId;
            this.model = model;
            this.configuration = configuration;
        }

        public void run() {
            Optional.ofNullable(configuration.getList()).ifPresentOrElse(
                listConfig -> handleListDeletion(listConfig, createContextName(configuration.getContext())),
                this::handleContextDeletion
            );
        }

        private void handleContextDeletion() {
            if (configuration.getContext() != null) {
                deleteContext(configuration.getContext());
            } else if (configuration.getContexts() != null) {
                deleteContexts(configuration.getContexts());
            } else if (configuration.getContextsMatching() != null) {
                deleteContextsMatching(configuration.getContextsMatching());
            } else {
                throw createConfigurationError("Missing/invalid configuration for context deletion");
            }
        }

        private void deleteContexts(List<String> rawContexts) {

            var contexts = rawContexts.stream().map(it -> renderTemplate(model, it)).collect(Collectors.toList());
            contextManager.onEach(requestId, context -> {
                if (contexts.contains(context.getContextName())) {
                    contextManager.deleteContext(requestId, context.getContextName());
                }
            });
        }

        private void deleteContextsMatching(String rawRegex) {
            try {
                var regex = renderTemplate(model, rawRegex);
                var pattern = Pattern.compile(regex);
                contextManager.onEach(requestId, context -> {
                    if (pattern.matcher(context.getContextName()).matches()) {
                        contextManager.deleteContext(requestId, context.getContextName());
                    }
                });
            } catch (PatternSyntaxException ex) {
                throw createConfigurationError("Missing/invalid configuration for context deletion: %s", ex.getMessage());
            }
        }

        private void deleteContext(String rawContext) {
            contextManager.deleteContext(requestId, createContextName(rawContext));
        }

        private void handleListDeletion(DeleteStateParameters.ListParameters listConfig, String contextName) {
            if (Boolean.TRUE.equals(listConfig.getDeleteFirst())) {
                deleteFirst(contextName);
            } else if (Boolean.TRUE.equals(listConfig.getDeleteLast())) {
                deleteLast(contextName);
            } else if (StringUtils.isNotBlank(listConfig.getDeleteIndex())) {
                deleteIndex(listConfig, contextName);
            } else if (listConfig.getDeleteWhere() != null &&
                listConfig.getDeleteWhere().getProperty() != null &&
                listConfig.getDeleteWhere().getValue() != null
            ) {
                deleteWhere(listConfig, contextName);
            } else {
                throw createConfigurationError("Missing/invalid configuration for list entry deletion");
            }
        }

        private void deleteFirst(String contextName) {
            contextManager.createOrUpdateContextList(requestId, contextName, maps -> {
                if (!maps.isEmpty()) maps.removeFirst();
                logger().info(contextName, "list::deleteFirst");
            });
        }

        private void deleteLast(String contextName) {
            contextManager.createOrUpdateContextList(requestId, contextName, maps -> {
                if (!maps.isEmpty()) maps.removeLast();
                logger().info(contextName, "list::deleteLast");
            });
        }

        private void deleteIndex(DeleteStateParameters.ListParameters listConfig, String contextName) {
            try {
                var index = Integer.parseInt(renderTemplate(model, listConfig.getDeleteIndex()));
                contextManager.createOrUpdateContextList(requestId, contextName, list -> {
                    list.remove(index);
                    logger().info(contextName, String.format("list::deleteIndex(%d)", index));
                });
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                logger().info(contextName, String.format("Unknown or unparsable list index: '%s' - ignoring", listConfig.getDeleteIndex()));
            }
        }

        private void deleteWhere(DeleteStateParameters.ListParameters listConfig, String contextName) {
            var property = renderTemplate(model, listConfig.getDeleteWhere().getProperty());
            var value = renderTemplate(model, listConfig.getDeleteWhere().getValue());
            contextManager.createOrUpdateContextList(requestId, contextName, list -> {
                var iterator = list.iterator();
                while (iterator.hasNext()) {
                    var element = iterator.next();
                    if (Objects.equals(element.getOrDefault(property, null), value)) {
                        iterator.remove();
                        logger().info(contextName, String.format("list::deleteWhere(property=%s)", property));
                        break;
                    }
                }
            });
        }

        private String createContextName(String rawContext) {
            var context = Optional.ofNullable(rawContext).filter(StringUtils::isNotBlank)
                .map(it -> renderTemplate(model, it))
                .orElseThrow(() -> new ConfigurationException("No context specified"));
            if (StringUtils.isBlank(context)) {
                throw createConfigurationError("Context cannot be blank");
            }
            return context;
        }

    }
}
