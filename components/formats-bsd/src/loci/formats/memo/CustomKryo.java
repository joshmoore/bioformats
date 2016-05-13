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

import static com.esotericsoftware.kryo.util.Util.getWrapperClass;

import java.nio.channels.InterruptedByTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import loci.formats.Memoizer.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.ClassResolver;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryo.util.ObjectMap;

public class CustomKryo implements ClassResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomKryo.class);

  private static Map<Class, Integer> primitives = new HashMap<Class, Integer>();

  private static List<Class> primitivesIndex = new ArrayList<Class>();
  
  private static IntMap<Registration> idToReg = new IntMap<Registration>();

  private static ObjectMap<Class, Registration> klsToReg = new ObjectMap<Class, Registration>();

  // DON'T CHANGE THE ORDER
  static {
    // Defining them here so that if future versions of the superclass Kryo
    // change the ordering, our memo file won't be invalidated.
    primitivesIndex.add(null); // Kryo.NULL
    primitivesIndex.add(int.class);
    primitivesIndex.add(String.class);
    primitivesIndex.add(float.class);
    primitivesIndex.add(boolean.class);
    primitivesIndex.add(byte.class);
    primitivesIndex.add(char.class);
    primitivesIndex.add(short.class);
    primitivesIndex.add(long.class);
    primitivesIndex.add(double.class);
    primitivesIndex.add(void.class);
    for (int i=1; i<primitivesIndex.size(); i++) {
      primitives.put(primitivesIndex.get(i), i);
    }
  }

  private final int PRIMITIVES = 10;

  private final Storage storage;

  private Kryo kryo = null;

  public CustomKryo(Storage storage) {
    super(); // new CustomKryoResolver(), null); //new MapReferenceResolver());
    // Initialization in the super class won't have access to storage!
    this.storage = storage;
  }

  @Override
  public void setKryo(Kryo kryo) {
    this.kryo = kryo;
  }

  @Override
  public Registration getRegistration(Class type) {
      if (type == null) return null;
      Registration reg = klsToReg.get(type);
      if (reg == null) {
        if (type.isPrimitive() || type == String.class) {
          // Used during initialization
          int val = primitives.get(type);
          // Using Void since the value will be reset by the Kryo initializer
          reg = new Registration(type, new DefaultSerializers.VoidSerializer(), val);
        } else {
          try {
            int classID = storage.registerClass(type);
            // Indexing starts with a number of primitive entries like
            // [1, String] so we allow shifting
            reg = new Registration(type, kryo.getDefaultSerializer(type), classID+PRIMITIVES);
          } catch (InterruptedByTimeoutException ibte) {
            // Could try a retry...
            throw new RuntimeException(ibte);
          }
        }
        register(reg);
      }
      return reg;
  }

  @Override
  public Registration register(Registration reg) {
    idToReg.put(reg.getId(), reg);
    klsToReg.put(reg.getType(), reg);
    if (reg.getType().isPrimitive()) {
        klsToReg.put(getWrapperClass(reg.getType()), reg);
    }
    LOGGER.debug("{}", reg);
    return reg;
  }

  @Override
  public Registration registerImplicit(Class type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Registration getRegistration(int classID) {
    return idToReg.get(classID);
  }

  /** Primary method which auto-generates registrations on lookup by type */
  @Override
  public Registration writeClass(Output output, Class type) {
    if (type == null) {
      output.writeVarInt(Kryo.NULL, true);
      return null;
    }
    Registration reg = kryo.getRegistration(type);
    output.writeVarInt(reg.getId(), true);
    return reg;
  }

  @Override
  public Registration readClass(Input input) {
    int classID = input.readInt(true);
    if (classID == Kryo.NULL){ 
      return null;
    } else if (classID < PRIMITIVES) {
      return getRegistration(primitivesIndex.get(classID));
    } else {
      Class type = storage.findClass(classID-PRIMITIVES);
      Registration reg = getRegistration(type);
      if (reg == null) throw new KryoException(
            "Encountered unregistered class ID: " + classID);
      return reg;
    }
  }

  @Override
  public void reset() {
    // since the registration ordering is intended to be stable, we're not
    // going to do anything during reset.
  }

}