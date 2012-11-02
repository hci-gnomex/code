package hci.gnomex.useq;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import hci.gnomex.useq.apps.*;

/**Class to convert xxx.useq archives to UCSC xxx.bb or xxx.bw archives.*/
public class USeq2UCSCBig extends Thread{

  private File[] useqArchives;
  private File workingUSeqArchiveFile;
  private USeqArchive workingUSeqArchive;
  private File ucscWig2BigWig;
  private File ucscBed2BigBed;
  private boolean verbose = false;
  private int lengthExtender = 10000;
  private File chromLengths;
  private File convertedFile;
  private File tempFile;
  private File tempFileSorted;

  //constructors
  //stand alone
  public USeq2UCSCBig (String[] args){
    try {
      verbose = true;
      processArgs(args);

      //for each archive
      for (File u : useqArchives){
        workingUSeqArchiveFile = u;
        if (verbose) System.out.println("Processing: "+workingUSeqArchiveFile.getName());

        convert();

      }
      if (verbose) System.out.println("\nDone!\n");
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  //for GenoPub integration using threads, create the Object, call the fetchConvertedFileNames(), then start the thread
  public USeq2UCSCBig (File ucscWig2BigWig, File ucscBed2BigBed, File useq){
    this.ucscWig2BigWig = ucscWig2BigWig;
    this.ucscBed2BigBed = ucscBed2BigBed;
    workingUSeqArchiveFile = useq;
  }

  //methods
  /**Returns converted File(s) or null if something bad happened.*/
  public ArrayList<File> convert () throws Exception{
  
    try {
      //create archive
      workingUSeqArchive = new USeqArchive(workingUSeqArchiveFile);

      //write out chrom name: max lengths
      writeChromLengths();

      //convert graph data
      if (workingUSeqArchive.getArchiveInfo().isGraphData()) return convertGraphData();

      //convert region data
      else return convertRegionData();

    } catch (Exception e){
      e.printStackTrace();
    }
    return null;

  }

  public void run(){
    try {
      convert();
    } catch (Exception e) {
      System.err.println("\nERROR: failed to convert useq file to big file!\n");
      e.printStackTrace();
    }
  }

  /**Fetches File objects for the converted files if they were converted.  Does not convert.*/
  public ArrayList<File> fetchConvertedFileNames() throws Exception{
    //create archive
    workingUSeqArchive = new USeqArchive(workingUSeqArchiveFile);
    
    //convert graph data
    if (workingUSeqArchive.getArchiveInfo().isGraphData()) return fetchConvertedGraphNames();

    //convert region data
    else return fetchConvertRegionNames();
  }

  private ArrayList<File> fetchConvertRegionNames(){
    String name = workingUSeqArchiveFile.getName().replace(USeqUtilities.USEQ_EXTENSION_WITH_PERIOD, "");
    File convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + ".bb");
    ArrayList<File> al = new ArrayList<File>();
    al.add(convertedFile);
    return al;
  }

  private ArrayList<File> convertRegionData() throws Exception{
    String name = workingUSeqArchiveFile.getName().replace(USeqUtilities.USEQ_EXTENSION_WITH_PERIOD, "");
    tempFile = new File (workingUSeqArchiveFile.getCanonicalPath() + ".bed");
    tempFile.deleteOnExit();

    USeq2Text.print2TextFile(workingUSeqArchiveFile, tempFile, true, true);

    //sort it using unix command
    tempFileSorted = new File (workingUSeqArchiveFile.getCanonicalPath() + ".sorted.bed");
    tempFileSorted.deleteOnExit();
    String shell = "sort -k1,1 -k2,2n "+tempFile.getCanonicalPath()+" > "+tempFileSorted.getCanonicalPath();
    USeqUtilities.executeShellScript(shell, workingUSeqArchiveFile.getParentFile());

    //convert to binary
    convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + ".bb");
    String[] command = new String[]{
        ucscBed2BigBed.getCanonicalPath(),
        tempFileSorted.getCanonicalPath(),
        chromLengths.getCanonicalPath(),
        convertedFile.getCanonicalPath()
    };
    executeUCSCCommand(command);
    deleteTempFiles();
    //return
    ArrayList<File> al = new ArrayList<File>();
    al.add(convertedFile);
    return al;
  }

  private ArrayList<File> fetchConvertedGraphNames(){
    String name = workingUSeqArchiveFile.getName().replace(USeqUtilities.USEQ_EXTENSION_WITH_PERIOD, "");
    ArrayList<File> convertedFiles = new ArrayList<File>();
    //is it stranded
    boolean stranded = workingUSeqArchive.isStranded();
    if (stranded){
      File convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + "_Plus.bw");
      convertedFiles.add(convertedFile);
      convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + "_Minus.bw");
      convertedFiles.add(convertedFile);
    }
    else {
      File convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + ".bw");
      convertedFiles.add(convertedFile);
    }
    return convertedFiles;
  }


  private ArrayList<File> convertGraphData() throws Exception{
    USeq2Text useq2Text = new USeq2Text();
    useq2Text.setPrintWigFormat(true);
    String name = workingUSeqArchiveFile.getName().replace(USeqUtilities.USEQ_EXTENSION_WITH_PERIOD, "");
    ArrayList<File> convertedFiles = new ArrayList<File>();
    tempFile = new File (workingUSeqArchiveFile.getCanonicalPath() + ".wig");
    tempFile.deleteOnExit();

    //is it stranded
    boolean stranded = workingUSeqArchive.isStranded();
    if (stranded){
      //convert for plus
      useq2Text.print2WigFile(workingUSeqArchiveFile, tempFile, "+");
      //convert text to binary, wigToBigWig in.wig chrom.sizes out.bw
      convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + "_Plus.bw");
      String[] command = new String[]{
          ucscWig2BigWig.getCanonicalPath(),
          tempFile.getCanonicalPath(),
          chromLengths.getCanonicalPath(),
          convertedFile.getCanonicalPath()
      };
      //execute it
      executeUCSCCommand(command);
      convertedFiles.add(convertedFile);

      //convert for minus
      useq2Text.print2WigFile(workingUSeqArchiveFile, tempFile, "-");
      //convert text to binary, wigToBigWig in.wig chrom.sizes out.bw
      convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + "_Minus.bw");
      command[3] = convertedFile.getCanonicalPath();
      //execute and save it
      executeUCSCCommand(command);
      convertedFiles.add(convertedFile);

    }
    else {
      //convert all
      useq2Text.print2WigFile(workingUSeqArchiveFile, tempFile, null);
      //convert text to binary, wigToBigWig in.wig chrom.sizes out.bw
      convertedFile = new File (workingUSeqArchiveFile.getParentFile(), name + ".bw");
      String[] command = new String[]{
          ucscWig2BigWig.getCanonicalPath(),
          tempFile.getCanonicalPath(),
          chromLengths.getCanonicalPath(),
          convertedFile.getCanonicalPath()
      };
      //execute it
      executeUCSCCommand(command);

      convertedFiles.add(convertedFile);
    }
    //cleanup
    deleteTempFiles();

    return convertedFiles;
  }

  private void executeUCSCCommand(String[] command) throws Exception{
    if (verbose) {
      System.out.println("\nUnix Command:");
      for (String c : command) System.out.println(c);
      System.out.println();
    }
    //execute ucsc converter, nothing should come back for wigToBigWig and sort
    String[] results = USeqUtilities.executeCommandLineReturnAll(command);
    if (results.length !=0){
      //scan to see if just bedToBigBed normal output
      boolean ok = true;
      StringBuilder sb = new StringBuilder("Error message:");
      for (String c : results) {
        sb.append("\n");
        sb.append(c);
        if (c.contains("millis") == false) ok = false;
      }
      if (ok != true) {
        deleteAllFiles();
        throw new Exception (sb.toString());
      }
    }
  }

  private void writeChromLengths() throws IOException{
    HashMap<String,Integer> nameBase = workingUSeqArchive.fetchChromosomesAndLastBase();
    chromLengths = new File (workingUSeqArchive.getZipFile()+".chromLengths");
    chromLengths.deleteOnExit();
    PrintWriter out = new PrintWriter( new FileWriter (chromLengths));
    for (String name: nameBase.keySet()){
      int length = nameBase.get(name)+ lengthExtender;
      out.print(name);
      out.print("\t");
      out.println(length);
    }
    out.close();
  }

  public void deleteAllFiles(){
    deleteTempFiles();
    if (convertedFile != null) convertedFile.delete();
  }
  public void deleteTempFiles(){
    if (tempFile!= null) tempFile.delete();
    if (chromLengths!= null) chromLengths.delete();
    if (tempFileSorted!= null) tempFileSorted.delete();
  }

  public static void main(String[] args) {
    if (args.length ==0){
      printDocs();
      System.exit(0);
    }
    new USeq2UCSCBig(args);
  }

  /**This method will process each argument and assign new variables*/
  public void processArgs(String[] args){
    Pattern pat = Pattern.compile("-[a-z]");
    if (verbose) System.out.println("\nArguments: "+USeqUtilities.stringArrayToString(args, " ")+"\n");
    File ucscDir = null;
    for (int i = 0; i<args.length; i++){
      String lcArg = args[i].toLowerCase();
      Matcher mat = pat.matcher(lcArg);
      if (mat.matches()){
        char test = args[i].charAt(1);
        try{
          switch (test){
          case 'u': useqArchives = USeqUtilities.fetchFilesRecursively(new File(args[++i]), USeqUtilities.USEQ_EXTENSION_WITH_PERIOD); break;
          case 'd': ucscDir = new File (args[++i]); break;
          case 'h': printDocs(); System.exit(0); break;
          default: USeqUtilities.printExit("\nProblem, unknown option! " + mat.group());
          }
        }
        catch (Exception e){
          USeqUtilities.printExit("\nSorry, something doesn't look right with this parameter: -"+test+"\n");
        }
      }
    }
    //make files
    if (ucscDir == null || ucscDir.isDirectory() == false) USeqUtilities.printExit("\nCannot find your directory containing the UCSC wig2BigWig and bed2BigBed apps -> "+ucscDir);
    ucscWig2BigWig = new File( ucscDir, "wigToBigWig");
    ucscBed2BigBed = new File( ucscDir, "bedToBigBed");

    //check files
    if (useqArchives == null || useqArchives.length == 0) USeqUtilities.printExit("\nCannot find any xxx."+USeqUtilities.USEQ_EXTENSION_NO_PERIOD+" USeq archives?\n");
    if (ucscWig2BigWig.canExecute() == false) USeqUtilities.printExit("\nCannot find or execute -> "+ucscWig2BigWig+"\n");
    if (ucscBed2BigBed.canExecute() == false) USeqUtilities.printExit("\nCannot find or execute -> "+ucscBed2BigBed+"\n");

  } 


  public static void printDocs(){
    System.out.println("\n" +
        "**************************************************************************************\n" +
        "**                              USeq 2 UCSC Big: Aug 2011                           **\n" +
        "**************************************************************************************\n" +
        "Converts USeq archives to UCSC bigWig (xxx.bw) or bigBed (xxx.bb) archives based on\n" +
        "the data type. WARNING: bigBed format conversion will clip any associated scores to\n" +
        "between 0-1000. \n" +

        "\nOptions:\n"+
        "-u Full path file/directory containing xxx.useq files. Recurses through sub \n" +
        "       if a directory is given.\n" +
        "-d Full path directory containing the UCSC wigToBigWig and bedToBigBed apps, download\n" +
        "       from http://hgdownload.cse.ucsc.edu/admin/exe/\n"+

        "\nExample: java -Xmx4G -jar pathTo/USeq/Apps/USeq2UCSCBig -u\n" +
        "      /AnalysisResults/USeqDataArchives/ -d /Apps/UCSC/\n\n" +

    "**************************************************************************************\n");

  }
}

