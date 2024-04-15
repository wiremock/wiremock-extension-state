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

import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.common.JsonException;
import com.github.tomakehurst.wiremock.extension.responsetemplating.helpers.HandlebarsHelper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.wiremock.extensions.state.internal.model.Context;
import org.wiremock.extensions.state.internal.ContextManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.wiremock.extensions.state.internal.ExtensionLogger.logger;

/**
 * Response templating helper to access state.
 * <p>
 * DO NOT REGISTER directly. Use {@link org.wiremock.extensions.state.StateExtension} instead.
 *
 * @see org.wiremock.extensions.state.StateExtension
 */
public class StateHandlerbarHelper extends HandlebarsHelper<Object> {

    private final ContextManager contextManager;

    public StateHandlerbarHelper(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    @Override
    public Object apply(Object o, Options options) {
        String contextName = Optional.ofNullable(options.hash("context")).map(Object::toString).orElse(null);
        String property = Optional.ofNullable(options.hash("property")).map(Object::toString).orElse(null);
        String list = Optional.ofNullable(options.hash("list")).map(Object::toString).orElse(null);
        String defaultValue = Optional.ofNullable(options.hash("default")).map(Object::toString).orElse(null);
        if (StringUtils.isEmpty(contextName)) {
            return handleError("'context' cannot be empty");
        }
        if (StringUtils.isNotBlank(property) == StringUtils.isNotBlank(list)) {
            return handleError("Either 'property' or 'list' has to be set");
        }
        if (StringUtils.isNotBlank(property)) {
            return getProperty(contextName, property, defaultValue)
                .orElseGet(() -> {
                    logger().info(contextName, String.format("property '%s' not found, using `null`", property));
                    return "";
                });
        } else {
            return getList(contextName, list)
                .orElseGet(() ->
                    Optional.ofNullable(defaultValue)
                        .orElseGet(() -> {
                            logger().info(contextName, "list not found, using `null`");
                            return "";
                        })
                );

        }
    }

    private Optional<Object> getProperty(String contextName, String property, String defaultValue) {
        return contextManager.getContextCopy(contextName)
            .map(context ->
                Stream.of(SpecialProperties.values())
                    .filter(it -> it.name().equals(property))
                    .findFirst()
                    .map(it -> it.getFromContext(context))
                    .orElseGet(() -> context.getProperties().get(property))
            )
            .or(() -> convertToPropertySpecificDefault(contextName, property, defaultValue))
            .map((obj) -> {
                logger().info(contextName, String.format("handlebar(property=%s)", property));
                return obj;
            });
    }

    private Optional<Object> convertToPropertySpecificDefault(String contextName, String property, String defaultValue) {
        return Stream.of(SpecialProperties.values())
            .filter(it -> it.name().equals(property))
            .findFirst()
            .map(specialProperty ->
                Optional.ofNullable(defaultValue)
                    .map(it -> specialProperty.convertDefaultValue(contextName, it))
                    .orElseGet(() -> specialProperty.getBuiltInDefault(contextName)))
            .or(() -> Optional.ofNullable(defaultValue));
    }

    private Optional<Object> getList(String contextName, String list) {
        return contextManager.getContextCopy(contextName)
            .flatMap(context -> {
                try {
                    return Optional.of(JsonPath.read(context.getList(), list));
                } catch (PathNotFoundException e) {
                    logger().info(contextName, "Path query failed: " + e.getMessage());
                    return Optional.empty();
                }
            })
            .map((obj) -> {
                logger().info(contextName, "handlebar(list)");
                return obj;
            });
    }

    private enum SpecialProperties {
        updateCount(Context::getUpdateCount, (contextName) -> 0, (contextName, it) -> it),
        listSize((context) -> context.getList().size(), (contextName) -> 0, (contextName, it) -> it),
        @SuppressWarnings("rawtypes") list(
            Context::getList,
            (contextName) -> List.of(),
            (contextName, defaultValue) -> Optional.ofNullable(defaultValue)
                .map(it -> {
                    try {
                        return Json.read(it, ArrayList.class);
                    } catch (JsonException ex) {
                        logger().error(contextName, "default for list property is not a JSON list - fallback to empty list: " + defaultValue);
                        return null;
                    }
                })
                .or(() -> Optional.of(new ArrayList()))
                .map(it -> it)
                .get()
        );

        private final Function<Context, Object> contextExtractor;
        private final Function<String, Object> builtInDefault;
        private final BiFunction<String, String, Object> defaultConverter;

        SpecialProperties(
            Function<Context, Object> contextExtractor,
            Function<String, Object> builtInDefault,
            BiFunction<String, String, Object> defaultConverter
        ) {
            this.contextExtractor = contextExtractor;
            this.builtInDefault = builtInDefault;
            this.defaultConverter = defaultConverter;
        }

        public Object getFromContext(Context context) {
            return contextExtractor.apply(context);
        }

        public Object convertDefaultValue(String contextName, String defaultValue) {
            logger().info(contextName, String.format("property '%s' using configured default value", name()));
            return defaultConverter.apply(contextName, defaultValue);
        }

        public Object getBuiltInDefault(String contextName) {
            logger().info(contextName, String.format("property '%s' using built-in default value", name()));
            return builtInDefault.apply(contextName);
        }
    }

}
