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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.InterruptedByTimeoutException;

import loci.formats.IFormatReader;
import loci.formats.Memoizer.Deser;
import loci.formats.Memoizer.InvalidFileException;
import loci.formats.Memoizer.Storage;

import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.io.CountingInputStream;

/**
 * TODO
 */
public class KryoDeser implements Deser {

  private static final Logger LOGGER = LoggerFactory.getLogger(KryoDeser.class);

  /**
   * first registration ID <em>after</em> those initially defined by {@link Kryo}
   */
  private final static int SHIFT = new Kryo().getNextRegistrationId();

  /**
   * Previously a public field -- downstream users should convert to using
   * a subclass to access this now protected field.
   */
  protected Kryo kryo;

  protected Storage storage;

  protected void initialize() {

    kryo = new Kryo() {

    @Override
    public Registration register(Class type, Serializer serializer) {
      try {
        int classID = storage.registerClass(type);
        return register(new Registration(type, serializer, classID));
      } catch (InterruptedByTimeoutException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Registration register(Class type) {
      throw new RuntimeException("only register(Class, Serializer) is allowed");
    }

    @Override
    public Registration register(Class type, int id) {
      throw new RuntimeException("only register(Class, Serializer) is allowed");
    }

    @Override
    public Registration register(Class type, Serializer serializer, int id) {
      throw new RuntimeException("only register(Class, Serializer) is allowed");
    }

      @Override
      public Registration getRegistration(Class type) {
          if (type == null) return null;
          Registration reg = null;
          try {
            reg = super.getRegistration(type);
          } catch (IllegalArgumentException iae) {
            try {
              int classID = storage.registerClass(type);
              // Indexing starts with a number of primitive entries like
              // [1, String] so we allow shifting
              reg = new Registration(type, getDefaultSerializer(type), classID);
              register(reg);
              LOGGER.debug("Registered {}", reg);
            } catch (InterruptedByTimeoutException ibte) {
              // Could try a retry...
              throw new RuntimeException(ibte);
            }
          }
          return reg;
      }

      @Override
      public Registration readClass(Input input) {
        int classID = input.readInt(true);
        if (classID == 0) return null;
        Class type = storage.findClass(classID);
        Registration reg = getRegistration(type);
        if (reg == null) throw new KryoException(
                "Encountered unregistered class ID: " + classID);
        return reg;
      }

    };
    // See https://github.com/EsotericSoftware/kryo/issues/216
    ((Kryo.DefaultInstantiatorStrategy) kryo.getInstantiatorStrategy())
        .setFallbackInstantiatorStrategy(new StdInstantiatorStrategy());
    kryo.setRegistrationRequired(true);
  }

  protected Input input;

  protected Output output;

  protected CountingInputStream cis;

  public void setStorage(Storage storage) {
    this.storage = storage;
    initialize();
  }

  @Override
  public void close() {
    try {
      loadStop();
      saveStop();
      kryo.reset();
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public void loadStart(InputStream is) {
    cis = new CountingInputStream(is);
    input = new Input(cis);
  }


  @Override
  public Integer loadVersion() {
    try {
      return kryo.readObject(input, Integer.class);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public String loadReleaseVersion() {
    try {
      return kryo.readObject(input, String.class);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public String loadRevision() {
    try {
      return kryo.readObject(input, String.class);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public IFormatReader loadReader() {
    try {
      Class<?> c = kryo.readObject(input, Class.class);
      return (IFormatReader) kryo.readObject(input, c);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public long loadStop() {
    if (input != null) {
      input.close();
      input = null;
    }
    if (cis == null) {
      return -1L;
    }
    return cis.getCount();
  }

  @Override
  public void saveStart(OutputStream os) {
    output = new Output(os);
  }

  @Override
  public void saveVersion(Integer version) {
    try {
      kryo.writeObject(output, version);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public void saveReleaseVersion(String version) {
    try {
      kryo.writeObject(output, version);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public void saveRevision(String revision) {
    try {
      kryo.writeObject(output, revision);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public void saveReader(IFormatReader reader) {
    try {
      kryo.writeObject(output, reader.getClass());
      kryo.writeObject(output, reader);
    } catch (KryoException ke) {
      throw new InvalidFileException(ke);
    }
  }

  @Override
  public void saveStop() {
    if (output != null) {
      output.close();
      output = null;
    }
  }
}
