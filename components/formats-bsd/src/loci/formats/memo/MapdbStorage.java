/*
 * #%L
 * BSD implementations of Bio-Formats readers and writers
 * %%
 * Copyright (C) 2005 - 2016 Open Microscopy Environment:
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

package loci.formats.memo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import loci.common.Location;
import loci.formats.Memoizer.Storage;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class MapdbStorage implements Storage {

  /**
   * Default {@link org.slf4j.Logger} for the memoizer class
   */
  private static final Logger LOGGER =
    LoggerFactory.getLogger(MapdbStorage.class);

  // -- Fields --

  private final String key;

  private final HTreeMap<String, byte[]> memos;

  private final HTreeMap<String, String> writeLocks;

  private boolean lockHeld;

  private OutputStream os;

  private File tempFile;

  private String uuid;

  private Runnable cleanup = null;

  // -- Constructors --

  public MapdbStorage(Location realFile, File directory, boolean doInPlaceCaching) {
    this.key = realFile.getAbsolutePath();
    if (directory == null && !doInPlaceCaching) {
      LOGGER.debug("skipping memo: no directory given");
      memos = null;
      writeLocks = null;
    } else {
      File writeDirectory = null;
      if (doInPlaceCaching || Utils.isRootDirectory(directory, key)) {
        writeDirectory = new File(key).getParentFile();
      } else {
        writeDirectory = directory;
      }

      final DB[] dbs = new DB[2];
      try {
        dbs[0] = DBMaker.fileDB(new File(writeDirectory, "bfmemo.db"))
          .transactionDisable()
          .allocateStartSize(  5 * 1024*1024*1024) // 5 GB
          .allocateIncrement(  1 * 1024*1024*1024) // 1 GB
          .closeOnJvmShutdown()
          .make();
        dbs[1] = DBMaker.memoryDB()
          .transactionDisable()
          //.cacheWeakRefEnable()
          //.cacheExecutorEnable()
          .make();
      } catch (Exception e) {
        LOGGER.error("failed to initialize db: {}", writeDirectory, e);
        memos = null;
        writeLocks = null;
        return;
      }

      memos = dbs[0]
        .hashMapCreate("memos")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.BYTE_ARRAY)
        .makeOrGet();
      writeLocks = dbs[1]
        .hashMapCreate("locks")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .makeOrGet();

      cleanup = new Runnable() {
        @Override
        public void run() {
          memos.close();
          writeLocks.close();
          dbs[0].close();
          dbs[1].close();
        }
      };

    }
    // TODO: overflow to disk, executors, soft refs
  }

  // -- Interface methods --

  public boolean readReady() {
    // If it exists, it's ready.
    return memos != null;
  }

  public boolean writeReady() {
    return memos != null && !memos.getEngine().isReadOnly();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    assertUnlocked();
    byte[] data = memos.get(key);
    if (data == null) {
      return null;
    }
    return new ByteArrayInputStream(data);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    acquireLock();
    if (os == null) {
      // Create temporary location for output
      // Note: can't rename tempfile until resources are closed.
      tempFile = File.createTempFile(key, ".tmp");
      tempFile.deleteOnExit();

      LOGGER.debug("saving to temp file: {}", tempFile);
      os = new FileOutputStream(tempFile);
    }
    return os;
  }

  @Override
  public void commit() throws IOException {
    acquireLock();
    try {
        byte[] data = Files.readAllBytes(Paths.get(tempFile.getAbsolutePath()));
        memos.put(key, data);
    } finally {
        Utils.deleteQuietly(LOGGER, tempFile);
        releaseLock();
    }
  }

  @Override
  public void rollback() {
    releaseLock();
  }

  @Override
  public void delete() {
    memos.remove(key);
    releaseLock();
  }


  @Override
  public void close() {
    releaseLock();
    if (cleanup != null) {
      cleanup.run();
      cleanup = null;
    }
  }

  // -- Implementation methods --

  public boolean containsKey(String key) {
    if (!readReady()) {
      return false;
    }
    return memos.containsKey(key);
  }

  protected void assertUnlocked() throws InterruptedByTimeoutException {
    if (!readReady()) {
      throw new IllegalStateException("no locking db available");
    }
    String current = writeLocks.get(key);
    if (!(current == null || current.equals(uuid))) {
      throw new InterruptedByTimeoutException();
    }
  }

  protected void acquireLock() throws InterruptedByTimeoutException {
    if (!writeReady()) {
      throw new IllegalStateException("no locking db available");
    }

    // Attempt lock
    if (uuid == null) {
        uuid = UUID.randomUUID().toString();
    }
    String old = writeLocks.putIfAbsent(key, uuid);
    lockHeld = (old == null || uuid.equals(old)); // i.e. previously unset.

    if (!lockHeld) {
      // Locked by others
      throw new InterruptedByTimeoutException();
    }
  }

  protected void releaseLock() {
    if (lockHeld) {
      writeLocks.remove(key);
    }
  }

}
