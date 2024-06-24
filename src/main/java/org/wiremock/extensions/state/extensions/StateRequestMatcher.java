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
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.wiremock.extensions.state.internal.ContextManager;
import org.wiremock.extensions.state.internal.StateExtensionMixin;
import org.wiremock.extensions.state.internal.model.Context;
import org.wiremock.extensions.state.internal.model.ContextTemplateModel;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
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

    private final WireMockServices wireMockServices;
    private final ContextManager contextManager;

    public StateRequestMatcher(ContextManager contextManager, WireMockServices services) {
        this.contextManager = contextManager;
        this.wireMockServices = services;
    }

    private static List<Map.Entry<ContextMatcher, Object>> getMatchers(Parameters parameters) {
        return parameters
            .entrySet()
            .stream()
            .filter(it -> ContextMatcher.from(it.getKey()) != null)
            .map(it -> Map.entry(ContextMatcher.from(it.getKey()), it.getValue()))
            .collect(Collectors.toUnmodifiableList());
    }

    private static <T> T mapToObject(Map<String, Object> map, Class<T> klass) {
        try {
            return Json.mapToObject(map, klass);
        } catch (Exception ex) {
            var msg = String.format("Cannot create pattern matcher: %s", ex.getMessage());
            var prefixed = String.format("%s: %s", "StateRequestMatcher", msg);
            notifier().error(prefixed);
            throw new ConfigurationException(prefixed);
        }
    }

    private static <T> T cast(Object object, Class<T> target) {
        try {
            //noinspection unchecked
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
        var model = wireMockServices.getTemplateEngine().buildModelForRequest(request);
        return Optional
            .ofNullable(parameters.getString("hasContext", null))
            .map(template -> hasContext(model, parameters, template))
            .or(() -> Optional.ofNullable(parameters.getString("hasNotContext", null)).map(template -> hasNotContext(model, template)))
            .orElseThrow(() -> createConfigurationError("Parameters should only contain 'hasContext' or 'hasNotContext'"));
    }

    private MatchResult hasContext(Map<String, Object> model, Parameters parameters, String template) {
        return contextManager.getContextCopy(renderTemplate(model, template))
            .map(context -> {
                List<Map.Entry<ContextMatcher, Object>> matchers = getMatchers(parameters);
                if (matchers.isEmpty()) {
                    logger().info(context, "hasContext matched");
                    return MatchResult.exactMatch();
                } else {
                    return calculateMatch(model, context, matchers);
                }
            }).orElseGet(MatchResult::noMatch);
    }

    private MatchResult calculateMatch(Map<String, Object> model, Context context, List<Map.Entry<ContextMatcher, Object>> matchers) {
        model.put("context", ContextTemplateModel.from(context));
        var results = matchers
            .stream()
            .map(it -> it.getKey().evaluate(context, renderTemplateRecursively(model, it.getValue())))
            .collect(Collectors.toList());

        return MatchResult.aggregate(results);
    }

    private MatchResult hasNotContext(Map<String, Object> model, String template) {
        var context = renderTemplate(model, template);
        if (contextManager.getContextCopy(context).isEmpty()) {
            logger().info(context, "hasNotContext matched");
            return MatchResult.exactMatch();
        } else {
            return MatchResult.noMatch();
        }
    }

    String renderTemplate(Object context, String value) {
        return wireMockServices.getTemplateEngine().getUncachedTemplate(value).apply(context);
    }

    @SuppressWarnings("unchecked")
    Object renderTemplateRecursively(Object context, Object value) {
        if (value instanceof Collection) {
            Collection<Object> castedCollection = cast(value, Collection.class);
            return castedCollection.stream().map(it -> renderTemplateRecursively(context, it)).collect(Collectors.toList());
        } else if (value instanceof Map) {
            var newMap = new HashMap<String, Object>();
            Map<String, Object> castedMap = cast(value, Map.class);
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

        property((Context c, Object object) -> {
            @SuppressWarnings("unchecked") Map<String, Map<String, Object>> mapValue = cast(object, Map.class);
            var results = mapValue.entrySet().stream().map(entry -> {
                var patterns = mapToObject(entry.getValue(), StringValuePattern.class);
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

        list((Context c, Object object) -> {
            @SuppressWarnings("unchecked") Map<String, Map<String, Map<String, Object>>> mapValue = cast(object, Map.class);
            var allResults = mapValue.entrySet().stream().map(listIndexEntry -> {
                Map<String, String> listEntry;
                switch (listIndexEntry.getKey()) {
                    case "last":
                    case "-1":
                        listEntry = c.getList().getLast();
                        break;
                    case "first":
                        listEntry = c.getList().getFirst();
                        break;
                    default:
                        listEntry = withConvertedNumberGet(c, listIndexEntry.getKey(), (context, value) -> c.getList().get(value.intValue()));
                }
                if (listEntry == null) {
                    return MatchResult.noMatch();
                } else {
                    List<MatchResult> results = listIndexEntry.getValue().entrySet().stream().map(entry -> {
                        var patterns = mapToObject(entry.getValue(), StringValuePattern.class);
                        var propertyValue = listEntry.get(entry.getKey());
                        return patterns.match(propertyValue);
                    }).collect(Collectors.toList());
                    if (results.isEmpty()) {
                        logger().info(c, "No interpretable matcher was found, defaulting to 'exactMatch'");
                        return MatchResult.exactMatch();
                    } else {
                        return MatchResult.aggregate(results);
                    }
                }
            }).collect(Collectors.toList());
            return MatchResult.aggregate(allResults);
        }),
        hasProperty((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(c.getProperties().containsKey(stringValue));
        }),
        hasNotProperty((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(!c.getProperties().containsKey(stringValue));
        }),
        updateCountEqualTo((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(withConvertedNumber(c, stringValue, (context, value) -> context.getUpdateCount().equals(value)));
        }),
        updateCountLessThan((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(withConvertedNumber(c, stringValue, (context, value) -> context.getUpdateCount() < value));
        }),
        updateCountMoreThan((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(withConvertedNumber(c, stringValue, (context, value) -> context.getUpdateCount() > value));
        }),
        listSizeEqualTo((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(withConvertedNumber(c, stringValue, (context, value) -> context.getList().size() == value));
        }),
        listSizeLessThan((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(withConvertedNumber(c, stringValue, (context, value) -> context.getList().size() < value));
        }),
        listSizeMoreThan((Context c, Object object) -> {
            String stringValue = cast(object, String.class);
            return toMatchResult(withConvertedNumber(c, stringValue, (context, value) -> context.getList().size() > value));
        });

        private final BiFunction<Context, Object, MatchResult> evaluator;

        ContextMatcher(BiFunction<Context, Object, MatchResult> evaluator) {
            this.evaluator = evaluator;
        }

        private static MatchResult toMatchResult(boolean result) {
            return result ? MatchResult.exactMatch() : MatchResult.noMatch();
        }

        public static ContextMatcher from(String from) {
            return Arrays.stream(values()).filter(it -> it.name().equals(from)).findFirst().orElse(null);
        }

        private static boolean withConvertedNumber(Context context, String stringValue, BiFunction<Context, Long, Boolean> evaluator) {
            try {
                var longValue = Long.valueOf(stringValue);
                return evaluator.apply(context, longValue);
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        private static <T> T withConvertedNumberGet(Context context, String stringValue, BiFunction<Context, Long, T> getter) {
            try {
                var longValue = Long.valueOf(stringValue);
                return getter.apply(context, longValue);
            } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                return null;
            }
        }

        public MatchResult evaluate(Context context, Object value) {
            return this.evaluator.apply(context, value);
        }
    }
}
