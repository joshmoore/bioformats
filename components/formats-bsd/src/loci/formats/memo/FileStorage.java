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

package loci.formats.memo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import loci.common.Location;
import loci.formats.Memoizer.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class FileStorage implements Storage {

  /**
   * Default {@link org.slf4j.Logger} for the memoizer class
   */
  private static final Logger LOGGER =
    LoggerFactory.getLogger(FileStorage.class);

  // -- Fields --

  private Location realFile;

  private File memoFile;

  private File tempFile;

  private File directory;

  private InputStream is;

  private OutputStream os;

  private boolean doInPlaceCaching;

  // -- Constructors --

  public FileStorage(Location realFile, File directory, boolean doInPlaceCaching) {
    this.realFile = realFile;
    this.directory = directory;
    this.doInPlaceCaching = doInPlaceCaching;
    memoFile = getMemoFile(realFile.getAbsolutePath());
  }

  // -- Interface methods --

  public boolean isReady() {

    if (memoFile == null || !memoFile.exists()) {
      LOGGER.trace("Memo file doesn't exist: {}", memoFile);
      return false;
    }

    if(!memoFile.canRead()) {
      LOGGER.trace("Can't read memo file: {}", memoFile);
      return false;
    }

    long memoLast = memoFile.lastModified();
    long realLast = realFile.lastModified();
    if (memoLast < realLast) {
      LOGGER.debug("memo(lastModified={}) older than real(lastModified={})",
        memoLast, realLast);
      return false;
    }

    return true;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    if (is == null) {
      is = new FileInputStream(memoFile);
    }
    return is;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    if (os == null) {
      // Create temporary location for output
      // Note: can't rename tempfile until resources are closed.
      tempFile = File.createTempFile(
        memoFile.getName(), "", memoFile.getParentFile());

      LOGGER.debug("saving to temp file: {}", tempFile);
      os = new FileOutputStream(tempFile);
    }
    return os;
  }

  // Rename temporary file if successful.
  // Any failures will have to be ignored.
  // Note: renaming the tempfile with open
  // resources can lead to segfaults
  @Override
  public void commit() {
    if (!tempFile.renameTo(memoFile)) {
      LOGGER.error("temp file rename returned false: {}", tempFile);
    } else {
      LOGGER.debug("saved memo file: {} ({} bytes)",
        memoFile, memoFile.length());
    }
    Utils.deleteQuietly(LOGGER, tempFile);
  }

  @Override
  public void rollback() {
    Utils.deleteQuietly(LOGGER, tempFile);
  }

  @Override
  public void delete() {
    try {
      memoFile.delete();
    } catch (Exception e) {
      LOGGER.warn("exception on deleting {}", memoFile, e);
    }
  }


  @Override
  public void close() {
    if (os != null) {
      try {
        os.close();
      } catch (Exception e) {
        LOGGER.warn("exception on closing OutputStream: {}", this, e);
      }
      os = null;
    }
    if (is != null) {
      try {
        is.close();
      } catch (Exception e) {
        LOGGER.warn("exception on closing InputStream: {}", this, e);
      }
      is = null;
    }
  }

  // -- Implementation methods --

  /**
   * Constructs a {@link File} object from {@code id} string. This method
   * can be modified by consumers, but then existing memo files will not be
   * found.
   *
   * @param id the path passed to {@link #setId}
   * @return a {@link File} object pointing at the location of the memo file
   */
  public File getMemoFile(String id) {
    File f = null;
    File writeDirectory = null;
    if (directory == null && !doInPlaceCaching) {
      // Disabling memoization unless specific directory is provided.
      // This prevents random cache files from being unknowingly written.
      LOGGER.debug("skipping memo: no directory given");
      return null;
    } else {
      if (doInPlaceCaching || Utils.isRootDirectory(directory, id)) {
        f = new File(id);
        writeDirectory = new File(f.getParent());
      } else {
        // this serves to strip off the drive letter on Windows
        // since we're using the absolute path, 'id' will either start with
        // File.separator (as on UNIX), or a drive letter (as on Windows)
        id = id.substring(id.indexOf(File.separator) + 1);
        f = new File(directory, id);
        writeDirectory = directory;
      }

      // Check either the in-place folder or the main memoizer directory
      // exists and is writeable
      if (!writeDirectory.exists() || !writeDirectory.canWrite()) {
        LOGGER.warn("skipping memo: directory not writeable - {}",
          writeDirectory);
        return null;
      }

      f.getParentFile().mkdirs();
    }
    String p = f.getParent();
    String n = f.getName();
    return new File(p, "." + n + ".bfmemo");
  }

}
