package org.wiremock.extensions.state.extensions.builder;

import com.github.tomakehurst.wiremock.extension.Parameters;
import org.jetbrains.annotations.NotNull;
import org.wiremock.extensions.state.internal.api.RecordStateParameters;

import java.util.Map;

public class RecordStateEventListenerBuilder extends EventListenerBuilder<RecordStateParameters> {

    private RecordStateEventListenerBuilder(RecordStateParameters params) {
        this.parameters = params;
    }

    public static @NotNull RecordStateEventListenerBuilder context(@NotNull String context) {
        var params = new RecordStateParameters();
        params.setContext(context);
        return new RecordStateEventListenerBuilder(params);
    }

    public @NotNull RecordStateEventListenerBuilder state(@NotNull Map<String, String> state) {
        parameters.setState(state);
        return this;
    }

    public @NotNull RecordStateEventListenerBuilder state(@NotNull Parameters state) {
        //noinspection unchecked
        parameters.setState(state.as(Map.class));
        return this;
    }

    public ListBuilder list() {
        return new ListBuilder(this);
    }

    public static class ListBuilder {
        private final RecordStateEventListenerBuilder recordStateBuilder;
        private final RecordStateParameters.ListParameters listParameters = new RecordStateParameters.ListParameters();

        private ListBuilder(RecordStateEventListenerBuilder recordStateBuilder) {
            this.recordStateBuilder = recordStateBuilder;
        }

        public @NotNull RecordStateEventListenerBuilder addFirst(@NotNull Map<String, String> state) {
            listParameters.setAddFirst(state);
            recordStateBuilder.parameters.setList(listParameters);
            return recordStateBuilder;
        }

        public @NotNull RecordStateEventListenerBuilder addFirst(@NotNull Parameters state) {
            //noinspection unchecked
            listParameters.setAddFirst(state.as(Map.class));
            recordStateBuilder.parameters.setList(listParameters);
            return recordStateBuilder;
        }

        public @NotNull RecordStateEventListenerBuilder addLast(@NotNull Map<String, String> state) {
            listParameters.setAddLast(state);
            recordStateBuilder.parameters.setList(listParameters);
            return recordStateBuilder;
        }

        public @NotNull RecordStateEventListenerBuilder addLast(@NotNull Parameters state) {
            //noinspection unchecked
            listParameters.setAddLast(state.as(Map.class));
            recordStateBuilder.parameters.setList(listParameters);
            return recordStateBuilder;
        }


    }


}
