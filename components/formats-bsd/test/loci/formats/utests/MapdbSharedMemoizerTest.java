/*
 * #%L
 * BSD implementations of Bio-Formats readers and writers
 * %%
 * Copyright (C) 2005 - 2015 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package loci.formats.utests;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import loci.common.Location;
import loci.formats.Memoizer;
import loci.formats.memo.MapdbStorage;
import loci.formats.memo.Utils;

import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests the {@link MapdbStorage} strategy in the context of shared DBs, i.e.
 * long-lived server processes like OMERO which will want to control the sharing
 * and locking of instances.
 */
public class MapdbSharedMemoizerTest extends AbstractMemoizerTest<MapdbStorage> {

  DB memoDB, lockDB;
  HTreeMap<String, byte[]> memos;
  HTreeMap<String, String> locks;
  String uuid = UUID.randomUUID().toString();
  File dir = new File(System.getProperty("java.io.tmpdir"), uuid);
  CountDownLatch latch = new CountDownLatch(1);

  /**
   * Class for using the protected methods in {@link Utils} since they
   * are not intended for general consumption.
   */
  static class U extends Utils {
    static void init(MapdbSharedMemoizerTest t) {
      t.memoDB = memoDB(t.dir);
      t.lockDB = lockDB();
      t.memos = memoTree(t.memoDB);
      t.locks = lockTree(t.lockDB);
    }
  }

  @BeforeClass
  public void create() {
    dir.mkdirs();
    U.init(this);
  }

  @AfterClass
  public void clean() {
    assertTrue(new File(dir, "bfmemo.db").delete());
  }

  @Override
  MapdbStorage mk(String id, File directory, boolean doInPlaceCaching) {
    // ignore directory and doInPlaceCaching; this represents a single
    // shared storage instance.
    return new TestMapdbStorage(id, memos, locks, latch);
  }

  @Test
  public void testSimple() throws Exception {
    ctorReader0();
    memoizer.setId(id);
    assertFalse(memoizer.isLoadedFromMemo());
    assertTrue(memoizer.isSavedToMemo());
    memoizer.close();
    memoizer.setId(id);
    assertTrue(memoizer.isLoadedFromMemo());
    assertFalse(memoizer.isSavedToMemo());
    memoizer.close();
  }

  @Test
  public void testSecondThreadThrows() throws Throwable {
    ctorReader0();
    Memoizer first = memoizer;
    TestMapdbStorage s1 = (TestMapdbStorage) first.getStorage();
    ctorReader0();
    Memoizer second = memoizer;
    TestMapdbStorage s2 = (TestMapdbStorage) second.getStorage();
    memoizer = null;

    ThreadTest t1 = new ThreadTest(first, id);
    ThreadTest t2 = new ThreadTest(second, id);
    t1.start();
    latch.await(5, TimeUnit.SECONDS);
    // At this point, we know that the first thread has already acquired
    // the write lock
    t2.start();
    t1.join();
    t2.join();

    // Make sure that nothing unexpected happened.
    if (t1.throwable != null) {
        throw t1.throwable;
    }

    // The second thread should have gotten an interrupted exception
    // which it is expected to handle. Likely this means that the
    // error will bubble up to the client which will need to retry.
    assertTrue(t2.throwable instanceof InterruptedByTimeoutException);

    // first successfully was created.
    assertFalse(first.isLoadedFromMemo());
    assertTrue(first.isSavedToMemo());
    first.close();
    // second failed completely.
    assertFalse(second.isLoadedFromMemo());
    assertFalse(second.isSavedToMemo());
    second.close();
    // a third try should be successful
    ctorReader0();
    memoizer.setId(id);
    assertTrue(memoizer.isLoadedFromMemo());
    assertFalse(memoizer.isSavedToMemo());
  }

}

class ThreadTest extends Thread {

  final Memoizer memoizer;
  final String id;
  volatile Throwable throwable;

  ThreadTest(Memoizer memoizer, String id) {
    this.memoizer = memoizer;
    this.id = id;
  }

  @Override
  public void run() {
    try {
      memoizer.setId(id);
    } catch (Throwable t) {
      this.throwable = t;
    }
  }

}

class TestMapdbStorage extends MapdbStorage {

  CountDownLatch latch;

  TestMapdbStorage(String id,
          HTreeMap<String, byte[]> memos,
          HTreeMap<String, String> locks,
          CountDownLatch latch) {
    super(new Location(id), memos, locks);
    this.latch = latch;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    try {
      return super.getOutputStream();
    } finally {
      latch.countDown();
    }
  }

}
