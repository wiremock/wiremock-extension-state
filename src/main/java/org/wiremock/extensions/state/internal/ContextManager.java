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
package org.wiremock.extensions.state.internal;

import com.github.tomakehurst.wiremock.store.Store;
import org.wiremock.extensions.state.internal.model.Context;

import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.wiremock.extensions.state.internal.ExtensionLogger.logger;

public class ContextManager {

    private final String CONTEXT_KEY_PREFIX = "context:";
    private final Store<String, Object> store;
    private final TransactionManager transactionManager;

    public ContextManager(Store<String, Object> store, TransactionManager transactionManager) {
        this.store = store;
        this.transactionManager = transactionManager;
    }

    private static Supplier<Context> createNewContext(String contextName) {
        logger().info(contextName, "created");
        return () -> new Context(contextName);
    }

    /**
     * Searches for the context by the given name.
     *
     * @param contextName The context name to search for.
     * @return Optional with a copy of the context - or empty.
     */
    public Optional<Context> getContextCopy(String contextName) {
        return getSafeContextCopy(contextName);
    }

    /**
     * Deletes a context by its name.
     *
     * @param requestId   ID of the request performing this action.
     * @param contextName Name of the context to delete.
     */
    public void deleteContext(String requestId, String contextName) {
        transactionManager.withTransaction(requestId, contextName, (transaction) -> {
            store.remove(createContextKey(contextName));
            logger().info(contextName, "deleted");
        });
    }

    /**
     * Iterates over all contexts, passing a safe copy to the consumer.
     * <p>
     * Silently ignores non-existing contexts.
     *
     * @param requestId ID of the request performing this action.
     * @param consumer  Action to be performed on the copy of the context.
     */
    public void onEach(String requestId, Consumer<Context> consumer) {
        store.getAllKeys()
            .filter(it -> it.startsWith(CONTEXT_KEY_PREFIX))
            .forEach(key -> {
                var contextName = getContextNameFromContextKey(key);
                transactionManager
                    .withTransaction(
                        requestId,
                        contextName,
                        (transaction) -> {
                            getSafeContextCopy(contextName).ifPresent(consumer);
                        });
            });
    }

    public void deleteAllContexts(String requestId) {
        store.getAllKeys()
            .filter(it -> it.startsWith(CONTEXT_KEY_PREFIX))
            .forEach(key -> {
                transactionManager
                    .withTransaction(
                        requestId,
                        getContextNameFromContextKey(key),
                        (transaction) -> {
                            store.remove(key);
                        });
                logger().info("allContexts", "deleted");
            });
    }

    public void createOrUpdateContextState(String requestId, String contextName, Map<String, String> properties) {
        transactionManager.withTransaction(requestId, contextName, (transaction) -> {
            var contextKey = createContextKey(contextName);
            var context = store.get(contextKey)
                .map(it -> (Context) it)
                .orElseGet(createNewContext(contextName));
            properties.forEach((k, v) -> {
                if (v.equals("null")) {
                    context.getProperties().remove(k);
                    logger().info(contextName, String.format("property '%s' removed", k));
                } else {
                    context.getProperties().put(k, v);
                    logger().info(contextName, String.format("property '%s' updated", k));
                }
            });
            transaction.recordWrite(context::incUpdateCount);
            store.put(contextKey, context);
        });
    }

    public void createOrUpdateContextList(String requestId, String contextName, Consumer<LinkedList<Map<String, String>>> consumer) {
        transactionManager.withTransaction(requestId, contextName, (transaction) -> {
            var contextKey = createContextKey(contextName);
            var context = store.get(contextKey)
                .map(it -> (Context) it)
                .orElseGet(createNewContext(contextName));
            consumer.accept(context.getList());
            transaction.recordWrite(context::incUpdateCount);
            store.put(contextKey, context);
        });
    }

    public Integer numUpdates(String contextName) {
        return store.get(createContextKey(contextName)).map(it -> ((Context) it).getUpdateCount()).orElse(0);
    }

    private String getContextNameFromContextKey(String key) {
        return key.substring(CONTEXT_KEY_PREFIX.length());
    }

    public String createContextKey(String contextName) {
        return CONTEXT_KEY_PREFIX + contextName;
    }

    private Optional<Context> getSafeContextCopy(String contextName) {
        return store.get(createContextKey(contextName)).map(it -> (Context) it).map(Context::new);
    }
}
