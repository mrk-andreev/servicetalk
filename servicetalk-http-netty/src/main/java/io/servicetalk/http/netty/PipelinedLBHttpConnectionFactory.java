/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static io.servicetalk.client.internal.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.http.api.HttpConnection.SettingKey.MAX_CONCURRENCY;
import static io.servicetalk.http.netty.DefaultHttpConnectionBuilder.buildForPipelined;
import static java.util.Objects.requireNonNull;

final class PipelinedLBHttpConnectionFactory<ResolvedAddress> extends AbstractLBHttpConnectionFactory<ResolvedAddress> {
    private final ReadOnlyHttpClientConfig config;
    private final ExecutionContext executionContext;

    PipelinedLBHttpConnectionFactory(final ReadOnlyHttpClientConfig config,
                                     final ExecutionContext executionContext,
                                     final Function<HttpConnection, HttpConnection> connectionFilterFactory) {
        super(connectionFilterFactory);
        this.config = requireNonNull(config);
        this.executionContext = requireNonNull(executionContext);
    }

    @Override
    Single<LoadBalancedHttpConnection> newConnection(final ResolvedAddress resolvedAddress,
                                final Function<HttpConnection, HttpConnection> connectionFilterFactory) {
        return buildForPipelined(executionContext, resolvedAddress, config, connectionFilterFactory)
                .map(filteredConnection -> new LoadBalancedHttpConnection(filteredConnection,
                        newController(filteredConnection.getSettingStream(MAX_CONCURRENCY),
                                   filteredConnection.onClose(),
                                   config.getMaxPipelinedRequests())));
    }
}
