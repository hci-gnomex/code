package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.Chromatogram;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.HibernateSession;

import java.io.File;
import java.io.Serializable;
import java.io.StringReader;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.apache.log4j.Logger;

public class DeleteChromatograms extends GNomExCommand implements Serializable {
  
  // the static field for logging in Log4J
  private static Logger LOG = Logger.getLogger(DeleteChromatograms.class);
  
  private String chromatsToDeleteXMLString;
  private Document chromatsToDeleteDoc;
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    
    if (request.getParameter("chromatsToDeleteXMLString") != null && !request.getParameter("chromatsToDeleteXMLString").equals("")) {
      chromatsToDeleteXMLString = request.getParameter("chromatsToDeleteXMLString");
      StringReader reader = new StringReader(chromatsToDeleteXMLString);
      try {
        SAXBuilder sax = new SAXBuilder();
        chromatsToDeleteDoc = sax.build(reader);     
      } catch (Exception je ) {
        LOG.error( "Cannot parse chromatsToDeleteXMLString", je );
        this.addInvalidField( "chromatsToDeleteXMLString", "Invalid chromatsToDeleteXMLString");
      }
    } 

   
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      Session sess = HibernateSession.currentSession(this.getUsername());

      //Change this conditional to check for this.getSecurityAdvisor().hasPermission(SecurityAdvisor.canDelete())
      if (this.getSecurityAdvisor().hasPermission(SecurityAdvisor.CAN_MANAGE_DNA_SEQ_CORE)) {
        for(Iterator i = this.chromatsToDeleteDoc.getRootElement().getChildren().iterator(); i.hasNext();) {
          Element node = (Element)i.next();
          Integer idChromatogram = Integer.parseInt(node.getAttributeValue("idChromatogram"));
          Chromatogram ch = (Chromatogram) sess.load(Chromatogram.class, idChromatogram);

          File chromatFile = new File(ch.getQualifiedFilePath() + Constants.FILE_SEPARATOR + ch.getFileName());
          chromatFile.delete();

          sess.delete(ch);
        }       
        sess.flush();
        setResponsePage(this.SUCCESS_JSP);
 
      } else {
        this.addInvalidField("Insufficient permissions", "Insufficient permission to edit dictionaries.");
        setResponsePage(this.ERROR_JSP);
      }
      
      
    }catch (Exception e){
      LOG.error("An exception has occurred in DeleteChromatograms ", e);

      throw new RollBackCommandException(e.getMessage());
        
    }finally {
      try {
        //closeHibernateSession;        
      } catch(Exception e) {
        LOG.error("An exception has occurred in DeleteChromatograms ", e);
      }
    }
    
    return this;
  }
}