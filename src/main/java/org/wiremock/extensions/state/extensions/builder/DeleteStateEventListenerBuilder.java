package org.wiremock.extensions.state.extensions.builder;

import org.jetbrains.annotations.NotNull;
import org.wiremock.extensions.state.internal.api.DeleteStateParameters;
import org.wiremock.extensions.state.internal.api.DeleteStateParameters.ListParameters;

import java.util.Collection;
import java.util.List;

public abstract class DeleteStateEventListenerBuilder extends EventListenerBuilder<DeleteStateParameters> {

    private DeleteStateEventListenerBuilder(DeleteStateParameters params) {
        this.parameters = params;
    }

    public static @NotNull SingleContextBuilder context(@NotNull String context) {
        var params = new DeleteStateParameters();
        params.setContext(context);
        return new SingleContextBuilder(params);
    }

    public static @NotNull MultiContextBuilder contexts(@NotNull Collection<String> contexts) {
        var params = new DeleteStateParameters();
        params.setContexts(List.copyOf(contexts));
        return new MultiContextBuilder(params);
    }

    public static @NotNull MultiContextBuilder contextsMatching(@NotNull String contextsMatching) {
        var params = new DeleteStateParameters();
        params.setContextsMatching(contextsMatching);
        return new MultiContextBuilder(params);
    }


    public static class SingleContextBuilder extends DeleteStateEventListenerBuilder {
        private SingleContextBuilder(DeleteStateParameters params) {
            super(params);
        }

        public @NotNull ListBuilder list() {
            return new ListBuilder(this);
        }
    }

    public static class ListBuilder {
        private final SingleContextBuilder singleContextBuilder;
        private final ListParameters listParams = new ListParameters();

        private ListBuilder(SingleContextBuilder contextBuilder) {
            this.singleContextBuilder = contextBuilder;
        }

        public @NotNull SingleContextBuilder first() {
            listParams.setDeleteFirst(true);
            singleContextBuilder.parameters.setList(listParams);
            return singleContextBuilder;
        }

        public @NotNull SingleContextBuilder last() {
            listParams.setDeleteLast(true);
            singleContextBuilder.parameters.setList(listParams);
            return singleContextBuilder;
        }

        public @NotNull SingleContextBuilder index(int index) {
            listParams.setDeleteIndex(String.valueOf(index));
            singleContextBuilder.parameters.setList(listParams);
            return singleContextBuilder;
        }

        public @NotNull SingleContextBuilder where(@NotNull String property, @NotNull String value) {
            listParams.setDeleteWhere(new ListParameters.Where(property, value));
            singleContextBuilder.parameters.setList(listParams);
            return singleContextBuilder;
        }
    }

    public static class MultiContextBuilder extends DeleteStateEventListenerBuilder {
        private MultiContextBuilder(DeleteStateParameters params) {
            super(params);
        }
    }

}
