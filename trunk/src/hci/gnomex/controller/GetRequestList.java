package hci.gnomex.controller;

import hci.gnomex.utility.DictionaryHelper;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.model.DetailObject;
import hci.framework.utilities.XMLReflectException;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Element;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;

import hci.gnomex.model.RequestCategory;
import hci.gnomex.model.RequestFilter;
import hci.gnomex.model.Request;


public class GetRequestList extends GNomExCommand implements Serializable {
  
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GetRequestList.class);
  
  private RequestFilter requestFilter;
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    requestFilter = new RequestFilter();
    HashMap errors = this.loadDetailObject(request, requestFilter);
    this.addInvalidFields(errors);
  }

  public Command execute() throws RollBackCommandException {
    
    try {
    Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());
    DictionaryHelper dh = DictionaryHelper.getInstance(sess);

    StringBuffer buf = requestFilter.getQuery(this.getSecAdvisor());
    
    log.info("Query for GetRequestList: " + buf.toString());
    List reqs = sess.createQuery(buf.toString()).list();
    
    Document doc = new Document(new Element("RequestList"));
    for(Iterator i = reqs.iterator(); i.hasNext();) {
      Request req = (Request)i.next();
      
      Element node = req.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
      
      RequestCategory requestCategory = dh.getRequestCategoryObject(req.getCodeRequestCategory());
      String requestStatus = dh.getRequestStatus(req.getCodeRequestStatus());
      
      node.setAttribute("icon", requestCategory != null && requestCategory.getIcon() != null ? requestCategory.getIcon() : "");
      
      StringBuffer queryBuf = new StringBuffer();
      queryBuf.append("select plate.label, run.idInstrumentRun, run.label ");
      queryBuf.append("from Request as req ");
      queryBuf.append("left Join req.samples as samps ");
      queryBuf.append("left Join samps.wells as pws ");
      queryBuf.append("left Join pws.plate as plate ");
      queryBuf.append("left Join plate.instrumentRun as run ");
      queryBuf.append("where req.idRequest = " + req.getIdRequest() + " ");
      queryBuf.append("AND plate.codePlateType = 'REACTION' ");
      List plateRows = sess.createQuery(queryBuf.toString()).list();
      
      if (plateRows != null && plateRows.size() > 0) {
        Object[] plateRow = (Object[])plateRows.get(0);
        String plateLabel        = (String)plateRow[0];
        Integer idInstrumentRun  = (Integer)plateRow[1];
        String runLabel          = (String)plateRow[2];
        
        node.setAttribute("plateLabel", plateLabel != null ? plateLabel : "");
        node.setAttribute("idInstrumentRun", idInstrumentRun != null ? idInstrumentRun.toString() : "");
        node.setAttribute("runLabel", runLabel != null ? runLabel : "");        
      }
      
      doc.getRootElement().addContent(node);
      
    }
    
    XMLOutputter out = new org.jdom.output.XMLOutputter();
    this.xmlResult = out.outputString(doc);
    
    setResponsePage(this.SUCCESS_JSP);
    }catch (NamingException e){
      log.error("An exception has occurred in GetRequestList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    }catch (SQLException e) {
      log.error("An exception has occurred in GetRequestList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (XMLReflectException e){
      log.error("An exception has occurred in GetRequestList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (Exception e){
      log.error("An exception has occurred in GetRequestList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        this.getSecAdvisor().closeReadOnlyHibernateSession();        
      } catch(Exception e) {
        
      }
    }
    
    return this;
  }

}