package io.smallrye.graphql.execution.datafetcher;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.dataloader.BatchLoaderEnvironment;
import org.eclipse.microprofile.context.ThreadContext;
import org.eclipse.microprofile.graphql.GraphQLException;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import io.smallrye.graphql.SmallRyeGraphQLServerMessages;
import io.smallrye.graphql.bootstrap.Config;
import io.smallrye.graphql.schema.model.Operation;
import io.smallrye.graphql.transformation.AbstractDataFetcherException;

/**
 * Handle Async calls with CompletableFuture
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 * @param <K>
 * @param <T>
 */
public class CompletionStageDataFetcher<K, T> extends AbstractDataFetcher<K, T> {

    public CompletionStageDataFetcher(Operation operation, Config config) {
        super(operation, config);
    }

    @Override
    protected <T> T invokeAndTransform(DataFetchingEnvironment dfe, DataFetcherResult.Builder<Object> resultBuilder,
            Object[] transformedArguments) throws AbstractDataFetcherException, Exception {

        ThreadContext threadContext = ThreadContext.builder().build();
        CompletionStage<Object> futureResultFromMethodCall = threadContext
                .withContextCapture(reflectionHelper.invoke(transformedArguments));

        return (T) futureResultFromMethodCall.handle((result, throwable) -> {

            if (throwable != null) {
                throwable = unwrapThrowable(throwable);

                eventEmitter.fireOnDataFetchError(dfe.getExecutionId().toString(), throwable);
                if (throwable instanceof GraphQLException) {
                    GraphQLException graphQLException = (GraphQLException) throwable;
                    partialResultHelper.appendPartialResult(resultBuilder, dfe, graphQLException);
                } else if (throwable instanceof Exception) {
                    throw SmallRyeGraphQLServerMessages.msg.dataFetcherException(operation, throwable);
                } else if (throwable instanceof Error) {
                    throw ((Error) throwable);
                }
            } else {
                try {
                    resultBuilder.data(fieldHelper.transformResponse(result));
                } catch (AbstractDataFetcherException te) {
                    te.appendDataFetcherResult(resultBuilder, dfe);
                }
            }

            return resultBuilder.build();
        });
    }

    @Override
    protected <T> T invokeFailure(DataFetcherResult.Builder<Object> resultBuilder) {
        return (T) CompletableFuture.completedFuture(resultBuilder.build());
    }

    @Override
    public CompletionStage<List<T>> load(List<K> keys, BatchLoaderEnvironment ble) {
        Object[] arguments = batchLoaderHelper.getArguments(keys, ble);
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        ThreadContext threadContext = ThreadContext.builder().build();
        return threadContext
                .withContextCapture((CompletableFuture<List<T>>) reflectionHelper.invokePrivileged(tccl, arguments));
    }
}
