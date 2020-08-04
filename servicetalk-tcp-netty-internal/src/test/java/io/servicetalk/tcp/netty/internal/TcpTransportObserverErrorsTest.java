/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(Parameterized.class)
public final class TcpTransportObserverErrorsTest extends AbstractTransportObserverTest {

    private enum ErrorSource {
        CONNECTION_ACCEPTOR,
        PIPELINE,
        CLIENT_WRITE,
    }

    private final ErrorSource errorSource;
    private ChannelInitializer channelInitializer = channel -> { };

    public TcpTransportObserverErrorsTest(ErrorSource errorSource) {
        this.errorSource = errorSource;
        switch (errorSource) {
            case CONNECTION_ACCEPTOR:
                connectionAcceptor(ctx -> failed(DELIBERATE_EXCEPTION));
                break;
            case PIPELINE:
                channelInitializer = channel -> channel.pipeline().addLast(new SimpleChannelInboundHandler<Buffer>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Buffer msg) {
                        throw DELIBERATE_EXCEPTION;
                    }
                });
                break;
            case CLIENT_WRITE:
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorSource);
        }
    }

    @Parameters(name = "errorSource={0}")
    public static Collection<ErrorSource> getErrorSources() {
        return asList(ErrorSource.PIPELINE, ErrorSource.CLIENT_WRITE);
    }

    @Override
    TcpServer createServer() {
        return new TcpServer(getTcpServerConfig()) {
            @Override
            ChannelInitializer getChannelInitializer(Function<NettyConnection<Buffer, Buffer>, Completable> service,
                                                     ExecutionContext executionContext) {
                return super.getChannelInitializer(service, executionContext).andThen(channelInitializer);
            }
        };
    }

    @Test
    public void testConnectionClosed() throws Exception {
        NettyConnection<Buffer, Buffer> connection = client.connectBlocking(CLIENT_CTX, serverAddress);
        verify(clientTransportObserver).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();
        switch (errorSource) {
            case CONNECTION_ACCEPTOR:
                break;
            case PIPELINE:
                verify(clientConnectionObserver).established(any(ConnectionInfo.class));
                verify(serverConnectionObserver, await()).established(any(ConnectionInfo.class));

                Buffer content = connection.executionContext().bufferAllocator().fromAscii("Hello");
                connection.write(from(content.duplicate())).toFuture().get();
                verify(clientConnectionObserver).onDataWrite(content.readableBytes());
                verify(clientConnectionObserver).onFlush();
                assertThrows(ExecutionException.class,
                        () -> connection.read().firstOrElse(() -> null).toFuture().get());
                verify(serverConnectionObserver).onDataRead(content.readableBytes());
                break;
            case CLIENT_WRITE:
                verify(clientConnectionObserver).established(any(ConnectionInfo.class));
                verify(serverConnectionObserver, await()).established(any(ConnectionInfo.class));

                assertThrows(ExecutionException.class, () -> connection.write(
                        Publisher.failed(DELIBERATE_EXCEPTION)).toFuture().get());
                verify(clientDataObserver).onNewWrite();
                verify(clientWriteObserver).requestedToWrite(anyLong());
                verify(clientWriteObserver).writeFailed(DELIBERATE_EXCEPTION);
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorSource);
        }
        connection.onClose().toFuture().get();
        verify(clientConnectionObserver).connectionClosed();
        switch (errorSource) {
            case PIPELINE:
                verify(serverConnectionObserver, await()).connectionClosed(DELIBERATE_EXCEPTION);
                break;
            case CLIENT_WRITE:
                verify(serverConnectionObserver, await()).connectionClosed();
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorSource);
        }

        verifyNoMoreInteractions(clientTransportObserver, clientConnectionObserver,
                serverTransportObserver, serverConnectionObserver);
    }

    @After
    public void qwe() {
        System.err.println(Mockito.mockingDetails(serverConnectionObserver).printInvocations());
        System.err.println(Mockito.mockingDetails(clientWriteObserver).printInvocations());
    }
}