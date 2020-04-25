package io.smallrye.graphql.execution;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.jboss.logging.Logger;

import graphql.ExecutionInput;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.validation.ValidationError;

public class QueryCache extends SimpleInstrumentation implements PreparsedDocumentProvider {
    private static final Logger LOG = Logger.getLogger(QueryCache.class);
    private static final int MAX_CACHE_SIZE = AccessController.doPrivileged((PrivilegedAction<Integer>) () -> {
        return Integer.getInteger("io.smallrye.graphql.execution.queryCacheMaxSize", 2048);
    });

    private final LRUCache<String, PreparsedDocumentEntry> cache = new LRUCache<>(MAX_CACHE_SIZE);
    private final Map<String, ExecutionFunction> functionCache = new HashMap<>();
    private final ThreadLocal<ExecutionFunction> executionFunctionTL = new ThreadLocal<>();

    @Override
    public PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
            Function<ExecutionInput, PreparsedDocumentEntry> computeFunction) {
        String query = executionInput.getQuery();
        PreparsedDocumentEntry entry = cache.get(query);
        if (entry == null) {
            ExecutionFunction executionFunction = new ExecutionFunction(computeFunction, executionInput);
            functionCache.putIfAbsent(query, executionFunction);
            executionFunctionTL.set(executionFunction);
            entry = computeFunction.apply(executionInput);
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Retrieved from cache: " + query);
        }
        return entry;
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(
            InstrumentationValidationParameters parameters) {

        ExecutionFunction executionFunction = executionFunctionTL.get();
        if (executionFunction != null) {
            executionFunctionTL.remove();
            return new ValidationInstrumentationContext(executionFunction);
        }
        return super.beginValidation(parameters);
    }

    private static class ExecutionFunction implements Function<String, PreparsedDocumentEntry> {
        private final Function<ExecutionInput, PreparsedDocumentEntry> function;
        private final ExecutionInput executionInput;

        ExecutionFunction(Function<ExecutionInput, PreparsedDocumentEntry> function, ExecutionInput executionInput) {
            this.function = function;
            this.executionInput = executionInput;
        }

        @Override
        public PreparsedDocumentEntry apply(String s) {
            return function.apply(executionInput);
        }

        String getQuery() {
            return executionInput.getQuery();
        }
    }

    private class ValidationInstrumentationContext implements InstrumentationContext<List<ValidationError>> {
        private final ExecutionFunction executionFunction;

        ValidationInstrumentationContext(ExecutionFunction executionFunction) {
            this.executionFunction = executionFunction;
        }

        @Override
        public void onDispatched(CompletableFuture<List<ValidationError>> result) {
            // no-op
        }

        @Override
        public void onCompleted(List<ValidationError> validationErrors, Throwable t) {
            // at this point, we know the validation is complete - go ahead and add it to the cache if no errors
            if (executionFunction != null && validationErrors == null || validationErrors.isEmpty()) {
                // valid, uncached query - add to cache
                cache.computeIfAbsent(executionFunction.getQuery(), executionFunction);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Added to cache: " + executionFunction.getQuery());
                }
            }
        }
    }
}