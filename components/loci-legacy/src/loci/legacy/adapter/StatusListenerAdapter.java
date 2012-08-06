/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2012 Open Microscopy Environment:
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
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package loci.legacy.adapter;

import java.util.Hashtable;

import loci.common.StatusEvent;
import loci.common.StatusListener;

/**
 * As delegators can not contain implementation, this class manages
 * general delegation between {@link loci.common.StatusListener} and
 * {@link ome.scifio.common.StatusListener}
 * 
 * Delegation is maintained by two Hashtables - every legacy and current
 * class appears once in each Hashtables. Once as a key, and once as a value.
 * 
 * Functionally, the delegation is handled in the private classes - one for
 * moving from ome.scifio.common.StatusListener to loci.common.StatusListener,
 * and one for the reverse direction.
 * 
 * @author Mark Hiner
 *
 */
public class StatusListenerAdapter implements 
  LegacyAdapter<StatusListener, ome.scifio.common.StatusListener> {
  
  // -- Fields --
  
  private Hashtable<StatusListener, ome.scifio.common.StatusListener> commonToScifio =
    new Hashtable<StatusListener, ome.scifio.common.StatusListener>();
  private Hashtable<ome.scifio.common.StatusListener, StatusListener> scifioToCommon =
    new Hashtable<ome.scifio.common.StatusListener, StatusListener>();
  
  // -- LegacyAdapter API --
  
  /**
   * Returns the mapped PassToCommon object for the provided 
   * loci.common.StatusListener object.
   * 
   * Creates a new PassToCommon wrapper if the mapping does not
   * already exist.
   */
  public ome.scifio.common.StatusListener getCurrent(StatusListener sl) {
    ome.scifio.common.StatusListener ret = commonToScifio.get(sl);
    
    if(ret == null) {
      ret = new LegacyWrapper(sl);
      commonToScifio.put(sl, ret);
      scifioToCommon.put(ret, sl);
    }
    
    return ret;
  }
  
  /**
   * Returns the mapped PassToScifio object for the provided 
   * ome.scifio.common.StatusListener object.
   * 
   * Creates a new PassToCommon wrapper if the mapping does not
   * already exist.
   */
  public StatusListener getLegacy(ome.scifio.common.StatusListener sl) {
    StatusListener ret = scifioToCommon.get(sl);
    
    if (ret == null) {
      ret = new SCIFIOWrapper(sl);
      commonToScifio.put(ret, sl);
      scifioToCommon.put(sl, ret);
    }
    
    return ret;
  }
  
  /* See LegacyAdapter#clear() */
  public void clear() {
    this.commonToScifio.clear();
    this.scifioToCommon.clear();
  }
  
  // -- Delegation Classes --

  public static class LegacyWrapper implements ome.scifio.common.StatusListener {
    
    // -- Fields --
    
    private StatusListener sl;
    
    // -- Constructor --
    
    public LegacyWrapper(StatusListener sl) {
      this.sl = sl;
    }
    
    // -- StatusListener API --

    public void statusUpdated(ome.scifio.common.StatusEvent e) {
      sl.statusUpdated(new StatusEvent(e));
    }
    
    // -- Object delegators --

    @Override
    public boolean equals(Object obj) {
      return sl.equals(obj);
    }
    
    @Override
    public int hashCode() {
      return sl.hashCode();
    }
    
    @Override
    public String toString() {
      return sl.toString();
    }

  }
  
  public static class SCIFIOWrapper implements StatusListener {
    
    // -- Fields --

    private ome.scifio.common.StatusListener sl;

    // -- Constructor --

    public SCIFIOWrapper(ome.scifio.common.StatusListener sl) {
      this.sl = sl;
    }

    // -- StatusListener API --

    public void statusUpdated(loci.common.StatusEvent e) {
      sl.statusUpdated(e.getEvent());
    }
    
    // -- Object delegators --

    @Override
    public boolean equals(Object obj) {
      return sl.equals(obj);
    }
    
    @Override
    public int hashCode() {
      return sl.hashCode();
    }
    
    @Override
    public String toString() {
      return sl.toString();
    }

  }
}
