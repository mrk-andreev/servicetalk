/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MapPublisherTest {

    private final TestPublisher<Integer> source = new TestPublisher.Builder<Integer>()
            .disableAutoOnSubscribe().build();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void testMapFunctionReturnsNull() {
        Publisher<String> map = source.map(v -> null);

        toSource(map).subscribe(subscriber);

        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);

        assertTrue(subscriber.subscriptionReceived());

        subscriber.request(2);
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1, 2);
        assertThat(subscriber.takeItems(), contains((Integer) null, null));
    }
}
