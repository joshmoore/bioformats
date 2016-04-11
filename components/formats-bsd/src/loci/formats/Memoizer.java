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

package loci.formats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.InterruptedByTimeoutException;

import loci.common.Location;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.in.MetadataLevel;
import loci.formats.in.MetadataOptions;
import loci.formats.memo.FileStorage;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import loci.formats.services.OMEXMLServiceImpl;

import org.perf4j.StopWatch;
import org.perf4j.slf4j.Slf4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ReaderWrapper} implementation which caches the state of the
 * delegate (including and other {@link ReaderWrapper} instances)
 * after {@link #setId(String)} has been called.
 *
 * Initializing a Bio-Formats reader can consume substantial time and memory.
 * Most of the initialization time is spent in the {@link #setId(String)} call.
 * Various factors can impact the performance of this step including the file
 * size, the amount of metadata in the image and also the file format itself.
 *
 * With the {@link Memoizer} reader wrapper, if the time required to call the
 * {@link #setId(String)} method is larger than {@link #minimumElapsed}, the
 * initialized reader including all reader wrappers will be cached in a memo
 * file via {@link #saveMemo()}.
 * Any subsequent call to {@link #setId(String)} with a reader decorated by
 * the Memoizer on the same input file will load the reader from the memo file
 * using {@link #loadMemo()} instead of performing a full reader
 * initialization.
 *
 * In essence, the speed-up gained from memoization will happen only after the
 * first initialization of the reader for a particular file.
 */
public class Memoizer extends ReaderWrapper {

  /**
   * Interface for conversion from {@link FormatReader} to a stream.
   *
   * Methods should not throw implementation-specific exceptions (like
   * KryoException) and instead should wrap all such "(de-)serialization is
   * impossible"-style exceptions with an {@link InvalidFileException}.
   */
  public interface Deser {

    void setStorage(Storage storage);

    void loadStart(InputStream is) throws IOException;

    Integer loadVersion() throws IOException;

    String loadReleaseVersion() throws IOException;

    String loadRevision() throws IOException;

    IFormatReader loadReader() throws IOException, ClassNotFoundException;

    /**
     * @return the number of bytes read.
     * @throws IOException
     */
    long loadStop() throws IOException;

    void saveStart(OutputStream os) throws IOException;

    void saveVersion(Integer version) throws IOException;

    void saveReleaseVersion(String version) throws IOException;

    void saveRevision(String revision) throws IOException;

    void saveReader(IFormatReader reader) throws IOException;

    void saveStop() throws IOException;

    void close();

  }

  /**
   * Class thrown by {@link Deser} implementations to signal that the given
   * memo file is no longer usable. This could be caused by corruption, changes
   * in the underlying library version, changes in the classpath, or similar.
   */
  public static class InvalidFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidFileException(Throwable t) {
      super(t);
    }

  }

  /**
   * Interface for persisting the streams created by {@link Deser} instances
   * for later retrieval.
   */
  public interface Storage {

    /**
     * Registers the given type in a persistent, ordered way such that calls
     * to this method with the same class will always return the same Integer
     * even between JVM restarts.
     *
     * @throws InterruptedByTimeoutException
     */
    int registerClass(Class type) throws InterruptedByTimeoutException;

    /**
     * Lookup the value which was pass to {@link #registerClass(Class)} by the
     * value which that method returned.
     *
     * @param registrationID
     * @return null if the ID is not found.
     */
    Class findClass(int registrationID);

    /**
     * Whether or not the underlying storage mechanism can be read. Usually
     * not being ready to read will imply that {@link #writeReady()} will also
     * return false, though that is not gauranteed.
     */
    boolean readReady();

    /**
     * Whether or not the underlying storage mechanism can be written to.
     */
    boolean writeReady();

    /**
     * The caller is responsible for closing the stream as quickly as
     * possible. However, at the latest when {@link #close()} is called,
     * the streams will be closed.
     *
     * @return an {@link InputStream} ready for reading. Never null.
     * @throws IOException
     */
    InputStream getInputStream() throws IOException;

    void delete();

    /**
     * The caller is responsible for closing the stream as quickly as
     * possible. However, at the latest when {@link #close()} is called,
     * the streams will be closed.
     *
     * @return an {@link OutputStream} ready for writing. Never null.
     * @throws IOException
     */
    OutputStream getOutputStream() throws IOException;

    void close();

    void commit() throws IOException;

    void rollback();

  }

  @Deprecated
  public static class KryoDeser extends loci.formats.memo.KryoDeser { }

  // -- Constants --

  /**
   * Default file version. Bumping this number will invalidate all other
   * cached items. This should happen when the order and type of objects stored
   * in the memo file changes.
   */
  public static final Integer VERSION = 3;

  /**
   * Default value for {@link #minimumElapsed} if none is provided in the
   * constructor.
   */
  public static final long DEFAULT_MINIMUM_ELAPSED = 100;

  /**
   * Default {@link org.slf4j.Logger} for the memoizer class
   */
  private static final Logger LOGGER =
    LoggerFactory.getLogger(Memoizer.class);

  // -- Fields --

  /**
   * Minimum number of milliseconds which must elapse during the call to
   * {@link #setId} before a memo file will be created. Default to
   * {@link #DEFAULT_MINIMUM_ELAPSED} if not specified via the constructor.
   */
  private final long minimumElapsed;

  /**
   * Directory where all memo files should be created. If this value is
   * non-null, then all memo files will be created under it. Can be
   * overriden by {@link #doInPlaceCaching}.
   */
  protected final File directory;

  /**
   * If {@code true}, then all memo files will be created in the same
   * directory as the original file.
   */
  protected boolean doInPlaceCaching = false;

  protected transient Deser ser;

  protected transient Storage storage;

  private transient OMEXMLService service;

  protected Location realFile;

  private boolean skipLoad = false;

  private boolean skipSave = false;

  /**
   * Boolean specifying whether to invalidate the memo file based upon
   * mismatched major/minor version numbers. By default, the Git commit hash
   * is used to invalidate the memo file.
   */
  private boolean versionChecking = false;

  /**
   * Whether the {@link #reader} instance currently active was loaded from
   * the memo file during {@link #setId(String)}.
   */
  private boolean loadedFromMemo = false;

  /**
   * Whether the {@link #reader} instance was saved to a memo file on
   * {@link #setId(String)}.
   */
  private boolean savedToMemo = false;

  /**
   * {@link MetadataStore} set by the caller. This value will be held locally
   * and <em>not</em> set on the {@link #reader} delegate until the execution
   * of {@link #setId(String)}. If no value has been set by the caller, then
   * no special actions are taken during {@link #setId(String)}. If a value
   * is set, however, we must be careful with attempting to serialize it
   *
   * @see #handleMetadataStore(IFormatReader)
   */
  private MetadataStore userMetadataStore = null;

  // -- Constructors --

  /**
   *  Constructs a memoizer around a new {@link ImageReader} creating memo
   *  files under the same directory as the original file only if the call to
   *  {@link #setId} takes longer than {@value DEFAULT_MINIMUM_ELAPSED} in
   *  milliseconds.
   */
  public Memoizer() {
    this(DEFAULT_MINIMUM_ELAPSED);
    configure(reader.getMetadataOptions());
  }

  /**
   *  Constructs a memoizer around a new {@link ImageReader} creating memo
   *  files under the same directory as the original file only if the call to
   *  {@link #setId} takes longer than {@code minimumElapsed} in milliseconds.
   *
   *  @param minimumElapsed a long specifying the number of milliseconds which
   *         must elapse during the call to {@link #setId} before a memo file
   *         will be created.
   */
  public Memoizer(long minimumElapsed) {
    this(minimumElapsed, null);
    this.doInPlaceCaching = true;
    configure(reader.getMetadataOptions());
  }

  /**
   *  Constructs a memoizer around a new {@link ImageReader} creating memo file
   *  files under the {@code directory} argument including the full path of the
   *  original file only if the call to {@link #setId} takes longer than
   *  {@code minimumElapsed} in milliseconds.
   *
   *  @param minimumElapsed a long specifying the number of milliseconds which
   *         must elapse during the call to {@link #setId} before a memo file
   *         will be created.
   *  @param directory a {@link File} specifying the directory where all memo
   *         files should be created. If {@code null}, disable memoization.
   */
  public Memoizer(long minimumElapsed, File directory) {
    super();
    this.minimumElapsed = minimumElapsed;
    this.directory = directory;
    configure(reader.getMetadataOptions());
  }

  /**
   *  Constructs a memoizer around the given {@link IFormatReader} creating
   *  memo files under the same directory as the original file only if the
   *  call to {@link #setId} takes longer than
   *  {@value DEFAULT_MINIMUM_ELAPSED} in milliseconds.
   *
   *  @param r an {@link IFormatReader} instance
   */
  public Memoizer(IFormatReader r) {
    this(r, DEFAULT_MINIMUM_ELAPSED);
    configure(reader.getMetadataOptions());
  }

  /**
   *  Constructs a memoizer around the given {@link IFormatReader} creating
   *  memo files under the same directory as the original file only if the
   *  call to {@link #setId} takes longer than {@code minimumElapsed} in
   *  milliseconds.
   *
   *  @param r an {@link IFormatReader} instance
   *  @param minimumElapsed a long specifying the number of milliseconds which
   *         must elapse during the call to {@link #setId} before a memo file
   *         will be created.
   */
  public Memoizer(IFormatReader r, long minimumElapsed) {
    this(r, minimumElapsed, null);
    this.doInPlaceCaching = true;
    configure(reader.getMetadataOptions());
  }

  /**
   *  Constructs a memoizer around the given {@link IFormatReader} creating
   *  memo files under the {@code directory} argument including the full path
   *  of the original file only if the call to {@link #setId} takes longer than
   *  {@code minimumElapsed} in milliseconds.
   *
   *  @param r an {@link IFormatReader} instance
   *  @param minimumElapsed a long specifying the number of milliseconds which
   *         must elapse during the call to {@link #setId} before a memo file
   *         will be created.
   *  @param directory a {@link File} specifying the directory where all memo
   *         files should be created. If {@code null}, disable memoization.
   */
  public Memoizer(IFormatReader r, long minimumElapsed, File directory) {
    super(r);
    this.minimumElapsed = minimumElapsed;
    this.directory = directory;
    configure(reader.getMetadataOptions());
  }

  /**
   * Used to inject all the properties necessary for {@link Memoizer}
   * creation into {@link MetadataOptions}. This is called by every
   * constructor so that {@link IFormatReader} instances created
   * internally can also make use of memoization.
   *
   * @param options
   */
  public void configure(MetadataOptions options) {
    String k = Memoizer.class.getName();
    options.setMetadataOption(k + ".cacheDirectory", this.directory);
    options.setMetadataOption(k + ".inPlace", this.doInPlaceCaching);
    options.setMetadataOption(k + ".minimumElapsed", this.minimumElapsed);
    reader.setMetadataOptions(options);
  }

  public void setMetadataOptions(MetadataOptions options) {
    configure(options);
    reader.setMetadataOptions(options);
  }

  /**
   * If {@link MetadataOptions} have been configured per
   * {@link #configure(MetadataOptions)}, then wrap the given
   * {@link IFormatReader} with a {@link Memoizer} instance and return.
   * Otherwise, return the {@link IFormatReader} unchanged.
   *
   * @param options If null, return the reader
   * @param r
   * @return Either a {@link Memoizer} or the {@link IFormatReader} argument.
   */
  public static IFormatReader wrap(MetadataOptions options, IFormatReader r) {
    if (options == null) {
      return null;
    }
    String k = Memoizer.class.getName();
    Object elapsed = options.getMetadataOption(k + ".minimumElapsed");
    Object inplace = options.getMetadataOption(k + ".inPlace");
    Object cachedir = options.getMetadataOption(k + ".cacheDirectory");
    if (elapsed == null && inplace == null && cachedir == null) {
      LOGGER.trace("config: Memoizer is not configured");
      return r;
    } else if (!(elapsed instanceof Long)) {
      LOGGER.warn("config: minimumElapsed wrong type: {}", elapsed);
      return r;
    } else if (!(inplace instanceof Boolean)) {
      LOGGER.warn("config: inplace wrong type: {}", inplace);
      return r;
    } else if (cachedir != null && !(cachedir instanceof File)) {
      LOGGER.warn("config: cachedir wrong type: {}", cachedir);
      return r;
    }

    if (((Boolean) inplace).booleanValue()) {
      return new Memoizer(r, (Long) elapsed);
    } else{
      return new Memoizer(r, (Long) elapsed, (File) cachedir);
    }
  }

  /**
   *  Returns whether the {@link #reader} instance currently active was loaded
   *  from the memo file during {@link #setId(String)}.
   *
   *  @return {@code true} if the reader was loaded from the memo file,
   *  {@code false} otherwise.
   */
  public boolean isLoadedFromMemo() {
    return loadedFromMemo;
  }

  /**
   *  Returns whether the {@link #reader} instance currently active was saved
   *  to the memo file during {@link #setId(String)}.
   *
   *  @return {@code true} if the reader was saved to the memo file,
   *  {@code false} otherwise.
   */
  public boolean isSavedToMemo() {
    return savedToMemo;
  }

  /**
   * Returns whether or not version checking is done based upon major/minor
   * version numbers.
   *
   *  @return {@code true} if version checking is done based upon
   *  major/minor version numbers, {@code false} otherwise.
   */
  public boolean isVersionChecking() {
    return versionChecking;
  }

  /**
   * Returns {@code true} if the version of the memo file as returned by
   * {@link Deser#loadReleaseVersion()} and {@link Deser#loadRevision()}
   * do not match the current version as specified by {@link FormatTools#VERSION}
   * and {@link FormatTools#VCS_REVISION}, respectively.
   */
  public boolean versionMismatch() throws IOException {

      final String releaseVersion = ser.loadReleaseVersion();
      final String revision = ser.loadRevision();

      if (!isVersionChecking()) {
        return false;
      }

      String minor = releaseVersion;
      int firstDot = minor.indexOf(".");
      if (firstDot >= 0) {
        int secondDot = minor.indexOf(".", firstDot + 1);
        if (secondDot >= 0) {
          minor = minor.substring(0, secondDot);
        }
      }

      String currentMinor = FormatTools.VERSION.substring(0,
        FormatTools.VERSION.indexOf(".", FormatTools.VERSION.indexOf(".") + 1));
      if (!currentMinor.equals(minor)) {
        LOGGER.info("Different release version: {} not {}",
          releaseVersion, FormatTools.VERSION);
        return true;
      }

      // REVISION NUMBER
      if (!versionChecking && !FormatTools.VCS_REVISION.equals(revision)) {
        LOGGER.info("Different Git version: {} not {}",
          revision, FormatTools.VCS_REVISION);
        return true;
      }

      return false;
  }

  /**
   * Set whether version checking is done based upon major/minor version
   * numbers.
   *
   * If {@code true}, then a mismatch between the major/minor version of the
   * calling code (e.g. 4.4) and the major/minor version saved in the memo
   * file (e.g. 5.0) will result in the memo file being invalidated.
   *
   * If {@code false} (default), a mismatch in the Git commit hashes will
   * invalidate the memo file.
   *
   * This method allows for less strict version checking.
   *
   *  @param version a boolean specifying whether version checking is done
   *  based upon major/minor version numbers to invalidate the memo file
   *
   */
  public void setVersionChecking(boolean version) {
    this.versionChecking = version;
  }

  protected void cleanup() {
    if (ser != null) {
      ser.close();
      ser = null;
    }
    if (storage != null) {
      storage.close();
      storage = null;
    }
  }

  @Override
  public void close() throws IOException {
    try {
      cleanup();
    } finally {
      super.close();
    }
  }

  @Override
  public void close(boolean fileOnly) throws IOException {
    try {
      cleanup();
    } finally {
      super.close(fileOnly);
    }
  }

  // -- ReaderWrapper API methods --

  /**
   * Primary driver of the {@link Memoizer} instance.
   */
  @Override
  public void setId(String id) throws FormatException, IOException {
    StopWatch sw = stopWatch();
    try {
      realFile = new Location(id);
      if (storage != null) {
        // Reload the storage on setId
        storage.close();
        storage = null;
      }
      storage = getStorage();

      if (storage == null) {
        // Memoization disabled.
        if (userMetadataStore != null) {
          reader.setMetadataStore(userMetadataStore);
        }
        super.setId(id); // EARLY EXIT
        return;
      }

      IFormatReader memo = loadMemo(); // Should never throw implementation exceptions

      loadedFromMemo = false;
      savedToMemo = false;

      if (memo != null) {
          loadedFromMemo = true;
          reader = memo;
      }

      if (memo == null) {
        OMEXMLService service = getService();
        OMEXMLMetadata all = service.createOMEXMLMetadata();
        OMEXMLMetadata min = service.createOMEXMLMetadata();

        // Load all the data for use
        super.setMetadataStore(all);
        long start = System.currentTimeMillis();
        super.setId(id);

        try {
          long elapsed = System.currentTimeMillis() - start;
          handleMetadataStore(null); // Between setId and saveMemo
          if (elapsed < minimumElapsed) {
            LOGGER.debug("skipping save memo. elapsed millis: {}", elapsed);
            return; // EARLY EXIT!
          }
          // but only persist the minimum information
          convertMetadata(all, min);
          reader.setMetadataStore(min);
          savedToMemo = saveMemo(); // Should never throw.
        } finally {
          super.setMetadataStore(all);
        }
      }
    } catch (ServiceException e) {
      LOGGER.error("Could not create OMEXMLMetadata", e);
    } finally {
      sw.stop("loci.formats.Memoizer.setId");
    }
  }

  @Override
  public void setMetadataStore(MetadataStore store) {
    this.userMetadataStore = store;
  }

  @Override
  public MetadataStore getMetadataStore() {
    if (this.userMetadataStore != null) {
      return this.userMetadataStore;
    }
    return reader.getMetadataStore();
  }

  //-- Helper methods --

  /**
   * Returns a configured {@link KryoDeser} instance. This method can be modified
   * by consumers. The returned instance is not thread-safe.
   *
   * @return a non-null {@link KryoDeser} instance.
   */
  protected Deser getDeser() {
    if (ser == null) {
      ser = new KryoDeser();
    }
    return ser;
  }

  /**
   * Returns a configured {@link FileStorage} instance. This method can be
   * modified by consumers. The returned instance is not thread-safe.
   *
   * @return a {@link FileStorage} instance. If null, memoization is disabled.
   */
  public Storage getStorage() {
    if (storage == null) {
      storage = new FileStorage(realFile, directory, doInPlaceCaching);
    }
    return storage;
  }

  // Copied from OMETiffReader.
  protected OMEXMLService getService() throws MissingLibraryException {
    if (service == null) {
      try {
        ServiceFactory factory = new ServiceFactory();
        service = factory.getInstance(OMEXMLService.class);
      } catch (DependencyException de) {
        throw new MissingLibraryException(OMEXMLServiceImpl.NO_OME_XML_MSG, de);
      }
    }
    return service;
  }
  protected Slf4JStopWatch stopWatch() {
      return new Slf4JStopWatch(LOGGER, Slf4JStopWatch.DEBUG_LEVEL);
  }



  /**
   * Load a memo file if possible, returning a null if not.
   *
   * Corrupt memo files will be deleted if possible. Kryo
   * exceptions should never propagate to the caller. Only
   * the regular Bio-Formats exceptions should be thrown.
   */
  public IFormatReader loadMemo() throws IOException, FormatException {

    if (skipLoad) {
      LOGGER.trace("skip load");
      return null;
    }

    final Deser ser = getDeser();
    final Storage storage = getStorage();
    ser.setStorage(storage);

    if (!storage.readReady()) {
      LOGGER.trace("storage not ready");
      return null;
    }

    final StopWatch sw = stopWatch();
    IFormatReader copy = null;
    long bytesRead = -1L;

    InputStream is = storage.getInputStream();
    if (is == null) {
      LOGGER.trace("no input stream");
      return null;
    }

    boolean fail = false;
    try {
      ser.loadStart(is);

      // VERSION
      Integer version = ser.loadVersion();
      if (!VERSION.equals(version)) {
        LOGGER.info("Old version of memo file: {} not {}", version, VERSION);
        storage.delete();
        return null;
      }

      // RELEASE VERSION NUMBER
       if (versionMismatch()) {
         // Logging done in versionMismatch
         storage.delete();
         return null;
       }

      // CLASS & COPY
      try {
        copy = ser.loadReader();
      } catch (ClassNotFoundException e) {
        LOGGER.warn("unknown reader type: {}", e);
        storage.delete();
        return null;
      }

      boolean equal = false;
      try {
        equal = FormatTools.equalReaders(reader, copy);
      } catch (RuntimeException rt) {
        copy.close();
        throw rt;
      } catch (Error err) {
        copy.close();
        throw err;
      }

      if (!equal) {
        copy.close();
        return null;
      }

      copy = handleMetadataStore(copy);
      if (copy == null) {
        LOGGER.debug("metadata store invalidated cache: {}", storage);
        fail = true;
      } else {
        fail = false;
        copy.reopenFile();
        return copy;
      }

    } catch (FileNotFoundException e) {
      LOGGER.info("could not reopen file - deleting invalid memo file: {}", storage);
      fail = true;
    } catch (InvalidFileException e) {
      LOGGER.warn("deleting invalid memo file: {}", storage, e);
      fail = true;
    } catch (ArrayIndexOutOfBoundsException e) {
      LOGGER.warn("deleting invalid memo file: {}", storage, e);
      fail = true;
    } catch (Throwable t) {
      // Logging at error since this is unexpected.
      LOGGER.error("deleting invalid memo file: {}", storage, t);
      fail = true;
    } finally {

      if (fail) {
        if (copy != null) {
          copy.close();
        }
        storage.delete();
      }

      bytesRead = ser.loadStop();
      sw.stop("loci.formats.Memoizer.loadMemo");
      LOGGER.debug("loaded memo file: {} ({} bytes)",
        storage, bytesRead);
    }

    // Only reached in a fail state
    return null;

  }

  /**
   * Save a reader including all reader wrappers inside a memo file.
   */
  public boolean saveMemo() {

    if (skipSave) {
      LOGGER.trace("skip memo");
      return false;
    }

    if (!storage.writeReady()) {
      LOGGER.trace("storage not write ready");
      return false;
    }

    final Deser ser = getDeser();
    final Storage storage = getStorage();
    ser.setStorage(storage);
    final StopWatch sw = stopWatch();
    boolean rv = true;

    OutputStream os = null;
    try {
      os = storage.getOutputStream();
      ser.saveStart(os);

      // Save to temporary location.
      ser.saveVersion(VERSION);
      ser.saveReleaseVersion(FormatTools.VERSION);
      ser.saveRevision(FormatTools.VCS_REVISION);
      ser.saveReader(reader);
      ser.saveStop();

    } catch (Throwable t) {
      // Any exception should be ignored, and false returned.
      LOGGER.warn("failed to save memo file: {}", storage, t);
      storage.rollback();
      rv = false;

    } finally {

      try {
        if (os != null) {
          os.close();
        }
      } catch (Exception e) {
        LOGGER.warn("failed to close FileOutputStream", e);
      }

      // Close the output stream quietly regardless.
      try {
        ser.saveStop();
        sw.stop("loci.formats.Memoizer.saveMemo");
      } catch (Throwable t) {
        LOGGER.warn("output close failed", t);
      }

      if (rv) {
        try {
          storage.commit();
        } catch (Exception e) {
          LOGGER.error("commit on storage failed: {}", storage, e);
          storage.rollback();
          storage.close();
          rv = false;
        }
      }
    }
    return rv;
  }


  /**
   * Return the {@link IFormatReader} instance that is passed in or null if
   * it has been invalidated, which will include the instance being closed.
   *
   * <ul>
   *  <li><em>Serialization:</em> If an unknown {@link MetadataStore}
   *  implementation is passed in when no memo file exists, then a replacement
   *  {@link MetadataStore} will be created and set on the {@link #reader}
   *  delegate before calling {@link ReaderWrapper#setId(String)}. This stack
   *  will then be serialized, before any values are copied into
   *  {@link #userMetadataStore}.
   *  </li>
   *  <li><em>Deserialization<em>: If an unknown {@link MetadataStore}
   *  implementation is set before calling {@link #setId(String)} then ...
   *  </li>
   * </ul>
   */
  protected IFormatReader handleMetadataStore(IFormatReader memo) throws MissingLibraryException {

    // Then nothing has been requested of us
    // and we can exit safely.
    if (userMetadataStore == null) {
      return memo; // EARLY EXIT. Possibly null.
    }

    // If we've been passed a memo object, then it's just been loaded.
    final boolean onLoad = (memo != null);

    // TODO: What to do if the contained store is a Dummy?
    // TODO: Which stores should we handle regularly?

    if (onLoad) {
      MetadataStore filledStore = memo.getMetadataStore();
      // Return value is important.
      if (filledStore == null) {
          // TODO: what now?
      } else if (!(filledStore instanceof MetadataRetrieve)) {
          LOGGER.error("Found non-MetadataRetrieve: {}" + filledStore.getClass());
      } else {
        convertMetadata((MetadataRetrieve) filledStore, userMetadataStore);
      }
    } else {
      // on save; we've just called super.setId()
      // Then pull out the reader and replace it.
      // Return value is unimportant.
      MetadataStore filledStore = reader.getMetadataStore();
      if (reader.getMetadataStore() == null) {
        // TODO: what now?
        LOGGER.error("Found null-MetadataRetrieve: {}" + filledStore.getClass());
      } else if (!(filledStore instanceof MetadataRetrieve)) {
        LOGGER.error("Found non-MetadataRetrieve: {}" + filledStore.getClass());
      } else {
        convertMetadata((MetadataRetrieve) filledStore, userMetadataStore);
      }

    }
    return memo;
  }

  private void convertMetadata(MetadataRetrieve retrieve, MetadataStore store)
    throws MissingLibraryException {
    OMEXMLService service = getService();
    service.convertMetadata(retrieve, store, MetadataLevel.MINIMUM);
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0 || args.length > 2) {
      System.err.println("Usage: memoizer file [tmpdir]");
      System.exit(2);
    }

    File tmp = new File(System.getProperty("java.io.tmpdir"));
    if (args.length == 2) {
      tmp = new File(args[1]);
    }

    System.out.println("First load of " + args[0]);
    load(args[0], tmp, true); // initial
    System.out.println("Second load of " + args[0]);
    load(args[0], tmp, false); // reload
  }

  private static void load(String id, File tmp, boolean delete) throws Exception {
    Memoizer m = new Memoizer(0L, tmp);
    FileStorage s = (FileStorage) m.getStorage(); // Assuming default

    File memo = s.getMemoFile(id);
    if (delete && memo != null && memo.exists()) {
        System.out.println("Deleting " + memo);
        memo.delete();
    }

    m.setVersionChecking(false);
    try {
      m.setId(id);
      m.openBytes(0);
      IFormatReader r = m.getReader();
      r = ((ImageReader) r).getReader();
      System.out.println(r);
    } finally {
      m.close();
    }
  }

}
