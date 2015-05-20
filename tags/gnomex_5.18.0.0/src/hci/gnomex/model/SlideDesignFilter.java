package hci.gnomex.model;


import hci.framework.model.DetailObject;
import hci.gnomex.security.SecurityAdvisor;

public class SlideDesignFilter extends DetailObject {
  
  
  // Criteria
  private String                codeApplication;
  private Integer               idVendor;
  private String                slideDesignProtocolName;
  private String                slideDesignName;
  private Integer               idOrganism;
  private Integer               idLab;
  private Integer               idSlideDesign;
  private String                accessionNumberArrayExpress;
  
  
  private StringBuffer          queryBuf;
  private boolean              addWhere = true;
  private SecurityAdvisor       secAdvisor;
  
  
  public StringBuffer getQuery(SecurityAdvisor secAdvisor) {
    this.secAdvisor = secAdvisor;
    queryBuf = new StringBuffer();
    
    queryBuf.append(" SELECT sd, sp");
    
    getQueryBody(queryBuf);
    
    return queryBuf;
    
  }
  
  public void getQueryBody(StringBuffer queryBuf) {
    
    queryBuf.append(" FROM        SlideDesign as sd, SlideProduct as sp ");
    queryBuf.append(" WHERE       sd.idSlideProduct = sp.idSlideProduct");
    // already have a "where"
    addWhere = false;
    
    
    addCriteria();
    
    queryBuf.append(" order by sd.name ");
  
  }
  
  

  private void addCriteria() {
    //  Search by idSlideDesign 
    if (idSlideDesign != null){
      this.addWhereOrAnd();
      queryBuf.append(" sd.idSlideDesign =");
      queryBuf.append(idSlideDesign);
    } 
    // Search by codeApplication number 
    if (codeApplication != null && !codeApplication.equals("")){
      this.addWhereOrAnd();
      queryBuf.append(" sp.microarrayCategories.codeApplication = '");
      queryBuf.append(codeApplication);
      queryBuf.append("'");
    } 
    // Search by vendor 
    if (idVendor != null){
      this.addWhereOrAnd();
      queryBuf.append(" sp.idVendor =");
      queryBuf.append(idVendor);
    } 
    // Search by organism 
    if (idOrganism != null){
      this.addWhereOrAnd();
      queryBuf.append(" sp.idOrganism =");
      queryBuf.append(idOrganism);
    } 
    // Search by lab 
    if (idLab != null){
      this.addWhereOrAnd();
      queryBuf.append(" sp.idLab =");
      queryBuf.append(idLab);
    } 
    // Search by slideDesignProtocolName
    if (slideDesignProtocolName != null && !slideDesignProtocolName.equals("")){
      this.addWhereOrAnd();
      queryBuf.append(" sd.slideDesignProtocolName LIKE '%");
      queryBuf.append(slideDesignProtocolName);
      queryBuf.append("%'");
    } 
    // Search by slideDesignName
    if (slideDesignName != null && !slideDesignName.equals("")){
      this.addWhereOrAnd();
      queryBuf.append(" sd.name LIKE '%");
      queryBuf.append(slideDesignName);
      queryBuf.append("%'");
    } 
    //  Search by accessionNumberArrayExpress
    if (accessionNumberArrayExpress != null && !accessionNumberArrayExpress.equals("")){
      this.addWhereOrAnd();
      queryBuf.append(" sd.accessionNumberArrayExpress LIKE '%");
      queryBuf.append(accessionNumberArrayExpress);
      queryBuf.append("%'");
    } 
  }
  
 
    
  
  protected boolean addWhereOrAnd() {
    if (addWhere) {
      queryBuf.append(" WHERE ");
      addWhere = false;
    } else {
      queryBuf.append(" AND ");
    }
    return addWhere;
  }

  
  public String getCodeApplication() {
    return codeApplication;
  }

  
  public void setCodeApplication(String codeApplication) {
    this.codeApplication = codeApplication;
  }

  
  public Integer getIdVendor() {
    return idVendor;
  }

  
  public void setIdVendor(Integer idVendor) {
    this.idVendor = idVendor;
  }

  
  public String getSlideDesignName() {
    return slideDesignName;
  }

  
  public void setSlideDesignName(String slideDesignName) {
    this.slideDesignName = slideDesignName;
  }

  
  public String getSlideDesignProtocolName() {
    return slideDesignProtocolName;
  }

  
  public void setSlideDesignProtocolName(String slideDesignProtocolName) {
    this.slideDesignProtocolName = slideDesignProtocolName;
  }

  
  public Integer getIdOrganism() {
    return idOrganism;
  }

  
  public void setIdOrganism(Integer idOrganism) {
    this.idOrganism = idOrganism;
  }

  
  public Integer getIdSlideDesign() {
    return idSlideDesign;
  }

  
  public void setIdSlideDesign(Integer idSlideDesign) {
    this.idSlideDesign = idSlideDesign;
  }

  
  public Integer getIdLab() {
    return idLab;
  }

  
  public void setIdLab(Integer idLab) {
    this.idLab = idLab;
  }

  
  public String getAccessionNumberArrayExpress() {
    return accessionNumberArrayExpress;
  }

  
  public void setAccessionNumberArrayExpress(String accessionNumberArrayExpress) {
    this.accessionNumberArrayExpress = accessionNumberArrayExpress;
  }

  
  


  
  
}
