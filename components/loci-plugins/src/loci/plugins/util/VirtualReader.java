//
// VirtualReader.java
//

/*
LOCI Plugins for ImageJ: a collection of ImageJ plugins including the
Bio-Formats Importer, Bio-Formats Exporter, Bio-Formats Macro Extensions,
Data Browser and Stack Slicer. Copyright (C) 2005-@year@ Melissa Linkert,
Curtis Rueden and Christopher Peterson.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.plugins.util;

import java.io.IOException;

import loci.formats.IFormatReader;
import loci.formats.ReaderWrapper;

/**
 * TODO
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/loci-plugins/src/loci/plugins/util/VirtualReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/loci-plugins/src/loci/plugins/util/VirtualReader.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 */
public class VirtualReader extends ReaderWrapper {

  // -- Fields --

  private int refCount;

  // -- Constructor --

  public VirtualReader(IFormatReader r) {
    super(r);
    refCount = 0;
  }

  // -- VirtualReader API methods --

  public void setRefCount(int refCount) {
    this.refCount = refCount;
  }

  // -- IFormatReader API methods --

  public void close() throws IOException {
    if (refCount > 0) refCount--;
    if (refCount == 0) super.close();
  }

}
