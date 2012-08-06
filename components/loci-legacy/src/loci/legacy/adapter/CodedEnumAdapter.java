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

import loci.common.enumeration.CodedEnum;;

/**
 * As delegators can not contain implementation, this class manages
 * general delegation between {@link loci.common.enumeration.CodedEnum} and
 * {@link ome.scifio.enumeration.CodedEnum}
 * 
 * Delegation is maintained by two Hashtables - every legacy and current
 * class appears once in each Hashtables. Once as a key, and once as a value.
 * 
 * Functionally, the delegation is handled in the private classes - one for
 * moving from ome.scifio.enumeration.CodedEnum to loci.common.enumeration.CodedEnum,
 * and one for the reverse direction.
 * 
 * @author Mark Hiner
 *
 */
public class CodedEnumAdapter implements 
  LegacyAdapter<CodedEnum, ome.scifio.enumeration.CodedEnum> {
  
  // -- Fields --
  
  private Hashtable<CodedEnum, ome.scifio.enumeration.CodedEnum> commonToScifio =
    new Hashtable<CodedEnum, ome.scifio.enumeration.CodedEnum>();
  private Hashtable<ome.scifio.enumeration.CodedEnum, CodedEnum> scifioToCommon =
    new Hashtable<ome.scifio.enumeration.CodedEnum, CodedEnum>();
  
  // -- LegacyAdapter API --
  
  /**
   * Returns the mapped PassToCommon object for the provided 
   * loci.common.CodedEnum object.
   * 
   * Creates a new PassToCommon wrapper if the mapping does not
   * already exist.
   */
  public ome.scifio.enumeration.CodedEnum getCurrent(CodedEnum ce) {
    ome.scifio.enumeration.CodedEnum ret = commonToScifio.get(ce);
    
    if(ret == null) {
      ret = new LegacyWrapper(ce);
      commonToScifio.put(ce, ret);
      scifioToCommon.put(ret, ce);
    }
    
    return ret;
  }
  
  /**
   * Returns the mapped PassToScifio object for the provided 
   * ome.scifio.enumeration.CodedEnum object.
   * 
   * Creates a new PassToCommon wrapper if the mapping does not
   * already exist.
   */
  public CodedEnum getLegacy(ome.scifio.enumeration.CodedEnum ce) {
    CodedEnum ret = scifioToCommon.get(ce);
    
    if (ret == null) {
      ret = new SCIFIOWrapper(ce);
      commonToScifio.put(ret, ce);
      scifioToCommon.put(ce, ret);
    }
    
    return ret;
  }
  
  /* See LegacyAdapter#clear() */
  public void clear() {
    this.commonToScifio.clear();
    this.scifioToCommon.clear();
  }
  
  // -- Delegation Classes --

  public static class LegacyWrapper implements ome.scifio.enumeration.CodedEnum {
    
    // -- Fields --
    
    private CodedEnum ce;
    
    // -- constructor --
    
    public LegacyWrapper(CodedEnum ce) {
      this.ce = ce;
    }
    
    // -- CodedEnum API --
    
    public int getCode() {
      return ce.getCode();
    }
    
    // -- Object delegators --

    @Override
    public boolean equals(Object obj) {
      return ce.equals(obj);
    }
    
    @Override
    public int hashCode() {
      return ce.hashCode();
    }
    
    @Override
    public String toString() {
      return ce.toString();
    }
  }
  
  public static class SCIFIOWrapper implements CodedEnum {
    
    // -- Fields --

    private ome.scifio.enumeration.CodedEnum ce;

    // -- Constructor --

    public SCIFIOWrapper(ome.scifio.enumeration.CodedEnum ce) {
      this.ce = ce;
    }
    
    // -- CodedEnum API --

    public int getCode() {
      return ce.getCode();
    }
    
    // -- Object delegators --

    @Override
    public boolean equals(Object obj) {
      return ce.equals(obj);
    }
    
    @Override
    public int hashCode() {
      return ce.hashCode();
    }
    
    @Override
    public String toString() {
      return ce.toString();
    }
  }
}
