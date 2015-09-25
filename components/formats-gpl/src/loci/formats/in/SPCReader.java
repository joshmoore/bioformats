/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2015 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package loci.formats.in;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import loci.common.Location;

import loci.common.RandomAccessInputStream;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;

import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

/**
 * SPCReader is the file format reader for
 * Becker &amp; Hickl SPC-Image SPC FIFO files.
 *
 * @author Ian i.munro at imperial.ac.uk
 * 
 * This format is documented in the TCSPC Handbook
 * available from http://www.becker-hickl.com/handbookphp.htm
 * See Page 675 FIFO Data Files (SPC-134, SPC-144, SPC-154, SPC-830).
 * Note that this code assumes the presence of pixel clock events and 
 * therefor the MacroTime information is not used.
 */
public class SPCReader extends FormatReader {
  
  // -- Constants --

  // Setup file text field strings.
  public static final String TAC_RANGE = "SP_TAC_R";
  public static final String TAC_GAIN = "SP_TAC_G";
  
  /*
   * No of bits by which the ADC value is shifted.
   * there are 12 bits in the file format so shifting by 8 bits 
   * leaves 4 bits of resolution ie 16 timebins.
   * shifting by 7 bits gives 32 timebins etc.
   */
  private final int adcResShift = 6;

  // -- Fields --
  
  /** List of all files to open */
  private Vector<String> allFiles;
  
  /** Number of time bins in lifetime histogram. */
  protected int nTimebins;

  /** Number of spectral nChannels. */
  protected int nChannels;

  /*
   * Array to hold re-ordered data for all the timeBins in one channel at one 
     real-time point.
   */
  protected byte[] Tstore = null;
  ByteBuffer tstoreb;

  /*
   * Currently stored channel
   */
  protected int storedChannel = -1;

  /*
   * Currently stored real time data-cube
   */
  protected int storedT = -1;

  /*
   * Current position in image
   */
  private int currentPixel;
  private int currentLine;
  private int currentFrame;
  
  
  /*
   * Buffer for reading from files
   */
  protected int bufLength;
  protected byte[] rawBuf;
  private int nBuffers;  // no of buffers read

  /*
   * Image size 
   */
  private int nLines;
  private int nFrames;
  private int nPixels;
  
  /*
   * Flag indicating that a Frame clock has been detected.
   * true until the first line clock in that frame is detected
   */
  private boolean endOfFrameFlag;
  
  /*
   * bits per pixel
   */
  private int bpp;
  
  /*
   * Length in bytes of data in a single timebin.
   */
  private int binSize;
  
  /*
   * Requested channel- photons in other channels are ignored.
   */
  private int channel;
  
  /*
   * Position of each frame clock in the .spc file.
   */
  
  List<Integer> frameClockList;
  
  
 
  // -- Constructor --

  /** Constructs a new SPC reader. */
  public SPCReader() {
    super("SPCImage Data", "spc");
    domains = new String[] {FormatTools.FLIM_DOMAIN};
    suffixSufficient = true;
    hasCompanionFiles = true;
    datasetDescription = "One .spc file and accompanying .set file";
    frameClockList = new ArrayList<Integer>();
  }
  
 
 
  // -- IFormatReader API methods --
  
  
  /* @see loci.formats.IFormatReader#isSingleFile(String) */
  @Override
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  @Override
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  } 

   /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  @Override
  public boolean isThisType(String name, boolean open) {
    Location location = new Location(name);
    if (!location.exists()) {
        return false;
    }
    
    if (!checkSuffix(name, ".spc"))  {
       return false;
    }
    location = location.getAbsoluteFile();
    Location parent = location.getParentFile();
    
    String baseName = name.substring(0, name.lastIndexOf(".spc"));
    LOGGER.info(baseName);
        
    if (new Location(parent, baseName + ".set").exists())  {
      return true;
    }
    
    return false;
    
  }

  
  
   /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  @Override
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);
    LOGGER.info("in getSeriesUsedFiles");
    LOGGER.info(allFiles.get(0));
    LOGGER.info(allFiles.get(1));
    String[] farray = allFiles.toArray(new String[allFiles.size()]);
    for (String farray1 : farray) {
      LOGGER.info("allFiles = " + farray1);
    }
    return farray;
  } 

 
   /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  @Override
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);
    
    Integer sizeT = getSizeT();
    channel = no/sizeT;
   
    no -= channel * sizeT;
            
    Integer T = no/nTimebins;
  
    
    no -= T * nTimebins;
            
    Integer timebin = no;
   
    bpp = 2;
    binSize = nPixels * nLines * bpp;
    
    if (Tstore == null) {
      Tstore = new byte[nPixels * nLines * bpp * nTimebins];
      tstoreb = ByteBuffer.wrap(Tstore); // Wrapper around underlying byte[].
    }
    int noOfBytes;
    
    // if the pre-stored data doesn't match that requested then read from the file
    if (storedT != T || storedChannel != channel)  {
      // skip to start of requested frame
      Integer frameClockPos = frameClockList.get(T);
    
      in.seek(frameClockPos);
  
      Integer frameLength = (int) (frameClockList.get(T + 1) - frameClockList.get(T));
    
      rawBuf = new byte[frameLength];

      noOfBytes = in.read(rawBuf);
   
      currentLine = -1;
      currentFrame = -1;
    
      if (noOfBytes == frameLength) {
        processBuffer(noOfBytes);
        storedT = T ;
        storedChannel = channel;
      }
    }
    
   
    // copy 2D plane  from Tstore  into buf
    Integer iLineSize = nPixels * bpp;
    Integer oLineSize = w * bpp;
    
    // offset to correct timebin yth line and xth pixel
    int input = (binSize * timebin) + (y * iLineSize) + (x * bpp);
    int output = 0;
    for (int row = 0; row < h; row++) {
      System.arraycopy(Tstore, input, buf, output, oLineSize);
      input += iLineSize;
      output += oLineSize;
    } 
    return buf;
  }


  /* @see loci.formats.IFormatReader#close(boolean) */
  @Override
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      Tstore = null;
      storedChannel = -1;
      storedT = -1;
      allFiles = null;
      frameClockList = new ArrayList<Integer>();

    }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  @Override
  protected void initFile(String id) throws FormatException, IOException {
    
    super.initFile(id);
    
    allFiles = new Vector<String>();
    
    // get the working directory
    Location tmpFile = new Location(id).getAbsoluteFile();
    Location workingDir = tmpFile.getParentFile();
    if (workingDir == null) workingDir = new Location(".");
    String workingDirPath = workingDir.getPath();
    if (!workingDirPath.equals("")) workingDirPath += File.separator;
    String[] ls = workingDir.list(true);
    if (!new Location(id).exists()) {
      ls = Location.getIdMap().keySet().toArray(new String[0]);
      workingDirPath = "";
    }
    
    String name = tmpFile.getName();
    allFiles.add(id);
    // generate the name of the matching .set file
    String setName = null;
    int pos = name.lastIndexOf(".");
    if (pos != -1) {
      setName = tmpFile.getName().substring(0, pos) + ".set";
      for (String l : ls) {
        if (l.equalsIgnoreCase(setName)) {
          setName = l;
        }
      }
    }
    
    if (setName == null)  {
      throw new FormatException("Failed to find a matching .set file!");
    }
     
    allFiles.add(workingDirPath + setName);
    in = new RandomAccessInputStream(workingDirPath + setName);
    
    LOGGER.info("Reading info from .set file");
    in.order(true);
  
    in.skip(8);
    
    Integer setuppos = in.readInt();
    
    Short setupcount = in.readShort();
    
    // skip to start of setup information
    in.skip(setuppos - 8);
    
    String setup = in.readString(setupcount);
    
    in.close();
    
    // get the tac range from the setup information
    double tacRange = parseSetup(TAC_RANGE, setup);
    
    // get the tac range from the setup information
    double tacGain = parseSetup(TAC_GAIN, setup);
    
    
    double timeBase;
    if (tacGain != 0.0 && tacRange != 0.0)  {
      timeBase = 4095 * tacRange/(tacGain * 4096);
       // convert from s to ps 
      timeBase *= 1.000e12;
    }
    else  {
      throw new FormatException("Failed to parse setup file!");
    }
    
   
    LOGGER.debug("timeBase = " + ((Double)timeBase).toString());
    
    // Now read .spc file
    in = new RandomAccessInputStream(id);
    in.order(true);

    LOGGER.info("Reading info from .spc file");
     
    currentPixel = 0;
    currentLine = -1;
    currentFrame = -1;
    rawBuf = new byte[bufLength];
    endOfFrameFlag = false;
    nBuffers = 0;
    
    bufLength = 1024;
    rawBuf = new byte[bufLength];
    int noOfBytes;
    nBuffers = 0;
    byte adcL;
    byte adcLM;
    
    while ((noOfBytes = in.read(rawBuf)) != -1) {
        for (int bb = 3; bb < noOfBytes; bb += 4) {    //even nybble adc

          adcL = rawBuf[bb];                // get upper byte containing ADC data
          adcLM = (byte) (adcL & 0xF0);     // mask out upper 4 bits

          // at this point only the various clocks are of interest
          switch (adcLM) { 
            case (byte) 0x90:
              invalidAndMark(bb, false);
              break;

            case (byte) 0xd0:         // Invalid, Mark and MTOV all set.
              invalidAndMark(bb, false);     // Unsure about this-- Not well documented!
              break;

            default:
              break;

          }  // end switch
        } //end for
        nBuffers++;
      }
    
    
    nTimebins = (0xFFF >> adcResShift) + 1;
    LOGGER.debug("nTimebins = " + ((Integer)nTimebins).toString());

    LOGGER.debug("nPixels = " + ((Integer)nPixels).toString());
    LOGGER.debug("nLines = " + ((Integer)nLines).toString());
    nFrames = currentFrame - 1;
    LOGGER.debug("nFrames = " + ((Integer)nFrames).toString());

    nChannels = 2;
    
    addGlobalMeta("time bins", nTimebins);
    addGlobalMeta("nChannels", nChannels);

    addGlobalMeta("time base", timeBase);

    LOGGER.info("Populating metadata");

    CoreMetadata m = core.get(0);

    m.sizeX = nPixels;
    m.sizeY = nLines;
    m.sizeZ = 1;
    m.sizeT =  nTimebins * nFrames;
    m.sizeC = nChannels;
    m.dimensionOrder = "XYZTC";
    m.pixelType = FormatTools.UINT16;
    m.rgb = false;
    m.littleEndian = true;
    m.imageCount = m.sizeZ * m.sizeC * m.sizeT;
    m.indexed = false;
    m.falseColor = false;
    m.metadataComplete = true;
 
    m.moduloT.type = FormatTools.LIFETIME;
    m.moduloT.parentType = FormatTools.SPECTRA;
    m.moduloT.typeDescription = "TCSPC";
    m.moduloT.start = 0;

  
    m.moduloT.step = timeBase / nTimebins;
    m.moduloT.end = m.moduloT.step * (nTimebins - 1);
    m.moduloT.unit = "ps";
    
    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this);
    
    
  }
  
 
   private void processBuffer(int noOfBytes) {

    byte adcL, adcLM, routLM;
    
    for (int bb = 3; bb < noOfBytes; bb += 4) {    //even nybble adc

      adcL = rawBuf[bb];                // get upper byte containing ADC data
      adcLM = (byte) (adcL & 0xF0);     // mask out upper 4 bits

      switch (adcLM) {
        case (byte) 0xA0:     //gap
          break;

        case 0x20:
          LOGGER.debug(" Got GAP but not invalid!!!");
          break;

        case 0x40:		// photon + ovfl
          //startOfLineTime -= timeStep;
          photon(bb);
          break;

        case 0x00:		//photon  
          photon(bb);
          break;

        case (byte) 0x80:        // invalid photon
          break;

        case (byte) 0x90:
          invalidAndMark(bb, true);
          break;
          
        case (byte) 0xd0:         // Invalid, Mark and MTOV all set.
          invalidAndMark(bb, true);     // Unsure about this-- Not well documented!
          break;

        case (byte) 0xC0:  // timer overflow  
          routLM = rawBuf[bb - 3];			
          break;

        default:
          LOGGER.debug("Unrecognised pattern !!!");
          break;

      }   //end switch

    } //end for
  }
  
 
   
   private void invalidAndMark(int blockPtr, boolean initialised) {

    long sum;
    byte routM = (byte) (rawBuf[blockPtr - 2] & 0xf0);

    switch (routM) {

      case 0x10:           // pixel clock

        currentPixel++;
        break;

      case 0x20:          //line clock

        //LOGGER.debug("Line clock");
        if (currentFrame == 0 && currentLine == 1) {
          nPixels = currentPixel;
        }

        if (endOfFrameFlag) {
          currentLine = -1;
          endOfFrameFlag = false;
          currentFrame++;
        }

        currentLine++;
        currentPixel = 0;
        break;

      case 0x40:         // frame clock

        LOGGER.debug("Frame clock!");
        if (initialised == false)  {
          if (currentFrame == 0) {
            nLines = currentLine + 1;
          }
          // Store position of start of word containing frame clock for later use
          Integer position = (blockPtr - 3) + (bufLength * nBuffers);
          frameClockList.add(position);
        }
        endOfFrameFlag = true;
        break;

      case 0x60:			//	fandlClock shouldn't happen
        //AfxMessageBox(_T(" both frame and line clock!"));
        break;

      default:
      //LOGGER.debug( "got unknown mark");
    }

  }
  
  
  private void photon(int blockPtr) {
   
    int adc = rawBuf[blockPtr] & 0x0F;   // 4 MSBs of the ADC 
    int currentChannel = (rawBuf[blockPtr - 2] & 0xF0) >> 4;
    if (currentChannel == channel) {
      if (currentPixel < nPixels && currentLine > -1) {  
        int pix =  bpp *((currentLine * nPixels) + currentPixel);
        
        int adcM = (rawBuf[blockPtr] & 0x0F)  << 8;        // 4 bottom bits are 4 MSBs of 12- bit ADC 
        adcM = adcM | (rawBuf[blockPtr - 1] & 0x0FF);      // get all 12 bits
        int  microTime = 4095 - adcM;
        int currentBin = microTime >> adcResShift;     
        pix += currentBin * binSize;
        Short intensity = tstoreb.getShort(pix);
        intensity++;
        tstoreb.putShort(pix, intensity);
      }
    }
  }
  
  private double parseSetup(String tag, String setup) {
    
    // Fields in setup text consist of a tag, followed by a type (either "I" or "F")
    // folowed by a text value
    // e.g. #SP [SP_TAC_G,I,4]
   
    
    int tagOffset = setup.indexOf(tag);
    String taggedString = setup.substring(tagOffset, tagOffset + 30);
    tagOffset = taggedString.indexOf(",");
    String tagType = taggedString.substring(tagOffset + 1, tagOffset + 2);
    LOGGER.debug("type = " + tagType);
    String valueTxt = taggedString.substring(tagOffset + 3, taggedString.indexOf("]"));
    double value = 0.0;
    if (tagType.matches("I")) {
      value = Integer.valueOf(valueTxt);
    }
    if (tagType.matches("F")) {
      value = Double.valueOf(valueTxt);
    }
    
    return value;
  }
  
  

}
