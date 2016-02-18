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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import loci.formats.IFormatReader;
import loci.formats.Memoizer.Deser;

import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * TODO
 */
public class KryoDeser implements Deser {

  /**
   * Default {@link org.slf4j.Logger} for the memoizer class
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(KryoDeser.class);

  @Deprecated
  final public Kryo kryo = new Kryo() {

      int count = 0;

      @Override
      public Registration getRegistration(Class k) {
          Registration rv = this.getClassResolver().getRegistration(k);
          if (rv == null) {
              rv = new Registration(k, getDefaultSerializer(k), count++);
              System.out.println("REGISTRATION: " + k + " --> " + count);
              this.register(rv);
          }
          return rv;
      }

  };

  {
    // See https://github.com/EsotericSoftware/kryo/issues/216
    ((Kryo.DefaultInstantiatorStrategy) kryo.getInstantiatorStrategy())
        .setFallbackInstantiatorStrategy(new StdInstantiatorStrategy());
    // The goal here is to eventually turn this on, but for the moment,
    // the getRegistration method will auto-register, so required=true
    // would have no effect.
    // kryo.setRegistrationRequired(true);
  }

  FileInputStream fis;
  FileOutputStream fos;
  Input input;
  Output output;

  @Override
  public void close() {
    loadStop();
    saveStop();
    kryo.reset();
  }

  @Override
  public void loadStart(File memoFile) throws FileNotFoundException {
    fis = new FileInputStream(memoFile);
    input = new Input(fis);
  }

  @Override
  public Integer loadVersion() {
    return kryo.readObject(input, Integer.class);
  }

  @Override
  public String loadReleaseVersion() {
    return kryo.readObject(input, String.class);
  }

  @Override
  public String loadRevision() {
    return kryo.readObject(input, String.class);
  }

  @Override
  public IFormatReader loadReader() {
    Class<?> c = kryo.readObject(input, Class.class);
    return (IFormatReader) kryo.readObject(input, c);
  }

  @Override
  public void loadStop() {
    if (input != null) {
      input.close();
      input = null;
    }
    if (fis != null) {
      try {
        fis.close();
      } catch (IOException e) {
        LOGGER.error("failed to close KryoDeser.fis", e);
      }
      fis = null;
    }
  }

  @Override
  public void saveStart(File tempFile) throws FileNotFoundException {
    fos = new FileOutputStream(tempFile);
    output = new Output(fos);
  }

  @Override
  public void saveVersion(Integer version) {
    kryo.writeObject(output, version);
  }

  @Override
  public void saveReleaseVersion(String version) {
    kryo.writeObject(output, version);
  }

  @Override
  public void saveRevision(String revision) {
    kryo.writeObject(output, revision);
  }

  @Override
  public void saveReader(IFormatReader reader) {
    kryo.writeObject(output, reader.getClass());
    kryo.writeObject(output, reader);
  }

  @Override
  public void saveStop() {
    if (output != null) {
      output.close();
      output = null;
    }
    if (fos != null) {
      try {
        fos.close();
        fos = null;
      } catch (IOException e) {
        LOGGER.error("failed to close KryoDeser.fis", e);
      }
    }
  }

}