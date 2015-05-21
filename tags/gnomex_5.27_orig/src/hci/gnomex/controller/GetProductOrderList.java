package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.model.Lab;
import hci.gnomex.model.ProductOrderFilter;
import hci.gnomex.security.SecurityAdvisor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.Iterator;
import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

public class GetProductOrderList extends GNomExCommand implements Serializable {

  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GetProductOrderList.class);

  private ProductOrderFilter productOrderFilter;

  private HashMap<Integer, List<Integer>> labOrders = new HashMap<Integer, List<Integer>>();
  private HashMap<Integer, String> productOrderMap = new HashMap<Integer, String>();

  public void loadCommand(HttpServletRequest request, HttpSession sess) {
    productOrderFilter = new ProductOrderFilter(this.getSecAdvisor());
    HashMap errors = this.loadDetailObject(request, productOrderFilter);
    this.addInvalidFields(errors);

    //TODO: Different permissions
    if (!this.getSecAdvisor().hasPermission(SecurityAdvisor.CAN_MANAGE_BILLING)) {
      this.addInvalidField("permission", "Insufficient permission to manage billing items");
    }
  }

  public Command execute() throws RollBackCommandException {

    try {
      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());

      Document doc = new Document(new Element("ProductOrderList"));

      StringBuffer buf = productOrderFilter.getProductOrderQuery();
      log.info("Query for GetProductOrderList: " + buf.toString());

      List productOrders = sess.createQuery(buf.toString()).list();

      for(Iterator i = productOrders.iterator(); i.hasNext();) {
        Object row[] = (Object[])i.next();
        Integer idProductOrder = (Integer)row[0];
        String  productOrderNumber = (String)row[1];
        productOrderMap.put( idProductOrder, productOrderNumber!=null ? productOrderNumber:"" );
        Integer idLab = (Integer)row[2];
        if(labOrders.containsKey(idLab)) {
          labOrders.get(idLab).add(idProductOrder);
        } else {
          List<Integer> labsPO = new ArrayList();
          labsPO.add(idProductOrder);
          labOrders.put(idLab, labsPO);
        }
      }

      for(Iterator j = labOrders.keySet().iterator(); j.hasNext();) {
        Integer idLab = (Integer)j.next();
        Lab l = (Lab)sess.load(Lab.class, idLab);
        List productOrderIDs = labOrders.get(idLab);


        Element lab = new Element("Lab");
        lab.setAttribute("display", l.getName(false, true));
        lab.setAttribute("idLab", idLab.toString());

        lab.setAttribute("icon", "assets/group.png");
        for(Iterator k = productOrderIDs.iterator(); k.hasNext();) {
          Integer id = (Integer)k.next();
          String number = productOrderMap.get( id );
          String display = "Product Order " + (!number.equals("")?number:id.toString());
          Element po = new Element("ProductOrder");
          po.setAttribute("display", display);
          po.setAttribute("icon", "assets/basket.png");
          po.setAttribute("idProductOrder", id.toString());
          po.setAttribute("productOrderNumber", (!number.equals("")?number:id.toString()));
          lab.addContent(po);
        }

        doc.getRootElement().addContent(lab);

      }

      XMLOutputter out = new org.jdom.output.XMLOutputter();
      this.xmlResult = out.outputString(doc);

      setResponsePage(this.SUCCESS_JSP);

    }catch(Exception e) {
      log.error("An exception has occurred in GetProductOrderList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());  
    }finally {
      try {
        this.getSecAdvisor().closeReadOnlyHibernateSession();        
      } catch(Exception e) {

      }
    }
    return this;
  }


  public void validate() {

  }

}
