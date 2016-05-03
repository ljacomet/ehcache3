/*
 * Copyright Terracotta, Inc.
 *
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
package org.ehcache.clustered.server.offheap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.ehcache.clustered.common.store.Chain;
import org.ehcache.clustered.server.store.ServerStore;
import org.terracotta.offheapstore.exceptions.OversizeMappingException;
import org.terracotta.offheapstore.paging.PageSource;

public class OffHeapServerStore implements ServerStore {

  private final List<OffHeapChainMap<Long>> segments;

  public OffHeapServerStore(PageSource source, int concurrency) {
    segments = new ArrayList<OffHeapChainMap<Long>>(concurrency);
    for (int i = 0; i < concurrency; i++) {
      segments.add(new OffHeapChainMap<Long>(source, LongPortability.INSTANCE));
    }
  }

  @Override
  public Chain get(long key) {
    return segmentFor(key).get(key);
  }

  @Override
  public void append(long key, ByteBuffer payLoad) {
    try {
      segmentFor(key).append(key, payLoad);
    } catch (OversizeMappingException e) {
      if (handleOversizeMappingException(key)) {
        try {
          segmentFor(key).append(key, payLoad);
          return;
        } catch (OversizeMappingException ex) {
          //ignore
        }
      }

      writeLockAll();
      try {
        do {
          try {
            segmentFor(key).append(key, payLoad);
            return;
          } catch (OversizeMappingException ex) {
            e = ex;
          }
        } while (handleOversizeMappingException(key));
        throw e;
      } finally {
        writeUnlockAll();
      }
    }
  }

  @Override
  public Chain getAndAppend(long key, ByteBuffer payLoad) {
    try {
      return segmentFor(key).getAndAppend(key, payLoad);
    } catch (OversizeMappingException e) {
      if (handleOversizeMappingException(key)) {
        try {
          return segmentFor(key).getAndAppend(key, payLoad);
        } catch (OversizeMappingException ex) {
          //ignore
        }
      }

      writeLockAll();
      try {
        do {
          try {
            return segmentFor(key).getAndAppend(key, payLoad);
          } catch (OversizeMappingException ex) {
            e = ex;
          }
        } while (handleOversizeMappingException(key));
        throw e;
      } finally {
        writeUnlockAll();
      }
    }
  }

  @Override
  public void replaceAtHead(long key, Chain expect, Chain update) {
    try {
      segmentFor(key).replaceAtHead(key, expect, update);
    } catch (OversizeMappingException e) {
      if (handleOversizeMappingException(key)) {
        try {
          segmentFor(key).replaceAtHead(key, expect, update);
          return;
        } catch (OversizeMappingException ex) {
          //ignore
        }
      }

      writeLockAll();
      try {
        do {
          try {
            segmentFor(key).replaceAtHead(key, expect, update);
            return;
          } catch (OversizeMappingException ex) {
            e = ex;
          }
        } while (handleOversizeMappingException(key));
        throw e;
      } finally {
        writeUnlockAll();
      }
    }
  }

  private OffHeapChainMap<Long> segmentFor(long key) {
    return segments.get(Math.abs((int) (key % segments.size())));
  }

  private void writeLockAll() {
    for (OffHeapChainMap<Long> s : segments) {
      s.writeLock().lock();
    }
  }

  private void writeUnlockAll() {
    for (OffHeapChainMap<Long> s : segments) {
      s.writeLock().unlock();
    }
  }

  private boolean handleOversizeMappingException(long hash) {
    boolean evicted = false;

    OffHeapChainMap<Long> target = segmentFor(hash);
    for (OffHeapChainMap<Long> s : segments) {
      if (s != target) {
        evicted |= s.shrink();
      }
    }

    return evicted;
  }
}
