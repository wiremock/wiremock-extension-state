package org.wiremock.extensions.state;

import org.wiremock.extensions.state.internal.ContextManager;
import com.github.tomakehurst.wiremock.core.ConfigurationException;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension;
import com.github.tomakehurst.wiremock.store.Store;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class StateRequestMatcher extends RequestMatcherExtension {

    private final TemplateEngine templateEngine = new TemplateEngine(Collections.emptyMap(), null, Collections.emptySet(), false);
    private final ContextManager contextManager;

    public StateRequestMatcher(Store<String, Object> store) {
        this.contextManager = new ContextManager(store);
    }

    @Override
    public String getName() {
        return "state-matcher";
    }

    @Override
    public MatchResult match(Request request, Parameters parameters) {
        if (parameters.size() != 1) {
            throw new ConfigurationException("Parameters should only contain one entry ('hasContext' or 'hasNotContext'");
        }
        var model = Map.of("request", RequestTemplateModel.from(request));
        return Optional
            .ofNullable(parameters.getString("hasContext", null))
            .map(template -> hasContext(model, template))
            .or(() -> Optional.ofNullable(parameters.getString("hasNotContext", null)).map(template -> hasNotContext(model, template)))
            .orElseThrow(() -> new ConfigurationException("Parameters should only contain 'hasContext' or 'hasNotContext'"));
    }

    private MatchResult hasContext(Map<String, RequestTemplateModel> model, String template) {
        var context = renderTemplate(model, template);
        if (contextManager.hasContext(context)) {
            return MatchResult.exactMatch();
        } else {
            return MatchResult.noMatch();
        }
    }

    private MatchResult hasNotContext(Map<String, RequestTemplateModel> model, String template) {
        var context = renderTemplate(model, template);
        if (!contextManager.hasContext(context)) {
            return MatchResult.exactMatch();
        } else {
            return MatchResult.noMatch();
        }
    }

    String renderTemplate(Object context, String value) {
        return templateEngine.getUncachedTemplate(value).apply(context);
    }
}