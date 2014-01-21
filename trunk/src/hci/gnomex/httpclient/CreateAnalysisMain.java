/**
 * 
 */
package hci.gnomex.httpclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


/**
 * @author Tony Di Sera
 * This is a java main that will be called by a batch script to autmotically
 * create a GNomEx analysis entry in the db from the bioinformatics
 * automated analysis pipeline.
 *
 */
public class CreateAnalysisMain extends HttpClientBase {
  
  private String lab;
  private String organism;
  private String genomeBuild;
  private String analysisType;
  private String  name;
  private String description;
  private String folderName;
  private String folderDescription;
  private List<String> seqLaneNumbers = new ArrayList<String>();
  private String lanesXMLString = null;

  /**
   * @param args
   */
  public static void main(String[] args) {

    CreateAnalysisMain createAnalysis = new CreateAnalysisMain(args);
    createAnalysis.callServlet();
  }
  
  private CreateAnalysisMain(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-h")) {
        printUsage();
        return;
      } else if (args[i].equals("-debug")) {
        debug = true;
      }  else if (args[i].equals("-properties")) {
        propertiesFileName = args[++i];
      } else if (args[i].equals("-server")) {
        server = args[++i];
      } else if (args[i].equals("-serverURL")) {
        serverURL = args[++i];
      } else if (args[i].equals("-lab")) {
        lab = args[++i];
      } else if (args[i].equals("-genomeBuild")) {
        genomeBuild = args[++i];
      } else if (args[i].equals("-organism")) {
        organism = args[++i];
      } else if (args[i].equals("-analysisType")) {
        analysisType = args[++i];
      } else if (args[i].equals("-name")) {
        name = args[++i];
      } else if (args[i].equals("-description")) {
        description = args[++i];
      } else if (args[i].equals("-folderName")) {
        folderName = args[++i];
      } else if (args[i].equals("-folderDescription")) {
        folderDescription = args[++i];
      } else if (args[i].equals("-seqLane")) {
        String seqLaneNumber = args[++i];
        seqLaneNumbers.add(seqLaneNumber);
      } 
    }
    
    if (!seqLaneNumbers.isEmpty()) {
        lanesXMLString = "<lanes>";
        for (Iterator iter = seqLaneNumbers.iterator(); iter.hasNext();) {
          String seqLaneNumber = (String)iter.next();
          lanesXMLString += "<SequenceLane number=\"" + seqLaneNumber + "\"/>";                  
        }
        lanesXMLString += "</lanes>";
    }  
  }
  
  protected void printUsage() {
    System.out.println("java hci.gnomex.utility.CreateAnalysisMain " + "\n" +
        "[-debug] " + "\n" +
        "-properties <propertiesFileName> " + "\n" +
        "-server <serverName>" + "\n" +
        "-name <analysisName>" + "\n" +
        "-lab <lab name>" + "\n" +
        "-folderName <name of folder>" + "\n" + 
        "-organism <organism           example: Human,E. coli, etc.>" +  "\n" +
        "-genomeBuild <genome build    example: hg18, hg19, TAIR8, etc.>" + "\n" +
        "-analysisType <analysis type  example: Alignment,SNP/INDEL,ChIP-Seq analysis,etc..>" +  "\n" +
        "[-description <analysisDescription>]" + "\n" +
        "[-folderDescription <description of folder>]" + "\n" +
        "[-seqLane <sequence lane number example: 8432F1_1> [...]]");
  }
  
  protected boolean checkParms() {
    if (lab == null || name == null || name.equals("") || organism == null || genomeBuild == null || analysisType == null || folderName == null || folderName.equals("")) {
      return false;
    } else {
      return true;
    }
  }
  
  protected String getParms() throws UnsupportedEncodingException {
    String parms = URLEncoder.encode("labName", "UTF-8") + "=" + URLEncoder.encode(lab, "UTF-8");
    parms += "&" + URLEncoder.encode("name", "UTF-8") + "=" + URLEncoder.encode(name, "UTF-8");
    parms += "&" + URLEncoder.encode("newAnalysisGroupName", "UTF-8") + "=" + URLEncoder.encode(folderName, "UTF-8");
    parms += "&" + URLEncoder.encode("organism", "UTF-8") + "=" + URLEncoder.encode(organism, "UTF-8");
    parms += "&" + URLEncoder.encode("genomeBuild", "UTF-8") + "=" + URLEncoder.encode(genomeBuild, "UTF-8");
    parms += "&" + URLEncoder.encode("analysisType", "UTF-8") + "=" + URLEncoder.encode(analysisType, "UTF-8");
    parms += "&" + URLEncoder.encode("isBatchMode", "UTF-8") + "=" + URLEncoder.encode("Y", "UTF-8");
    if (description != null) {
      parms += "&" + URLEncoder.encode("description", "UTF-8") + "=" + URLEncoder.encode(description, "UTF-8");        
    }
    if (folderDescription != null) {
      parms += "&" + URLEncoder.encode("newAnalysisGroupDescription", "UTF-8") + "=" + URLEncoder.encode(folderDescription, "UTF-8");        
    }
    if (lanesXMLString != null) {
      parms += "&" + "lanesXMLString" + "=" + lanesXMLString;        
    } 

    return parms;
  }
  
  protected String getServletName() {
    return "CreateAnalysisServlet";
  }

}
