package org.wiremock.extensions.state.extensions.builder;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.matching.CustomMatcherDefinition;
import org.jetbrains.annotations.NotNull;
import org.wiremock.extensions.state.internal.api.StateRequestMatcherParameters;
import org.wiremock.extensions.state.internal.api.StateRequestMatcherParameters.HasContext;
import org.wiremock.extensions.state.internal.api.StateRequestMatcherParameters.HasNotContext;

public abstract class StateRequestMatcherBuilder<T extends StateRequestMatcherParameters> {
    protected T parameters;

    public static @NotNull HasContextBuilder hasContext(@NotNull String context) {
        return new HasContextBuilder(context);
    }

    public static @NotNull HasNotContextBuilder hasNotContext(@NotNull String context) {
        return new HasNotContextBuilder(context);
    }

    public CustomMatcherDefinition build() {
        var convertedParams = Parameters.from(Json.objectToMap(parameters));
        return new CustomMatcherDefinition("state-matcher", convertedParams);
    }

    public static class HasContextBuilder extends StateRequestMatcherBuilder<HasContext> {
        private HasContextBuilder(@NotNull String context) {
            parameters = new HasContext();
            parameters.setHasContext(context);
        }



    }

    public static class HasNotContextBuilder extends StateRequestMatcherBuilder<HasNotContext> {
        private HasNotContextBuilder(@NotNull String context) {
            parameters = new HasNotContext();
            parameters.setHasNotContext(context);
        }

    }

}
