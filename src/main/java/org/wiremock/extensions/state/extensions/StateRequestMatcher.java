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
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.jetbrains.annotations.NotNull;
import org.wiremock.extensions.state.internal.ContextManager;
import org.wiremock.extensions.state.internal.StateExtensionMixin;
import org.wiremock.extensions.state.internal.api.StateRequestMatcherParameters;
import org.wiremock.extensions.state.internal.api.StateRequestMatcherParameters.HasContext;
import org.wiremock.extensions.state.internal.api.StateRequestMatcherParameters.HasNotContext;
import org.wiremock.extensions.state.internal.model.Context;
import org.wiremock.extensions.state.internal.model.ContextTemplateModel;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.common.LocalNotifier.notifier;
import static org.wiremock.extensions.state.internal.ExtensionLogger.logger;

/**
 * Request matcher for state.
 * <p>
 * DO NOT REGISTER directly. Use {@link org.wiremock.extensions.state.StateExtension} instead.
 *
 * @see org.wiremock.extensions.state.StateExtension
 */
public class StateRequestMatcher extends RequestMatcherExtension implements StateExtensionMixin {

    private final TemplateEngine templateEngine;
    private final ContextManager contextManager;

    public StateRequestMatcher(ContextManager contextManager, TemplateEngine templateEngine) {
        this.contextManager = contextManager;
        this.templateEngine = templateEngine;
    }

    private static List<Map.Entry<ContextMatcher, Object>> getMatchers(HasContext parameters) {
        return ContextMatcher.from(parameters);
    }

    private static StringValuePattern mapToPatternMatcher(Map<String, Object> map) {
        try {
            return Json.mapToObject(map, StringValuePattern.class);
        } catch (Exception ex) {
            var msg = String.format("Cannot create pattern matcher: %s", ex.getMessage());
            var prefixed = String.format("%s: %s", "StateRequestMatcher", msg);
            notifier().error(prefixed);
            throw new ConfigurationException(prefixed);
        }
    }

    private static <T> T cast(Object object, Class<T> target) {
        try {
            return target.cast(object);
        } catch (ClassCastException ex) {
            var msg = String.format("Configuration has invalid type: %s", ex.getMessage());
            var prefixed = String.format("%s: %s", "StateRequestMatcher", msg);
            notifier().error(prefixed);
            throw new ConfigurationException(prefixed);
        }
    }

    @Override
    public String getName() {
        return "state-matcher";
    }

    @Override
    public MatchResult match(Request request, Parameters parameters) {
        var parsedParameters = Json.mapToObject(parameters, StateRequestMatcherParameters.class);
        var model = new HashMap<String, Object>(Map.of("request", RequestTemplateModel.from(request)));
        if (parsedParameters instanceof HasContext) {
            return hasContext((HasContext) parsedParameters, model);
        } else if (parsedParameters instanceof HasNotContext) {
            return hasNotContext((HasNotContext) parsedParameters, model);
        } else {
            throw createConfigurationError("Parameters should only contain 'hasContext' or 'hasNotContext'");
        }
    }

    private MatchResult hasContext(HasContext parameters, Map<String, Object> model) {
        return contextManager.getContextCopy(renderTemplate(model, parameters.getHasContext()))
            .map(context -> {
                model.put("context", ContextTemplateModel.from(context));
                @SuppressWarnings("unchecked") HasContext renderedParameters = Json.mapToObject((Map<String, Object>) renderTemplateRecursively(model, Json.objectToMap(parameters)), HasContext.class);
                List<Map.Entry<ContextMatcher, Object>> matchers = getMatchers(renderedParameters);
                if (matchers.isEmpty()) {
                    logger().info(context, "hasContext matched");
                    return MatchResult.exactMatch();
                } else {
                    return calculateMatch(context, matchers);
                }
            }).orElseGet(MatchResult::noMatch);
    }

    private MatchResult calculateMatch(Context context, List<Map.Entry<ContextMatcher, Object>> matchers) {
        var results = matchers
            .stream()
            .map(it -> it.getKey().evaluate(context, it.getValue()))
            .collect(Collectors.toList());

        return MatchResult.aggregate(results);
    }

    private MatchResult hasNotContext(HasNotContext parameters, Map<String, Object> model) {
        var context = renderTemplate(model, parameters.getHasNotContext());
        if (contextManager.getContextCopy(context).isEmpty()) {
            logger().info(context, "hasNotContext matched");
            return MatchResult.exactMatch();
        } else {
            return MatchResult.noMatch();
        }
    }

    String renderTemplate(Object context, String value) {
        return templateEngine.getUncachedTemplate(value).apply(context);
    }

    Object renderTemplateRecursively(Object context, Object value) {
        if (value instanceof Collection) {
            @SuppressWarnings("unchecked") Collection<Object> castedCollection = cast(value, Collection.class);
            return castedCollection.stream().map(it -> renderTemplateRecursively(context, it)).collect(Collectors.toList());
        } else if (value instanceof Map) {
            var newMap = new HashMap<String, Object>();
            @SuppressWarnings("unchecked") Map<String, Object> castedMap = cast(value, Map.class);
            castedMap.forEach((k, v) -> newMap.put(
                renderTemplate(context, k),
                renderTemplateRecursively(context, v)
            ));
            return newMap;
        } else {
            return renderTemplate(context, value.toString());
        }
    }

    private enum ContextMatcher {

        property(
            HasContext::getProperty,
            (Context c, Object object) -> {
                @SuppressWarnings("unchecked") Map<String, Map<String, Object>> mapValue = cast(object, Map.class);
                var results = mapValue.entrySet().stream().map(entry -> {
                    var patterns = mapToPatternMatcher(entry.getValue());
                    var propertyValue = c.getProperties().get(entry.getKey());
                    return patterns.match(propertyValue);
                }).collect(Collectors.toList());
                if (results.isEmpty()) {
                    logger().info(c, "No interpretable matcher was found, defaulting to 'exactMatch'");
                    return MatchResult.exactMatch();
                } else {
                    return MatchResult.aggregate(results);
                }
            }),

        list(
            HasContext::getList,
            (Context c, Object object) -> {
                var mapValue = cast(object, HasContext.ContextList.class);
                LinkedList<MatchResult> allResults = new LinkedList<>();
                if (mapValue.getFirst() != null) {
                    allResults.push(evaluateListMatcher(c, mapValue.getFirst(), () -> c.getList().getFirst()));
                }
                if (mapValue.getLast() != null) {
                    allResults.push(evaluateListMatcher(c, mapValue.getLast(), () -> c.getList().getLast()));
                }
                if (mapValue.getIndexed() != null) {
                    mapValue.getIndexed().forEach((key, value) -> allResults.push(evaluateListMatcher(c, value, () -> c.getList().get(Integer.parseInt(key)))));
                }
                return MatchResult.aggregate(allResults);
            }),
        hasProperty(
            HasContext::getHasProperty,
            (Context c, Object object) -> {
                var stringValue = cast(object, String.class);
                return toMatchResult(c.getProperties().containsKey(stringValue));
            }),
        hasNotProperty(
            HasContext::getHasNotProperty,
            (Context c, Object object) -> {
                var stringValue = cast(object, String.class);
                return toMatchResult(!c.getProperties().containsKey(stringValue));
            }),
        updateCountEqualTo(
            HasContext::getUpdateCountEqualTo,
            (Context c, Object object) -> {
                var value = cast(object, Integer.class);
                return toMatchResult(c.getUpdateCount().equals(value));
            }),
        updateCountLessThan(
            HasContext::getUpdateCountLessThan,
            (Context c, Object object) -> {
                var value = cast(object, Integer.class);
                return toMatchResult(c.getUpdateCount() < value);
            }),
        updateCountMoreThan(
            HasContext::getUpdateCountMoreThan,
            (Context c, Object object) -> {
                var value = cast(object, Integer.class);
                return toMatchResult(c.getUpdateCount() > value);
            }),
        listSizeEqualTo(
            HasContext::getListSizeEqualTo,
            (Context c, Object object) -> {
                var value = cast(object, Integer.class);
                return toMatchResult(c.getList().size() == value);
            }),
        listSizeLessThan(
            HasContext::getListSizeLessThan,
            (Context c, Object object) -> {
                var value = cast(object, Integer.class);
                return toMatchResult(c.getList().size() < value);
            }),
        listSizeMoreThan(
            HasContext::getListSizeMoreThan,
            (Context c, Object object) -> {
                var value = cast(object, Integer.class);
                return toMatchResult(c.getList().size() > value);
            });

        private final Function<HasContext, Object> getConfiguration;
        private final BiFunction<Context, Object, MatchResult> evaluator;

        ContextMatcher(Function<HasContext, Object> getConfiguration, BiFunction<Context, Object, MatchResult> evaluator) {
            this.getConfiguration = getConfiguration;
            this.evaluator = evaluator;
        }

        private static MatchResult evaluateListMatcher(Context c, Map<String, Map<String, Object>> listIndexEntry, Supplier<Map<String, String>> listEntrySupplier) {
            try {
                var listEntry = listEntrySupplier.get();
                List<MatchResult> results = listIndexEntry.entrySet().stream().map(entry -> {
                    var patterns = mapToPatternMatcher(entry.getValue());
                    var propertyValue = listEntry.get(entry.getKey());
                    return patterns.match(propertyValue);
                }).collect(Collectors.toList());
                if (results.isEmpty()) {
                    logger().info(c, "No interpretable matcher was found, defaulting to 'exactMatch'");
                    return MatchResult.exactMatch();
                } else {
                    return MatchResult.aggregate(results);
                }
            } catch (IndexOutOfBoundsException ex) {
                logger().info(c, "List entry does not exist");
                return MatchResult.noMatch();
            }
        }

        private static MatchResult toMatchResult(boolean result) {
            return result ? MatchResult.exactMatch() : MatchResult.noMatch();
        }

        public static List<Map.Entry<ContextMatcher, Object>> from(@NotNull HasContext hasContext) {
            return Arrays.stream(values())
                .map(matcher -> {
                        var configuration = matcher.getConfiguration.apply(hasContext);
                        return configuration != null ? Map.entry(matcher, configuration) : null;
                    }
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        public MatchResult evaluate(Context context, Object value) {
            return this.evaluator.apply(context, value);
        }
    }
}
