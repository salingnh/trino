/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.elasticsearch;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;

import static com.google.common.base.Preconditions.checkState;

/**
 * Measurements are process-local; run the benchmark classes in isolation, not alongside the connector suites.
 */
public final class BenchmarkMemory
{
    private final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private final long allocatedBefore;
    private final long collectionsBefore;
    private final long gcMillisBefore;

    public BenchmarkMemory()
    {
        checkState(threads.isThreadAllocatedMemorySupported(), "Thread allocation measurement is unavailable");
        threads.setThreadAllocatedMemoryEnabled(true);
        allocatedBefore = threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
        collectionsBefore = collections();
        gcMillisBefore = gcMillis();
        ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .forEach(pool -> pool.resetPeakUsage());
    }

    public long allocatedBytes()
    {
        return threads.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocatedBefore;
    }

    public String summary()
    {
        // Pool peaks need not occur simultaneously. This is an upper bound, not a measured live-set size.
        long heapPoolPeaks = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .mapToLong(pool -> pool.getPeakUsage().getUsed())
                .sum();
        return "allocatedBytes=" + allocatedBytes() + " heapPoolPeakUpperBound=" + heapPoolPeaks +
                " gcCollections=" + (collections() - collectionsBefore) + " gcMillis=" + (gcMillis() - gcMillisBefore);
    }

    private static long collections()
    {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    private static long gcMillis()
    {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }
}
