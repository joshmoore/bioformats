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
import java.util.UUID;

import loci.common.Location;
import loci.formats.Memoizer;
import loci.formats.Memoizer.Storage;
import loci.formats.in.FakeReader;
import loci.formats.memo.MapdbStorage;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Like {@link MemoizerTest} but uses the {@link MapdbStorage} backend rather
 * than files on disk. This only tests the automatic creation of mapdb instances
 * which is most likely to occur by third-party users and also most matches
 * the {@link loci.formats.memo.FileStorage} scenario.
 */
public class MapdbMemoizerTest {

  private static final String TEST_FILE =
    "test&pixelType=int8&sizeX=20&sizeY=20&sizeC=1&sizeZ=1&sizeT=1.fake";

  private File idDir;

  private String id;

  private FakeReader reader;

  private Memoizer memoizer;

  private MapdbStorage dbStorage;

  @BeforeMethod
  public void setUp() throws Exception {
    // No mapping.
    // Location.mapId(TEST_FILE, TEST_FILE);
    reader = new FakeReader();
    try {
      String uuid = UUID.randomUUID().toString();
      idDir = new File(System.getProperty("java.io.tmpdir"), uuid);
      idDir.mkdirs();
      File tempFile = new File(idDir, TEST_FILE);
      tempFile.createNewFile();
      id = tempFile.getAbsolutePath();
      reader.setId(id);
    } finally {
      reader.close();
    }
    reader = new FakeReader(); // No setId !
  }

  @AfterMethod
  public void tearDown() throws Exception {
    if (memoizer != null) {
      memoizer.close();
    }
    if (reader != null) {
      reader.close();
    }
  }

  Storage mk(Storage storage, String id, File directory, boolean doInPlaceCaching) {
    if (storage != null) {
      return storage;
    }
    dbStorage = new MapdbStorage(new Location(id), directory, doInPlaceCaching);
    return dbStorage;
  }

  void ctor() {
    memoizer = new Memoizer() {
      { storage = getStorage(); }
      public Storage getStorage() {
        return mk(storage, id, directory, doInPlaceCaching);
      }
    };
  }

  void ctor0() {
    memoizer = new Memoizer(0) {
      { storage = getStorage(); }
      public Storage getStorage() {
        return mk(storage, id, directory, doInPlaceCaching);
      }
    };
  }

  void ctor0(File directory) {
    memoizer = new Memoizer(0, directory) {
      { storage = getStorage(); }
      public Storage getStorage() {
        return mk(storage, id, directory, doInPlaceCaching);
      }
    };
  }

  void ctorReader() {
    memoizer = new Memoizer(reader) {
      { storage = getStorage(); }
      public Storage getStorage() {
        return mk(storage, id, directory, doInPlaceCaching);
      }
    };
  }

  void ctorReader0() {
    memoizer = new Memoizer(reader, 0) {
      { storage = getStorage(); }
      public Storage getStorage() {
        return mk(storage, id, directory, doInPlaceCaching);
      }
    };
  }

  void ctorReader0(File directory) {
    memoizer = new Memoizer(reader, 0, directory) {
      { storage = getStorage(); }
      public Storage getStorage() {
        return mk(storage, id, directory, doInPlaceCaching);
      }
    };
  }

  void assertKey() {
    assertTrue(dbStorage.containsKey(new Location(id).getAbsolutePath()));
  }

  void assertNoKey() {
    assertFalse(dbStorage.containsKey(new Location(id).getAbsolutePath()));
  }

  @Test
  public void testSimple() throws Exception {
    ctorReader();

    // At this point we're sure that there's no memo file.
    reader.setId(id);
    reader.close();
    memoizer.setId(id);
    memoizer.close();
    memoizer.setId(id);
    memoizer.close();
  }

  public void testDefaultConstructor() throws Exception {
    ctor();
    assertKey();
  }

  @Test
  public void testConstructorTimeElapsed() throws Exception {
    ctor0();
    assertNoKey();

    // Test multiple setId invocations
    memoizer.setId(id);
    assertKey();
    assertFalse(memoizer.isLoadedFromMemo());
    assertTrue(memoizer.isSavedToMemo());
    memoizer.close();
    memoizer.setId(id);
    assertKey();
    assertTrue(memoizer.isLoadedFromMemo());
    assertFalse(memoizer.isSavedToMemo());
    memoizer.close();
  }

  @Test
  public void testConstructorReader() throws Exception {
    ctorReader0(); // no minimum
    assertNoKey();

    // Test multiple setId invocations
    memoizer.setId(id);
    assertKey();
    assertFalse(memoizer.isLoadedFromMemo());
    assertTrue(memoizer.isSavedToMemo());
    memoizer.close();
    memoizer.setId(id);
    assertKey();
    assertTrue(memoizer.isLoadedFromMemo());
    assertFalse(memoizer.isSavedToMemo());
    memoizer.close();
  }

  @Test
  public void testConstructorTimeElapsedDirectory() throws Exception {

    String uuid = UUID.randomUUID().toString();
    File directory = new File(System.getProperty("java.io.tmpdir"), uuid);
    ctor0(directory);

    // Check non-existing memo directory returns null
    assertNoKey();
    assertFalse(dbStorage.isReady());

    // Create memoizer directory and memoizer reader
    directory.mkdirs();

    ctor0(directory);
    assertTrue(dbStorage.isReady());

    // Test multiple setId invocations
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
  public void testConstructorTimeElapsedNull() throws Exception {

    ctor0(null);

    // Check null memo directory returns null
    assertFalse(dbStorage.isReady());

    // Test setId invocation
    memoizer.setId(id);
    assertFalse(memoizer.isLoadedFromMemo());
    assertFalse(memoizer.isSavedToMemo());
    memoizer.close();

  }

  @Test
  public void testConstructorReaderTimeElapsedDirectory() throws Exception {

    String uuid = UUID.randomUUID().toString();
    File directory = new File(System.getProperty("java.io.tmpdir"), uuid);
    ctorReader0(directory);

    // Check non-existing memo directory returns null
    assertFalse(dbStorage.isReady());

    // Create memoizer directory and memoizer reader
    directory.mkdirs();

    ctor0(directory);
    assertTrue(dbStorage.isReady());

    // Test multiple setId invocations
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
  public void testConstructorReaderTimeElapsedNull() throws Exception {

    ctorReader0(null);

    // Check null memo directory returns null
    assertFalse(dbStorage.isReady());

    // Test setId invocation
    memoizer.setId(id);
    assertFalse(memoizer.isLoadedFromMemo());
    assertFalse(memoizer.isSavedToMemo());
    memoizer.close();
  }

  @Test
  public void testGetMemoFilePermissionsDirectory() throws Exception {
      String uuid = UUID.randomUUID().toString();
      File directory = new File(System.getProperty("java.io.tmpdir"), uuid);
      ctorReader0(directory);

      // Check non-existing memo directory returns null
      assertFalse(dbStorage.isReady());

      // Create memoizer directory and memoizer reader
      directory.mkdirs();

      // Check existing non-writeable memo directory returns null
      if (File.separator.equals("/")) {
        // File.setWritable() does not work properly on Windows
        directory.setWritable(false);
        ctorReader0(directory);
        assertFalse(dbStorage.isReady());
        memoizer.close();
      }

      // Check existing writeable memo diretory returns a memo file
      directory.setWritable(true);
      ctorReader0(directory);
      assertTrue(dbStorage.isReady());
  }

  @Test
  public void testGetMemoFilePermissionsInPlaceDirectory() throws Exception {
      String rootPath = id.substring(0, id.indexOf(File.separator) + 1);

      // Check non-writeable file directory returns null for in-place caching
      if (File.separator.equals("/")) {
        // File.setWritable() does not work properly on Windows
        idDir.setWritable(false);
        ctorReader0(new File(rootPath));
        assertFalse(dbStorage.isReady());
      }

      // Check writeable file directory returns memo file beside file
      idDir.setWritable(true);
      ctorReader0(new File(rootPath));
      assertTrue(dbStorage.isReady());
  }

  @Test
  public void testGetMemoFilePermissionsInPlace() throws Exception {

    // Check non-writeable file directory returns null for in-place caching
    if (File.separator.equals("/")) {
      // File.setWritable() does not work properly on Windows
      idDir.setWritable(false);
      ctorReader();
      assertFalse(dbStorage.isReady());
    }
    // Check writeable file directory returns memo file beside file
    idDir.setWritable(true);
    ctorReader();
    assertTrue(dbStorage.isReady());
  }

  @Test
  public void testRelocate() throws Exception {
    // Create an in-place memo file
    ctorReader0();
    memoizer.setId(id);
    memoizer.close();
    assertFalse(memoizer.isLoadedFromMemo());
    assertTrue(memoizer.isSavedToMemo());

    // Rename the directory (including the file and the memo file)
    String uuid = UUID.randomUUID().toString();
    File newidDir = new File(System.getProperty("java.io.tmpdir"), uuid);
    idDir.renameTo(newidDir);
    File newtempFile = new File(newidDir, TEST_FILE);
    id = newtempFile.getAbsolutePath();

    // Try to reopen the file with the Memoizer
    memoizer.setId(id);
    memoizer.close();
    assertFalse(memoizer.isLoadedFromMemo());
    assertTrue(memoizer.isSavedToMemo());
  }

  public static void main(String[] args) throws Exception {
    MemoizerTest t = new MemoizerTest();
    t.setUp();
    try {
      t.testSimple();
    } finally {
      t.tearDown();
    }
  }
}
