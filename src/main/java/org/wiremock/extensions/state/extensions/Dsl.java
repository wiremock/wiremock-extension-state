package org.wiremock.extensions.state.extensions;


import org.jetbrains.annotations.NotNull;
import org.wiremock.extensions.state.extensions.builder.DeleteStateEventListenerBuilder;
import org.wiremock.extensions.state.extensions.builder.DeleteStateEventListenerBuilder.MultiContextBuilder;
import org.wiremock.extensions.state.extensions.builder.DeleteStateEventListenerBuilder.SingleContextBuilder;
import org.wiremock.extensions.state.extensions.builder.RecordStateEventListenerBuilder;
import org.wiremock.extensions.state.extensions.builder.StateRequestMatcherBuilder;
import org.wiremock.extensions.state.extensions.builder.StateRequestMatcherBuilder.HasContextBuilder;
import org.wiremock.extensions.state.extensions.builder.StateRequestMatcherBuilder.HasNotContextBuilder;

import java.util.Arrays;
import java.util.Collection;

public class Dsl {

    public static @NotNull RecordStateEventListenerBuilder recordContext(@NotNull String context) {
        return RecordStateEventListenerBuilder.context(context);
    }

    public static @NotNull SingleContextBuilder deleteContext(@NotNull String context) {
        return DeleteStateEventListenerBuilder.context(context);
    }

    public static @NotNull MultiContextBuilder deleteContexts(@NotNull String... contexts) {
        return DeleteStateEventListenerBuilder.contexts(Arrays.asList(contexts));
    }

    public static @NotNull MultiContextBuilder deleteContexts(@NotNull Collection<String> contexts) {
        return DeleteStateEventListenerBuilder.contexts(contexts);
    }

    public static @NotNull MultiContextBuilder deleteContextsMatching(@NotNull String contextMatching) {
        return DeleteStateEventListenerBuilder.contextsMatching(contextMatching);
    }

    public static @NotNull HasContextBuilder hasContext(@NotNull String context) {
        return StateRequestMatcherBuilder.hasContext(context);
    }

    public static @NotNull HasNotContextBuilder hasNotContext(@NotNull String context) {
        return StateRequestMatcherBuilder.hasNotContext(context);
    }
}
