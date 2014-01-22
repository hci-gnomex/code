package hci.gnomex.controller;


import hci.gnomex.model.Chromatogram;
import hci.gnomex.model.InstrumentRun;
import hci.gnomex.model.Plate;
import hci.gnomex.model.PlateType;
import hci.gnomex.model.PlateWell;
import hci.gnomex.model.Request;
import hci.gnomex.model.RequestStatus;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.ChromatogramParser;
import hci.gnomex.utility.HibernateSession;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;

import java.io.Serializable;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

public class DeleteInstrumentRuns extends GNomExCommand implements Serializable {
  
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(DeleteInstrumentRuns.class);
  
  private String runsToDeleteXMLString;
  private Document runsToDeleteDoc;
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    
    if (request.getParameter("runsToDeleteXMLString") != null && !request.getParameter("runsToDeleteXMLString").equals("")) {
      runsToDeleteXMLString = request.getParameter("runsToDeleteXMLString");
      StringReader reader = new StringReader(runsToDeleteXMLString);
      try {
        SAXBuilder sax = new SAXBuilder();
        runsToDeleteDoc = sax.build(reader);     
      } catch (JDOMException je ) {
        log.error( "Cannot parse runsToDeleteXMLString", je );
        this.addInvalidField( "runsToDeleteXMLString", "Invalid runsToDeleteXMLString");
      }
    } 

   
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      Session sess = HibernateSession.currentSession(this.getUsername());

    //Change this conditional to check for this.getSecurityAdvisor().hasPermission(SecurityAdvisor.canDelete())
      if (this.getSecurityAdvisor().hasPermission(SecurityAdvisor.CAN_WRITE_DICTIONARIES)) {
        for(Iterator i = this.runsToDeleteDoc.getRootElement().getChildren().iterator(); i.hasNext();) {
          Element node = (Element)i.next();
          Integer idInstrumentRun = Integer.parseInt(node.getText());
          InstrumentRun ir = (InstrumentRun) sess.load(InstrumentRun.class, idInstrumentRun);
          changeStatusDeletePlates(sess, ir, RequestStatus.PROCESSING);
          sess.delete(ir); //delete the instrument run after all associations to it have been removed(ie:plate, plate wells)
          
        }       
        sess.flush();
        setResponsePage(this.SUCCESS_JSP);
 
      } else {
        this.addInvalidField("Insufficient permissions", "Insufficient permission to edit dictionareis.");
        setResponsePage(this.ERROR_JSP);
      }
      
      
    }catch (Exception e){
      log.error("An exception has occurred in DeleteInstrumentRuns ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }finally {
      try {
        HibernateSession.closeSession();        
      } catch(Exception e) {
        
      }
    }
    
    return this;
  }
 
  //This function changes the request Status back to submitted and also deletes the plate and
  // plate wells that are associated with the instrument run (ir)
 private void changeStatusDeletePlates( Session sess, InstrumentRun ir, String status ) {
    
    // Get any requests on that run
    Map requests = new HashMap();
    ChromatogramParser cp = new ChromatogramParser();
    List plates = sess.createQuery( "SELECT p from Plate as p " +
        " where p.idInstrumentRun =" + ir.getIdInstrumentRun() ).list();
    for(Iterator i1 = plates.iterator(); i1.hasNext();) {
      Plate plate = (Plate)i1.next();
      List wells = sess.createQuery("SELECT pw from PlateWell as pw where pw.idPlate=" + plate.getIdPlate()).list();
      for(Iterator i2 = wells.iterator(); i2.hasNext();){
        PlateWell well = (PlateWell)i2.next();
        Chromatogram chroma = null;
        List chromaList =  sess.createQuery("Select c from Chromatogram as c where c.idPlateWell=" + well.getIdPlateWell()).list();
        if(chromaList.size() == 1){
          chroma = (Chromatogram) chromaList.get(0);
        }
        
        if(well.getRedoFlag().equals("Y")){
          cp.requeueSourceWells(well.getIdPlateWell(), sess);
        }

        if (well.getIdRequest() != null && !well.getIdRequest().equals( "" ) && !requests.containsKey( well.getIdRequest() ) ) {
          Request req = (Request) sess.get(Request.class, well.getIdRequest());
          requests.put( req.getIdRequest(), req );
        }
        
        if(chroma != null){
          sess.delete(chroma);
        }
        sess.delete(well);
      }
      sess.delete(plate);
    }
    
    // Change request Status 
    for ( Iterator i = requests.keySet().iterator(); i.hasNext();) {
      int idReq = (Integer) i.next();
      Request req = (Request) sess.get(Request.class, idReq );
      req.setCodeRequestStatus( status );
    }
    sess.flush();
  }
  
  

}