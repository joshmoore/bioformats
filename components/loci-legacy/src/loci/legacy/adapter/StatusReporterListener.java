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
import loci.common.StatusReporter;

/**
 * As delegators can not contain implementation, this class manages
 * general delegation between {@link loci.common.StatusReporter} and
 * {@link ome.scifio.common.StatusReporter}
 * 
 * Delegation is maintained by two Hashtables - every legacy and current
 * class appears once in each Hashtables. Once as a key, and once as a value.
 * 
 * Functionally, the delegation is handled in the private classes - one for
 * moving from ome.scifio.common.StatusReporter to loci.common.StatusReporter,
 * and one for the reverse direction.
 * 
 * @author Mark Hiner
 *
 */
public class StatusReporterListener implements 
  LegacyAdapter<StatusReporter, ome.scifio.common.StatusReporter> {
  
  // -- Fields --
  
  private Hashtable<StatusReporter, ome.scifio.common.StatusReporter> commonToScifio =
    new Hashtable<StatusReporter, ome.scifio.common.StatusReporter>();
  private Hashtable<ome.scifio.common.StatusReporter, StatusReporter> scifioToCommon =
    new Hashtable<ome.scifio.common.StatusReporter, StatusReporter>();
  
  // -- LegacyAdapter API --
  
  /**
   * Returns the mapped PassToCommon object for the provided 
   * loci.common.StatusReporter object.
   * 
   * Creates a new PassToCommon wrapper if the mapping does not
   * already exist.
   */
  public ome.scifio.common.StatusReporter getCurrent(StatusReporter sr) {
    ome.scifio.common.StatusReporter ret = commonToScifio.get(sr);
    
    if(ret == null) {
      ret = new LegacyWrapper(sr);
      commonToScifio.put(sr, ret);
      scifioToCommon.put(ret, sr);
    }
    
    return ret;
  }
  
  /**
   * Returns the mapped PassToScifio object for the provided 
   * ome.scifio.common.StatusReporter object.
   * 
   * Creates a new PassToCommon wrapper if the mapping does not
   * already exist.
   */
  public StatusReporter getLegacy(ome.scifio.common.StatusReporter sr) {
    StatusReporter ret = scifioToCommon.get(sr);
    
    if (ret == null) {
      ret = new SCIFIOWrapper(sr);
      commonToScifio.put(ret, sr);
      scifioToCommon.put(sr, ret);
    }
    
    return ret;
  }
  
  /* See LegacyAdapter#clear() */
  public void clear() {
    this.commonToScifio.clear();
    this.scifioToCommon.clear();
  }
  
  // -- Delegation Classes --

  public static class LegacyWrapper implements ome.scifio.common.StatusReporter {
    
    // -- Fields --
    
    private StatusReporter sr;
    
    private StatusListenerAdapter adapter = new StatusListenerAdapter();
    
    // -- Constructor --
    
    public LegacyWrapper(StatusReporter sr) {
      this.sr = sr;
    }
    
    // -- StatusReporter API --

    public void addStatusListener(ome.scifio.common.StatusListener l) {
      sr.addStatusListener(adapter.getLegacy(l));
    }

    public void removeStatusListener(ome.scifio.common.StatusListener l) {
      sr.removeStatusListener(adapter.getLegacy(l));
    }

    public void notifyListeners(ome.scifio.common.StatusEvent e) {
      sr.notifyListeners(new StatusEvent(e));
    }
    
  }
  
  public static class SCIFIOWrapper implements StatusReporter {
    
    // -- Fields --

    private ome.scifio.common.StatusReporter sr;
    
    private StatusListenerAdapter adapter = new StatusListenerAdapter();


    // -- Constructor --

    public SCIFIOWrapper(ome.scifio.common.StatusReporter sr) {
      this.sr = sr;
    }
    
    // -- StatusReporter API --

    public void addStatusListener(StatusListener l) {
      sr.addStatusListener(adapter.getCurrent(l));
    }

    public void removeStatusListener(StatusListener l) {
      sr.removeStatusListener(adapter.getCurrent(l));
    }

    public void notifyListeners(StatusEvent e) {
      sr.notifyListeners(e.getEvent());
    }
  }
}
