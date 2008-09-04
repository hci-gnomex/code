package hci.gnomex.controller;

import hci.gnomex.constants.Constants;
import hci.gnomex.model.Request;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.HibernateSession;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Session;


public class DownloadResultsServlet extends HttpServlet { 
  
  
  private String    keysString = null;
  private String    includeTIF = "N";
  private String    includeJPG = "N";

  private String    baseDir;
  
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(DownloadResultsServlet.class);
  
  public void init() {
  
  }
    
  protected void doGet(HttpServletRequest req, HttpServletResponse response)
      throws ServletException, IOException {
    
    // restrict commands to local host if request is not secure
    if (!req.isSecure()) {
      if (req.getRemoteAddr().equals(InetAddress.getLocalHost().getHostAddress())
          || req.getRemoteAddr().equals("127.0.0.1")) {
        log.debug("Requested from local host");
      }
      else {
        log.error("Accessing secure command over non-secure line from remote host is not allowed");
        
        response.setContentType("text/html");
        response.getOutputStream().println(
            "<html><head><title>Error</title></head>");
        response.getOutputStream().println("<body><b>");
        response.getOutputStream().println(
            "Secure connection is required. Prefix your request with 'https: "
                + "<br>");
        response.getOutputStream().println("</body>");
        response.getOutputStream().println("</html>");
        return;
      }
    }

    // Get input parameters
    keysString = req.getParameter("resultKeys");
    if (req.getParameter("includeTIF") != null
        && !req.getParameter("includeTIF").equals("")) {
      includeTIF = req.getParameter("includeTIF");
    }
    if (req.getParameter("includeJPG") != null
        && !req.getParameter("includeJPG").equals("")) {
      includeJPG = req.getParameter("includeJPG");
    }
    baseDir = Constants.getMicroarrayDirectoryForReading(req.getServerName());



    try {

      // Get security advisor
      SecurityAdvisor secAdvisor = (SecurityAdvisor) req.getSession().getAttribute(SecurityAdvisor.SECURITY_ADVISOR_SESSION_KEY);

      if (secAdvisor != null) {
        response.setContentType("application/x-download");
        response.setHeader("Content-Disposition", "attachment;filename=microarray.zip");
        response.setHeader("Cache-Control", "max-age=0, must-revalidate");
        
        long time1 = System.currentTimeMillis();
        
        
        Session sess = secAdvisor.getReadOnlyHibernateSession(req.getUserPrincipal().getName());
        
       
        Map fileNameMap = new HashMap();
        long fileSizeTotal = getFileNamesToDownload(baseDir, keysString, fileNameMap, includeTIF.equals("Y"), includeJPG.equals("Y"));

        // Set content length to estimated zip (compressed) size.
        int estimatedCompressedSize = new Double(fileSizeTotal / 2.5).intValue();

        
        ZipOutputStream zout = new ZipOutputStream(response.getOutputStream());
        byte b[] = new byte[102400];

        
        int totalZipSize = 0;
        // For each request
        for(Iterator i = fileNameMap.keySet().iterator(); i.hasNext();) {
          String requestNumber = (String)i.next();
          
          Request request = findRequest(sess, requestNumber);
          
          // If we can't find the request in the database, just bypass it.
          if (request == null) {
            log.error("Unable to find request " + requestNumber + ".  Bypassing download for user " + req.getUserPrincipal().getName() + ".");
            continue;
          }
          
          // Check permissions - bypass this request if the user 
          // does not have  permission to read it.
          if (!secAdvisor.canRead(request)) {  
            log.error("Insufficient permissions to read request " + requestNumber + ".  Bypassing download for user " + req.getUserPrincipal().getName() + ".");
            continue;
          }
          
          List fileNames = (List)fileNameMap.get(requestNumber);
          
          // For each file to be downloaded for the request
          for (Iterator i1 = fileNames.iterator(); i1.hasNext();) {

            String filename = (String) i1.next();

            
            FileInputStream in = new FileInputStream(filename);

            // Add ZIP entry to output stream.
            // (The file name starts after the year subdirectory)
            ZipEntry zipEntry = new ZipEntry(filename.substring(Constants.getMicroarrayDirectoryNameLength() + 5));
            zout.putNextEntry(zipEntry);

            // Transfer bytes from the file to the ZIP file
            int numRead = 0;
            int size = 0;

            
            while (numRead != -1) {
              numRead = in.read(b);
              if (numRead != -1) {
                zout.write(b, 0, numRead);
                size += numRead;
              }
            }

            zout.closeEntry();
            totalZipSize += zipEntry.getCompressedSize();
          }     
          
          
        }
        
        response.setContentLength(totalZipSize);
        
        zout.finish();
        zout.flush();
        

        
        long time3 = System.currentTimeMillis();

        secAdvisor.closeReadOnlyHibernateSession();


      } else {
        response.setContentType("text/html");
        response.getOutputStream().println(
            "<html><head><title>Error</title></head>");
        response.getOutputStream().println("<body><b>");
        response.getOutputStream().println(
            "You must have a SecurityAdvisor in order to run this command "
                + "<br>");
        response.getOutputStream().println("</body>");
        response.getOutputStream().println("</html>");
      }
    } catch (Exception e) {
      response.setContentType("text/html");
      response.getOutputStream().println(
          "<html><head><title>Error</title></head>");
      response.getOutputStream().println("<body><b>");
      response.getOutputStream().println(
          "An error has occured while processing " + "<br>");
      response.getOutputStream().println(
          "Here is the exception: <br>" + e + "<br>");
      e.printStackTrace(new PrintWriter(response.getOutputStream()));
      response.getOutputStream().println("</body>");
      response.getOutputStream().println("</html>");
      e.printStackTrace();
    }

  }    
  
  
  public static Request findRequest(Session sess, String requestNumber) {
    Request request = null;
    List requests = sess.createQuery("SELECT req from Request req where req.number = '" + requestNumber + "'").list();
    if (requests.size() == 1) {
      request = (Request)requests.get(0);
    }
    return request;    
  }
    
  public static long getFileNamesToDownload(String baseDir, String keysString, Map fileNameMap, boolean includeAllTIFFiles, boolean includeAllJPGFiles) {

    long fileSizeTotal = 0;
    String[] keys = keysString.split(":");
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];

      String tokens[] = key.split("-");
      String createYear = tokens[0];
      String createDate = tokens[1];
      String requestNumber = tokens[2];
      String resultDirectory = tokens[3];

      String directoryName = baseDir + createYear + "/" + requestNumber + "/" + resultDirectory;      
      fileSizeTotal += getFileNames(requestNumber, directoryName, fileNameMap, includeAllTIFFiles, includeAllJPGFiles);
    }
    return fileSizeTotal;
  }      
      
     
    
  public static long getFileNames(String requestNumber, String directoryName, Map fileNameMap, boolean includeAllTIFFiles, boolean includeAllJPGFiles) {
    File fd = new File(directoryName);
    
    long fileSizeTotal = 0;

    if (fd.isDirectory()) {
      String[] fileList = fd.list();
      for (int x = 0; x < fileList.length; x++) {
        String fileName = directoryName + "/" + fileList[x];
        File f1 = new File(fileName);
        if (f1.isDirectory()) {
          fileSizeTotal += getFileNames(requestNumber, fileName, fileNameMap, includeAllTIFFiles, includeAllJPGFiles);
        } else {
          boolean include = true;
          if (!includeAllJPGFiles && fileName.toLowerCase().endsWith(".jpg")) {
            include = false;
          } else if (!includeAllTIFFiles && fileName.toLowerCase().endsWith(".tif")) {
            include = false;
          }
          if (include) {
            fileSizeTotal += f1.length();
            
            
            List fileNames = (List)fileNameMap.get(requestNumber);
            if (fileNames == null) {
              fileNames = new ArrayList();
              fileNameMap.put(requestNumber, fileNames);
            }
            fileNames.add(fileName);
          }
        }
      }
    }
    return fileSizeTotal;
  }
}