package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.security.UnknownPermissionException;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.FlowCell;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.FlowCellHTMLFormatter;

import java.io.Serializable;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.apache.log4j.Logger;

public class ShowFlowCellPrepForm extends GNomExCommand implements Serializable {
  
  private static Logger LOG = Logger.getLogger(ShowFlowCellPrepForm.class);
  
  public String SUCCESS_JSP = "/getHTML.jsp";
  
  private Integer          idFlowCell;
  private FlowCell          flowCell;
  
  private DictionaryHelper dictionaryHelper;
  
  

  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    if (request.getParameter("idFlowCell") != null) {
      idFlowCell = new Integer(request.getParameter("idFlowCell"));
    } else {
      this.addInvalidField("idFlowCell", "idFlowCell is required");
    }
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      
   
      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());
      
     
      dictionaryHelper = DictionaryHelper.getInstance(sess);
    
      flowCell = (FlowCell)sess.get(FlowCell.class, idFlowCell);
      if (flowCell == null) {
        this.addInvalidField("no flow cell", "Flow cell not found");
      }
      

      if (this.isValid()) {
        if (this.getSecAdvisor().hasPermission(SecurityAdvisor.CAN_MANAGE_WORKFLOW)) { 
          
          FlowCellHTMLFormatter formatter = new FlowCellHTMLFormatter(flowCell, dictionaryHelper);

          Element root = new Element("HTML");
            Document doc = new Document(root);
            
            Element head = new Element("HEAD");
            root.addContent(head);
            
            Element link = new Element("link");
            link.setAttribute("rel", "stylesheet");
            link.setAttribute("type", "text/css");
            link.setAttribute("href", Constants.REQUEST_FORM_CSS);
            head.addContent(link);
            
            Element title = new Element("TITLE");
            title.addContent("Flow Cell Preparation - " + flowCell.getNumber());
            head.addContent(title);
            
            Element body = new Element("BODY");
            root.addContent(body);

            Element outerDiv = new Element("DIV");
            outerDiv.setAttribute("id", "container");
            body.addContent(outerDiv);
            
            Element maindiv = new Element("DIV");
            maindiv.setAttribute("id", "containerForm");
            outerDiv.addContent(maindiv);


            
            // 'Print this page' link
            Element printColRight = new Element("DIV");
            printColRight.setAttribute("id", "printLinkColRight");
            Element printLink = new Element("A");
            printLink.setAttribute("HREF", "javascript:window.print()");
            printLink.addContent("Print page");
            printColRight.addContent(printLink);
            maindiv.addContent(printColRight);
            Element ftr = new Element("DIV");
            ftr.setAttribute("id", "footer");            
            maindiv.addContent(ftr);
            

            Element h2 = new Element("H2");
            h2.addContent("Flow Cell Preparation Report - " + flowCell.getNumber());
            maindiv.addContent(h2);
            Element h4 = new Element("H4");
            h4.addContent(dictionaryHelper.getSequencingPlatform(flowCell.getCodeSequencingPlatform()));
            maindiv.addContent(h4);
            
            maindiv.addContent(formatter.makeFlowCellPrepTable());

            
            
            
            if (!flowCell.getFlowCellChannels().isEmpty()) {
              maindiv.addContent(formatter.makeFlowCellPrepChannelTable(flowCell.getFlowCellChannels()));          
            }

            
          
            XMLOutputter out = new org.jdom.output.XMLOutputter();
            out.setOmitEncoding(true);
            this.xmlResult = out.outputString(doc);
            this.xmlResult = this.xmlResult.replaceAll("&amp;", "&");
            this.xmlResult = this.xmlResult.replaceAll("�",     "&micro");
          
        } else {
          this.addInvalidField("Insufficient permissions", "Insufficient permission to show flow cell report.");
        }
        
      }
    
      if (isValid()) {
        setResponsePage(this.SUCCESS_JSP);
      } else {
        setResponsePage(this.ERROR_JSP);
      }
    
    }catch (UnknownPermissionException e){
      LOG.error("An exception has occurred in ShowFlowCellPrepForm ", e);

      throw new RollBackCommandException(e.getMessage());
        
    }catch (NamingException e){
      LOG.error("An exception has occurred in ShowFlowCellPrepForm ", e);

      throw new RollBackCommandException(e.getMessage());
        
    }catch (SQLException e) {
      LOG.error("An exception has occurred in ShowFlowCellPrepForm ", e);

      throw new RollBackCommandException(e.getMessage());
      
    } catch (Exception e) {
      LOG.error("An exception has occurred in ShowFlowCellForm ", e);

      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        //closeReadOnlyHibernateSession;    
      } catch(Exception e){
        LOG.error("Error", e);
      }
    }
    
    return this;
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
    return response;
  } 
 

  
}