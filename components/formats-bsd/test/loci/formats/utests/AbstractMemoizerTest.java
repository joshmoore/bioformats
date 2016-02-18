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

import java.io.File;
import java.util.UUID;

import loci.formats.Memoizer;
import loci.formats.Memoizer.Storage;
import loci.formats.in.FakeReader;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public abstract class AbstractMemoizerTest<T extends Storage> {

  protected static final String TEST_FILE =
    "test&pixelType=int8&sizeX=20&sizeY=20&sizeC=1&sizeZ=1&sizeT=1.fake";

  protected File idDir;

  protected String id;

  protected FakeReader reader;

  protected Memoizer memoizer;

  protected T myStorage;

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

  abstract T mk(String id, File directory, boolean doInPlaceCaching);

  Storage mk(Storage storage, String id, File directory, boolean doInPlaceCaching) {
    if (storage != null) {
      return storage;
    }
    myStorage = mk(id, directory, doInPlaceCaching);
    return myStorage;
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

}
