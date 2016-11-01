package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.utility.DataTrackQuery;
import hci.gnomex.utility.PropertyDictionaryHelper;

import java.io.Serializable;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.apache.log4j.Logger;
import org.apache.log4j.Logger;
public class GetDataTrackList extends GNomExCommand implements Serializable {
  
  private static Logger 	LOG = Logger.getLogger(GetDataTrackList.class);
  
  private static final int     				MAX_DATATRACK_COUNT_DEFAULT = 200; 
  
  private DataTrackQuery 					dataTrackQuery;
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    dataTrackQuery = new DataTrackQuery(request);
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      
   
      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());
      
      Document doc = dataTrackQuery.getDataTrackDocument(sess, this.getSecAdvisor(), getMaxDataTracks(sess));
      
      org.jdom.output.XMLOutputter out = new org.jdom.output.XMLOutputter();
      this.xmlResult = out.outputString(doc);
    
      setResponsePage(this.SUCCESS_JSP);
    }  catch (Exception e) {
      LOG.error("An exception has occurred in GetDataTrackList ", e);
      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        //closeReadOnlyHibernateSession;        
      } catch(Exception e) {
          LOG.error("An exception has occurred in GetDataTrackList ", e);
      }
    }
    
    return this;
  }
  
  private Integer getMaxDataTracks(Session sess) {
	    Integer maxDataTracks = MAX_DATATRACK_COUNT_DEFAULT;
	    String prop = PropertyDictionaryHelper.getInstance(sess).getProperty(PropertyDictionary.DATATRACK_VIEW_LIMIT);
	    if (prop != null && prop.length() > 0) {
	      try {
	    	  maxDataTracks = Integer.parseInt(prop);
	      }
	      catch(NumberFormatException e) {
              LOG.error("An exception has occurred in GetDataTrackList ", e);
	      }    
	    }
	    return maxDataTracks;
  }
}