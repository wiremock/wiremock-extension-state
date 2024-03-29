package org.wiremock.extensions.state.extensions.builder;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListenerDefinition;
import org.wiremock.extensions.state.extensions.DeleteStateEventListener;

public class EventListenerBuilder<T> {

    protected T parameters;

    public ServeEventListenerDefinition build() {
        var convertedParams = Parameters.from(Json.objectToMap(parameters));
        return new ServeEventListenerDefinition(DeleteStateEventListener.NAME, convertedParams);
    }
}
