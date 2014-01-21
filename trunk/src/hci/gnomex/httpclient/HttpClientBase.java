package hci.gnomex.httpclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public abstract class HttpClientBase {

  protected String userName;
  protected String server;
  protected String serverURL;
  protected boolean debug = false;

  protected String propertiesFileName = "/properties/gnomex_httpclient.properties";
  
  protected void loadProperties() throws FileNotFoundException, IOException {
    File file = new File(propertiesFileName);
    FileInputStream fis = new FileInputStream(file);
    Properties p = new Properties();
    p.load(fis);
    userName = (String)p.get("userName");
  }

  protected void trustCerts() {
    TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {  
      public java.security.cert.X509Certificate[] getAcceptedIssuers() {  
        return null;  
      }  

      public void checkClientTrusted(  
          java.security.cert.X509Certificate[] certs, String authType) {  
      }  

      public void checkServerTrusted(  
        java.security.cert.X509Certificate[] certs, String authType) {  
        if (debug) {
          System.out.println("authType is " + authType);  
          System.out.println("cert issuers");              
        }
        for (int i = 0; i < certs.length; i++) {
          if (debug) {
            System.out.println("\t" + certs[i].getIssuerX500Principal().getName());  
            System.out.println("\t" + certs[i].getIssuerDN().getName());                
          }
        }  
      }  
    } };  

    try {  
      SSLContext sc = SSLContext.getInstance("SSL");  
      sc.init(null, trustAllCerts, new java.security.SecureRandom());  
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());  
    } catch (Exception e) {  
      e.printStackTrace();  
      System.exit(1);  
    }  

  }
  
  protected abstract void printUsage();
  
  protected abstract boolean checkParms();
  protected abstract String getParms() throws UnsupportedEncodingException;
  protected abstract String getServletName();
  
  protected void callServlet() {
    BufferedReader in = null;
    String inputLine;    
    StringBuffer outputXML = new StringBuffer();
    boolean success = false;

    try {
      loadProperties();
      
      // Make sure mandatory arguments were passed in
      if (!checkParms()) {
        this.printUsage();
        throw new Exception("Please specify all mandatory arguments.  See command line usage.");
      }
      
      if (server == null && serverURL == null) {
        this.printUsage();
        throw new Exception("Please specify all mandatory arguments.  See command line usage.");
      }
      
      trustCerts(); 
      
      //
      // Login using forms based authentication
      //
      if (serverURL == null) {
        serverURL = (server.equals("localhost") ? "http://" : "https://") + server;
      }
      
      //
      // Call servlet
      //
      
      // Construct request parameters
      String parms = getParms();
      if (parms.length() > 0) {
        parms += "&";
      }
      parms += URLEncoder.encode("userName", "UTF-8") + "=" + URLEncoder.encode(userName, "UTF-8");
      


      success = false;
      outputXML = new StringBuffer();
      URL url = new URL(serverURL + "/gnomex/" + getServletName() + ".gx");
      URLConnection conn = url.openConnection();
      conn.setDoOutput(true);

      OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
      wr.write(parms);
      wr.flush();

      in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      while ((inputLine = in.readLine()) != null) {
        System.out.print(inputLine);
        if (inputLine.indexOf("<SUCCESS") >= 0) {
          success = true;
        } 
      }
      System.out.println();
      if (!success) {
        throw new Exception("Error calling servlet");
      }

    } catch (MalformedURLException e) {
      e.printStackTrace();
      System.err.println(e.toString());
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println(e.toString());
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println(e.toString());
    } finally {
      if (in != null) {
        try {
          in.close();          
        } catch (IOException e) {
          
        }
      }
    }
  }

}
