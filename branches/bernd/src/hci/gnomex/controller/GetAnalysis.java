package hci.gnomex.controller;

import hci.gnomex.utility.AnalysisFileDescriptor;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.HibernateSession;
import hci.gnomex.utility.PropertyDictionaryHelper;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.model.DetailObject;
import hci.framework.security.UnknownPermissionException;
import hci.framework.utilities.XMLReflectException;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.jdom.Element;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;


import hci.gnomex.constants.Constants;
import hci.gnomex.model.Analysis;
import hci.gnomex.model.AnalysisCollaborator;
import hci.gnomex.model.AnalysisFile;
import hci.gnomex.model.AnalysisType;
import hci.gnomex.model.AppUser;
import hci.gnomex.model.DataTrack;
import hci.gnomex.model.DataTrackFile;
import hci.gnomex.model.ExperimentDesign;
import hci.gnomex.model.ExperimentDesignEntry;
import hci.gnomex.model.ExperimentFactor;
import hci.gnomex.model.ExperimentFactorEntry;
import hci.gnomex.model.Organism;
import hci.gnomex.model.Project;
import hci.gnomex.model.Property;
import hci.gnomex.model.PropertyEntry;
import hci.gnomex.model.PropertyEntryValue;
import hci.gnomex.model.PropertyOption;
import hci.gnomex.model.PropertyType;
import hci.gnomex.model.Request;
import hci.gnomex.model.Topic;


public class GetAnalysis extends GNomExCommand implements Serializable {
  
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GetAnalysis.class);
  
  private Integer idAnalysis;
  private String  analysisNumber;
  private String  showUploads = "N";
  private String  serverName;
  private String  baseDir;
  private String  baseDirDataTrack;

  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    if (request.getParameter("idAnalysis") != null) {
      idAnalysis = new Integer(request.getParameter("idAnalysis"));
    }     
    if (request.getParameter("analysisNumber") != null && !request.getParameter("analysisNumber").equals("")) {
      analysisNumber = request.getParameter("analysisNumber");
    } 
    if (request.getParameter("showUploads") != null && !request.getParameter("showUploads").equals("")) {
      showUploads = request.getParameter("showUploads");
    } 
    
    if (idAnalysis == null && analysisNumber == null) {
      this.addInvalidField("idAnalysis or analysisNumber", "Either idAnalysis or analysisNumber must be provided");
    }
    serverName = request.getServerName();
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      
      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());
      
      DictionaryHelper dh = DictionaryHelper.getInstance(sess);
      baseDir = PropertyDictionaryHelper.getInstance(sess).getAnalysisDirectory(serverName);
      baseDirDataTrack = PropertyDictionaryHelper.getInstance(sess).getDataTrackDirectory(serverName);
      
      Analysis a = null;
      if (idAnalysis != null && idAnalysis.intValue() == 0) {
        a = new Analysis();
        a.setIdAnalysis(new Integer(0));
      } else if (idAnalysis != null){
        a = (Analysis)sess.get(Analysis.class, idAnalysis);
        Hibernate.initialize(a.getAnalysisGroups());
        
      }else {
        a = GetAnalysis.getAnalysisFromAnalysisNumber(sess, analysisNumber);
        if(a != null) {
          Hibernate.initialize(a.getAnalysisGroups());
        }
      }
      
      if (a == null) {
        this.addInvalidField("missingAnalysis", "Cannot find analysis idAnalysis=" + idAnalysis + " analysisNumber=" + analysisNumber);
      } else {
        if (!this.getSecAdvisor().canRead(a)) {
          this.addInvalidField("permissionerror", "Insufficient permissions to access this analysis Group.");
        } else {
          this.getSecAdvisor().flagPermissions(a);
          
        }         
      }
      
      if (isValid())  {
        
        // If user can write analysis, show collaborators.
        if (this.getSecAdvisor().canUpdate(a)) {
          Hibernate.initialize(a.getCollaborators());
        } else {
          a.excludeMethodFromXML("getCollaborators");
        }
        
        Hibernate.initialize(a.getTopics());
        
        if(a.getTopics() != null) {
          Iterator<?> it = a.getTopics().iterator();
          while(it.hasNext()) {
            Topic t = (Topic) it.next();
            t.excludeMethodFromXML("getTopics");
            t.excludeMethodFromXML("getRequests");
            t.excludeMethodFromXML("getAnalyses");
            t.excludeMethodFromXML("getDataTracks");
            t.excludeMethodFromXML("getAppUser");
            t.excludeMethodFromXML("getLab");
          }
          
        }
        
        Document doc = new Document(new Element("OpenAnalysisList"));
        Element aNode = a.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
        
        
        
        // Hash the know analysis files
        Map knownAnalysisFileMap = new HashMap();
        for(Iterator i = a.getFiles().iterator(); i.hasNext();) {
          AnalysisFile af = (AnalysisFile)i.next();
          knownAnalysisFileMap.put(af.getQualifiedFileName(), af);
        }

        
        // Now add in the files from the upload staging area
        Element filesNode = new Element("ExpandedAnalysisFileList");
        aNode.addContent(filesNode);
        
        Map analysisMap = new TreeMap();
        Map directoryMap = new TreeMap();
        Map fileMap = new HashMap();
        List analysisNumbers = new ArrayList<String>();
        GetExpandedAnalysisFileList.getFileNamesToDownload(baseDir, a.getKey(), analysisNumbers, analysisMap, directoryMap, false);

        for(Iterator i = analysisNumbers.iterator(); i.hasNext();) {
          String analysisNumber = (String)i.next();
          List directoryKeys   = (List)analysisMap.get(analysisNumber);

          // For each directory of analysis
          for(Iterator i1 = directoryKeys.iterator(); i1.hasNext();) {

            String directoryKey = (String)i1.next();
            
            String[] dirTokens = directoryKey.split("-");

            String directoryName = ""; 
            if (dirTokens.length > 1) {
              directoryName = dirTokens[1];
            } 

            // Show files uploads that are in the staging area.
            if (showUploads.equals("Y")) {
              Element analysisUploadNode = new Element("AnalysisUpload");
              filesNode.addContent(analysisUploadNode);
              String key = a.getKey(Constants.UPLOAD_STAGING_DIR);
              GetAnalysisDownloadList.addExpandedFileNodes(baseDir, aNode, analysisUploadNode, analysisNumber, key, dh, knownAnalysisFileMap, fileMap);
            }
          }
        }

        
        // Get the DataTracks that are linked to this Analysis via its files
        if (a.getFiles().size() > 0) {
          appendDataTrackNodes(sess, a, aNode);
        }

        // Add properties
        Element pNode = getProperties(dh, a);
        aNode.addContent(pNode);

        doc.getRootElement().addContent(aNode);
        
        XMLOutputter out = new org.jdom.output.XMLOutputter();
        this.xmlResult = out.outputString(doc);
      }
    
      if (isValid()) {
        setResponsePage(this.SUCCESS_JSP);
      } else {
        setResponsePage(this.ERROR_JSP);
      }
    
    }catch (UnknownPermissionException e){
      log.error("An exception has occurred in GetAnalysis ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (NamingException e){
      log.error("An exception has occurred in GetAnalysis ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    }catch (SQLException e) {
      log.error("An exception has occurred in GetAnalysis ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (XMLReflectException e){
      log.error("An exception has occurred in GetAnalysis ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (Exception e){
      log.error("An exception has occurred in GetAnalysis ", e);
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
  
  public static Analysis getAnalysisFromAnalysisNumber(Session sess, String  analysisNumber) {
    Analysis analysis = null;
    analysisNumber = analysisNumber.replaceAll("#", "");
    StringBuffer buf = new StringBuffer("SELECT a from Analysis as a where a.number = '" + analysisNumber.toUpperCase() + "'");
    List analyses = sess.createQuery(buf.toString()).list();
    if (analyses.size() > 0) {
      analysis = (Analysis)analyses.get(0);      
    }
    return analysis;
  }
  
  private void appendDataTrackNodes(Session sess, Analysis a, Element aNode) throws UnknownPermissionException {
   
    StringBuffer queryBuf = new StringBuffer();
    queryBuf.append("SELECT dt, af FROM DataTrack dt ");
    queryBuf.append("JOIN dt.dataTrackFiles dtf ");
    queryBuf.append("JOIN dtf.analysisFile af ");
    queryBuf.append("WHERE dtf.idAnalysisFile IN (");
    for (Iterator i = a.getFiles().iterator(); i.hasNext();) {
      AnalysisFile af = (AnalysisFile)i.next();
      queryBuf.append(af.getIdAnalysisFile());
      if (i.hasNext()) {
        queryBuf.append(",");
      }
    }
    queryBuf.append(")");
    Element dtParentNode = new Element("DataTrackList");
    aNode.addContent(dtParentNode);
    List dataTracks = (List)sess.createQuery(queryBuf.toString()).list();
    dtParentNode.setAttribute("count", Integer.valueOf(dataTracks.size()).toString());

    for (Iterator i = dataTracks.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();
      DataTrack dt         = (DataTrack)row[0];
      AnalysisFile afFile = (AnalysisFile)row[1];
      
      Element dtNode = new Element("DataTrack");
      dtParentNode.addContent(dtNode);
      dtNode.setAttribute("idDataTrack", dt.getIdDataTrack().toString());
      dtNode.setAttribute("idDataTrackFolder", dt.getIdDataTrackFolder().toString());
      dtNode.setAttribute("number", dt.getNumber());
      dtNode.setAttribute("name", this.getSecAdvisor().canRead(dt) ? (dt.getName() != null ? dt.getName() : "") : "(Not authorized)");
      dtNode.setAttribute("createdBy", dt.getCreatedBy() != null ? dt.getCreatedBy() : "");
      dtNode.setAttribute("idAnalysisFile", afFile.getIdAnalysisFile().toString());
      dtNode.setAttribute("label", dt.getNumber() + " " + dt.getName());
      dtNode.setAttribute("fileName", afFile.getQualifiedFileName() != null ? afFile.getQualifiedFileName() : "");
      dtNode.setAttribute("codeVisibility", dt.getCodeVisibility() != null ? dt.getCodeVisibility() : "");
    }
  }
  
  /**
   *  Adds custom properties to the xml document.
   *
   *@param  aNode     root element of document to append properties to
   *@param  dh        Dictionary helper to find properties
   *@param  analysis  Analysis object with property values.
   */
  private Element getProperties(DictionaryHelper dh, Analysis analysis) {
    Element propertiesNode = new Element("AnalysisProperties");
    for (Property property : dh.getPropertyList()) {

      if (property.getForAnalysis() == null || !property.getForAnalysis().equals("Y")) {
        // Skip if this property isn't applicable to Analysis
        continue;
      }

      // Check to see if there are Organism restrictions
      Set organismRestrictions = property.getOrganisms();
      if(organismRestrictions != null && organismRestrictions.size() > 0) {
        Integer idOrganism = analysis.getIdOrganism();
        if(organismRestrictions != null) {
          boolean organismFound = false;
          for(Iterator i = organismRestrictions.iterator(); i.hasNext();) {
            Organism thisOrganism = (Organism) i.next();
            if(idOrganism.compareTo(thisOrganism.getIdOrganism())==0) {
              organismFound = true;
              break;
            }
          }
          if(!organismFound) {
            // If the organism has been specified but is not on the "restrict by" list 
            // then don't show the annotation. 
            continue;
          }
        } else {
          // If the organism has not been specified but a "restrict by" list exists
          // then don't show the annotation. 
          continue;
        }
      }

      // Check to see if there are Analysis Type restrictions
      Set analysisTypeRestrictions = property.getAnalysisTypes();
      if(analysisTypeRestrictions != null && analysisTypeRestrictions.size() > 0) {
        Integer at = analysis.getIdAnalysisType();
        if(at != null) {
          boolean atFound = false;
          for(Iterator i = analysisTypeRestrictions.iterator(); i.hasNext();) {
            AnalysisType thisAT = (AnalysisType)i.next();
            if(at.compareTo(thisAT.getIdAnalysisType())==0) {
              atFound = true;
              break;
            }
          }
          if(!atFound) {
            // If the analysis type has been specified but is not on the "restrict by" list 
            // then don't show the annotation. 
            continue;
          }
        } else {
          // If the analysis type has not been specified but a "restrict by" list exists
          // then don't show the annotation. 
          continue;
        }
      }

      // Find the analysis data corresponding to this property (if present) 
      PropertyEntry ap = null;
      for(Iterator i = analysis.getPropertyEntries().iterator(); i.hasNext();) {
        PropertyEntry propertyEntry = (PropertyEntry)i.next();
        if (propertyEntry.getIdProperty().equals(property.getIdProperty())) {
          ap = propertyEntry;
          break;
        }
      }

      // If no data and property is inactive, don't show it.
      if (ap == null && property.getIsActive().equals("N")) {
        continue;
      }
      
      Element propNode = new Element("PropertyEntry");
      propertiesNode.addContent(propNode);

      propNode.setAttribute("idPropertyEntry", ap != null ? ap.getIdPropertyEntry().toString() : "");  
      propNode.setAttribute("name", property.getName());
      propNode.setAttribute("value", ap != null && ap.getValue() != null ? ap.getValue() : "");
      propNode.setAttribute("codePropertyType", property.getCodePropertyType());
      propNode.setAttribute("idProperty", property.getIdProperty().toString());
      propNode.setAttribute("isRequired", property.getIsRequired());

      if (ap != null && ap.getValues() != null && ap.getValues().size() > 0) {
        for (Iterator i1 = ap.getValues().iterator(); i1.hasNext();) {
          PropertyEntryValue av = (PropertyEntryValue)i1.next();
          Element valueNode = new Element("PropertyEntryValue");
          propNode.addContent(valueNode);
          valueNode.setAttribute("idPropertyEntryValue", av.getIdPropertyEntryValue().toString());
          valueNode.setAttribute("value", av.getValue() != null ? av.getValue() : "");
          valueNode.setAttribute("url", av.getUrl() != null ? av.getUrl() : "");
          valueNode.setAttribute("urlDisplay", av.getUrlDisplay() != null ? av.getUrlDisplay() : "");
          valueNode.setAttribute("urlAlias", av.getUrlAlias() != null ? av.getUrlAlias() : "");
        }
      }
      if (property.getCodePropertyType().equals(PropertyType.URL)) {
        // Add an empty value for URL
        Element emptyNode = new Element("PropertyEntryValue");
        propNode.addContent(emptyNode);
        emptyNode.setAttribute("idPropertyEntryValue", "");
        emptyNode.setAttribute("url", "Enter URL here...");
        emptyNode.setAttribute("urlAlias", "Enter alias here...");
        emptyNode.setAttribute("urlDisplay", "");
        emptyNode.setAttribute("value", "");
      }

      if (property.getOptions() != null && property.getOptions().size() > 0) {
        for (Iterator i1 = property.getOptions().iterator(); i1.hasNext();) {
          PropertyOption option = (PropertyOption)i1.next();
          Element optionNode = new Element("PropertyOption");
          propNode.addContent(optionNode);
          optionNode.setAttribute("idPropertyOption", option.getIdPropertyOption().toString());
          optionNode.setAttribute("name", option.getOption());
          boolean isSelected = false;
          if (ap != null && ap.getOptions() != null) {
            for (Iterator i2 = ap.getOptions().iterator(); i2.hasNext();) {
              PropertyOption optionSelected = (PropertyOption)i2.next();
              if (optionSelected.getIdPropertyOption().equals(option.getIdPropertyOption())) {
                isSelected = true;
                break;
              }
            }
          }
          optionNode.setAttribute("selected", isSelected ? "Y" : "N");
        }
      }
    }      
    return propertiesNode;
  }
  
  /**
   *  The callback method allowing you to manipulate the HttpServletRequest
   *  prior to forwarding to the response JSP. This can be used to put the
   *  results from the execute method into the request object for display in the
   *  JSP.
   *
   *@param  request  The new requestState value
   *@return          Description of the Return Value
   */
  public HttpServletRequest setRequestState(HttpServletRequest request) {
    // load any result objects into request attributes, keyed by the useBean id in the jsp
    request.setAttribute("xmlResult",this.xmlResult);
    
    // Garbage collect
    this.xmlResult = null;
    System.gc();
    
    return request;
  }

  /**
   *  The callback method called after the loadCommand, and execute methods,
   *  this method allows you to manipulate the HttpServletResponse object prior
   *  to forwarding to the result JSP (add a cookie, etc.)
   *
   *@param  request  The HttpServletResponse for the command
   *@return          The processed response
   */
  public HttpServletResponse setResponseState(HttpServletResponse response) {
    response.setHeader("Cache-Control", "max-age=0, must-revalidate");
    return response;
  }

}