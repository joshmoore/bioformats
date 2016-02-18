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
public class FilebasedKryoDeser extends AbstractKryoDeser {

  /**
   * Default {@link org.slf4j.Logger} for the memoizer class
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FilebasedKryoDeser.class);

  FileInputStream fis;
  FileOutputStream fos;

  @Override
  public void loadStart(File memoFile) throws FileNotFoundException {
    fis = new FileInputStream(memoFile);
    input = new Input(fis);
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