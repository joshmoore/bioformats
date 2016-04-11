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

import java.io.Closeable;
import java.io.File;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;

/**
 * public to allow subclassing for unit tests
 */
public class Utils {

  /**
   * Takes any number of {@link Closeable} arguments
   * and calls {@link Closeable#close()} on each of
   * them in a loop. Exceptions are printed at WARN.
   **/
  protected static void closeQuietly(Logger LOGGER, Closeable...cs) {
    for (Closeable c : cs) {
      try {
        c.close();
      } catch (Exception e) {
        LOGGER.warn("failed to close {}", c, e);
      }
    }
  }

  /**
   * Attempts to delete an existing file, logging at
   * warn if the deletion returns false or at error
   * if an exception is thrown.
   *
   * @return the result from {@link java.io.File#delete} or {@code false} if
   * an exception is thrown.
   */
  protected static boolean deleteQuietly(Logger LOGGER, File file) {
    try {
      if (file != null && file.exists()) {
        if (file.delete()) {
          LOGGER.trace("deleted {}", file);
          return true;
        } else {
          LOGGER.warn("file deletion failed {}", file);
        }
      }
    } catch (Throwable t) {
      LOGGER.error("file deletion failed: {}", file, t);
    }
    return false;
  }

  /**
   * If the memoizer directory is set to be the root folder, the memo file
   * will be saved in the same folder as the file specified by id. Since
   * the root folder will likely not be writeable by the user, we want to
   * exclude this special case from the test below
   */
  protected static boolean isRootDirectory(File directory, String id) {
    id = new File(id).getAbsolutePath();
    String rootPath = id.substring(0, id.indexOf(File.separator) + 1);
    return directory.getAbsolutePath().equals(rootPath);
  }

  protected static DB memoDB(File writeDirectory) {
    return DBMaker.fileDB(new File(writeDirectory, "bfmemo.db"))
      .transactionDisable()
      .allocateStartSize(  5 * 1024*1024*1024) // 5 GB
      .allocateIncrement(  1 * 1024*1024*1024) // 1 GB
      .closeOnJvmShutdown()
      .make();
  }

  protected static DB lockDB() {
    return DBMaker.memoryDB()
      .transactionDisable()
      .serializerRegisterClass(int.class)
      //.cacheWeakRefEnable()
      //.cacheExecutorEnable()
      .make();
  }

  protected static HTreeMap<String, byte[]> memoTree(DB db) {
    return db
      .hashMapCreate("memos")
      .keySerializer(Serializer.STRING)
      .valueSerializer(Serializer.BYTE_ARRAY)
      .makeOrGet();
  }

  protected static HTreeMap<Class, Integer> regTree(DB db) {
    return db
      .hashMapCreate("classRegistry")
      .keySerializer(Serializer.CLASS)
      .valueSerializer(Serializer.INTEGER)
      .makeOrGet();
  }

  protected static NavigableSet<Object[]> inverse(HTreeMap<Class, Integer> map) {
    NavigableSet<Object[]> inverseMapping = new TreeSet<Object[]>(
            Fun.COMPARABLE_ARRAY_COMPARATOR);

    Bind.mapInverse(map, inverseMapping);
    return inverseMapping;
  }

  protected static HTreeMap<String, String> lockTree(DB db) {
    return db
      .hashMapCreate("locks")
      .keySerializer(Serializer.STRING)
      .valueSerializer(Serializer.STRING)
      .makeOrGet();
  }

}
