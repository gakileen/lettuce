/*
 * Copyright 2011-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lambdaworks.redis.cluster.topology;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.lambdaworks.redis.RedisFuture;
import com.lambdaworks.redis.RedisURI;

/**
 * @author Mark Paluch
 */
class Requests {

    Map<RedisURI, TimedAsyncCommand<String, String, String>> rawViews = new TreeMap<>(
            TopologyComparators.RedisURIComparator.INSTANCE);

    Requests() {
    }

    Requests(Map<RedisURI, TimedAsyncCommand<String, String, String>> rawViews) {
        this.rawViews = rawViews;
    }

    void addRequest(RedisURI redisURI, TimedAsyncCommand<String, String, String> command) {
        rawViews.put(redisURI, command);
    }

    long await(long timeout, TimeUnit timeUnit) throws InterruptedException {

        long waitTime = 0;

        for (Map.Entry<RedisURI, ? extends RedisFuture<?>> entry : rawViews.entrySet()) {
            long timeoutLeft = timeUnit.toNanos(timeout) - waitTime;

            if (timeoutLeft <= 0) {
                break;
            }

            long startWait = System.nanoTime();
            RedisFuture<?> future = entry.getValue();

            try {
                if (!future.await(timeoutLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            } finally {
                waitTime += System.nanoTime() - startWait;
            }

        }
        return waitTime;
    }

    Set<RedisURI> nodes() {
        return rawViews.keySet();
    }

    TimedAsyncCommand<String, String, String> getRequest(RedisURI redisURI) {
        return rawViews.get(redisURI);
    }

    Requests mergeWith(Requests requests) {

        Map<RedisURI, TimedAsyncCommand<String, String, String>> result = new TreeMap<>(
                TopologyComparators.RedisURIComparator.INSTANCE);
        result.putAll(this.rawViews);
        result.putAll(requests.rawViews);

        return new Requests(result);
    }
}
