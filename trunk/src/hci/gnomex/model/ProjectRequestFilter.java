package hci.gnomex.model;


import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.DictionaryHelper;
import hci.framework.model.DetailObject;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ProjectRequestFilter extends DetailObject {
  
  
  // Criteria
  private Integer               idAppUser;
  private Integer               idLab;
  private Integer               idRequest;
  private Integer               idProject;
  private String                codeRequestCategory;
  private String                codeApplication;
  private Integer               idSlideProduct;
  private Integer               idOrganism;
  private String                projectDescriptionText1;
  private String                projectDescriptionText2;
  private String                projectDescriptionText3;
  private String                projectDescriptionText4;
  private String                matchAnyProjectDescriptionText;
  private String                matchAllProjectDescriptionText;
  private List                  experimentDesignCodes;
  private List                  experimentFactorCodes;
  private String                searchOrganismOnSlideProduct;
  private String                searchOrganismOnSample;
  private String                publicExperimentsInOtherGroups;
  private String                allExperiments = "N";
  private String                showSamples = "Y";
  private String                showCategory = "Y";
  private String                showExternalExperiments = "Y";
  private String                lastWeek  = "N";
  private String                lastMonth = "N";
  private String                lastThreeMonths = "N";
  private String                lastYear  = "N";
  private String                isMicroarray = "N";
  private String                isNextGenSeq = "N";
  private String                isBioanalyzer = "N";
  private String                isCapSeq = "N";
  private String                isMitSeq = "N";
  private String                isFragAnal = "N";
  private String                isCherryPick = "N";
  private String                isExternalOnly = "N";
  private String                showEmptyProjectFolders = "N";
  
  
  
  
  private StringBuffer          queryBuf;
  private boolean              addWhere = true;
  private SecurityAdvisor       secAdvisor;
  private DictionaryHelper      dictionaryHelper;
  
  public boolean hasSufficientCriteria(SecurityAdvisor secAdvisor) {
    this.secAdvisor = secAdvisor;
    boolean hasLimitingCriteria = false;
    
    if ((idRequest != null) ||
        idLab != null ||
        idProject != null ||
        idAppUser != null ||
        (allExperiments != null && allExperiments.equalsIgnoreCase("Y")) ||
        (publicExperimentsInOtherGroups != null && publicExperimentsInOtherGroups.equalsIgnoreCase("Y")) ||                
        (lastWeek != null && lastWeek.equalsIgnoreCase("Y")) ||
        (lastMonth != null && lastMonth.equalsIgnoreCase("Y")) ||
        (lastThreeMonths != null && lastThreeMonths.equalsIgnoreCase("Y")) ||
        (lastYear != null && lastYear.equalsIgnoreCase("Y")) ||
        (isExternalOnly != null && isExternalOnly.equalsIgnoreCase("Y")) ||
        (codeRequestCategory != null && !codeRequestCategory.equals("")) ||
        (codeApplication != null && !codeApplication.equals("")) ||
        idSlideProduct != null ||
        idOrganism != null ||
        (isMicroarray != null && isMicroarray.equalsIgnoreCase("Y")) ||
        (isNextGenSeq != null && isNextGenSeq.equalsIgnoreCase("Y")) ||
        (isBioanalyzer != null && isBioanalyzer.equalsIgnoreCase("Y")) ||
        (isCapSeq != null && isCapSeq.equalsIgnoreCase("Y")) ||
        (isMitSeq != null && isMitSeq.equalsIgnoreCase("Y")) ||
        (isFragAnal != null && isFragAnal.equalsIgnoreCase("Y")) ||
        (isCherryPick != null && isCherryPick.equalsIgnoreCase("Y")) ||
        (projectDescriptionText1 != null && !projectDescriptionText1.equals("")) ||
        (projectDescriptionText2 != null && !projectDescriptionText2.equals("")) ||
        (projectDescriptionText3 != null && !projectDescriptionText3.equals("")) ||
        (projectDescriptionText4 != null && !projectDescriptionText4.equals("")) ||
        (experimentDesignCodes != null && experimentDesignCodes.size() > 0) ||
        (experimentFactorCodes != null && experimentFactorCodes.size() > 0)) {
      hasLimitingCriteria = true;
    } 
    
    // Require limiting criteria for admins since they are not scoped by labs 
    // automatically
    if (secAdvisor.hasPermission(secAdvisor.CAN_ACCESS_ANY_OBJECT)) {
      return hasLimitingCriteria;
    } else {
      // Non-admins are always scoped by either lab, own experiments,
      // so no need to require any additional filtering.
      return true;
    }
  }
    
  
  public StringBuffer getQuery(SecurityAdvisor secAdvisor, DictionaryHelper dictionaryHelper) {
    this.secAdvisor = secAdvisor;
    this.dictionaryHelper = dictionaryHelper;
    queryBuf = new StringBuffer();
    addWhere = true;
    
    queryBuf.append(" SELECT distinct project.idProject, project.name, project.description, '', ");
    queryBuf.append("        req.idRequest, req.number, req.createDate,  ");
    queryBuf.append("        sample.name, sample.idSampleType, ");
    queryBuf.append("        req.idSlideProduct, sample.idSample, ");
    queryBuf.append("        project.idLab, req.idLab, project.idAppUser, req.idAppUser, req.codeRequestCategory, req.codeApplication, lab.lastName, lab.firstName, slideProduct.name, projectLab.lastName, projectLab.firstName, ");
    queryBuf.append("        '', req.codeVisibility,");
    queryBuf.append("        projectOwner.firstName, projectOwner.lastName, ");
    queryBuf.append("        reqOwner.firstName, reqOwner.lastName, req.isExternal, req.name, sample.number, req.idInstitution ");
    
    getQueryBody(queryBuf);
    
    return queryBuf;
    
  }
  
  public void getQueryBody(StringBuffer queryBuf) {
    
    queryBuf.append(" FROM                Project as project ");
    queryBuf.append(" JOIN                project.lab as projectLab ");
    queryBuf.append(" JOIN                project.appUser as projectOwner ");
    queryBuf.append(" LEFT JOIN           project.requests as req ");
    queryBuf.append(" LEFT JOIN           req.appUser as reqOwner ");
    queryBuf.append(" LEFT JOIN           req.lab as lab ");
    queryBuf.append(" LEFT JOIN           req.slideProduct as slideProduct ");
    queryBuf.append(" LEFT JOIN           req.samples as sample ");
    queryBuf.append(" LEFT JOIN           req.collaborators as collab ");

    if (hasSlideProductCriteria()) {
      queryBuf.append(" JOIN           req.slideProduct as sp ");
    }
    
    if (experimentDesignCodes != null && experimentDesignCodes.size() > 0) {
      queryBuf.append(" JOIN           project.experimentDesignEntries as ede ");
    }
    if (experimentFactorCodes != null && experimentFactorCodes.size() > 0) {
      queryBuf.append(" JOIN           project.experimentFactorEntries as efe ");
    }
    addProjectCriteria();
    addRequestCriteria();
    addSampleCriteria();
    
    addSecurityCriteria();
    if (hasSlideProductCriteria()) {
      addSlideProductCriteria();
    }
    
    if (this.showCategory.equals("Y")) {
      queryBuf.append(" order by projectLab.lastName, projectLab.firstName, project.name, req.codeRequestCategory, req.codeApplication, req.createDate desc, req.idRequest desc, sample.number asc");
    } else {
      queryBuf.append(" order by projectLab.lastName, projectLab.firstName, project.name, req.createDate desc, req.idRequest desc, sample.number asc");
    }
  
  }
  
  public StringBuffer getAnalysisExperimentQuery(SecurityAdvisor secAdvisor) {
    this.secAdvisor = secAdvisor;
    queryBuf = new StringBuffer();
    addWhere = true;
    
    queryBuf.append(" SELECT DISTINCT ");
    queryBuf.append("        ax.idRequest, ");
    queryBuf.append("        a.number,  ");
    queryBuf.append("        a.name  ");
    
    getAnalysisExperimentQueryBody(queryBuf);
    
    return queryBuf;
    
  }
  
  public void getAnalysisExperimentQueryBody(StringBuffer queryBuf) {
    
    queryBuf.append(" FROM                Request as req ");
    queryBuf.append(" JOIN                req.analysisExperimentItems as ax ");
    queryBuf.append(" JOIN                ax.analysis as a ");
    queryBuf.append(" LEFT JOIN           req.collaborators as collab ");

    addRequestCriteria();
    
    addAnalysisExperimentSecurityCriteria();
    
    
  
  }
  
  private boolean hasSlideProductCriteria() {
    if ((idOrganism != null && !searchOrganismOnSlideProduct.equals("") && searchOrganismOnSlideProduct.equalsIgnoreCase("Y")) || 
        idSlideProduct != null) {
      return true;
    } else {
      return false;
    }
  }
  
  
  private void addProjectCriteria() {
    // Search by lab 
    if (idLab != null){
      this.addWhereOrAnd();
      queryBuf.append(" project.idLab =");
      queryBuf.append(idLab);
    } 
    
    if ((projectDescriptionText1 != null && !projectDescriptionText1.equals("")) ||
        (projectDescriptionText2 != null && !projectDescriptionText2.equals("")) ||
        (projectDescriptionText3 != null && !projectDescriptionText3.equals("")) ||
        (projectDescriptionText4 != null && !projectDescriptionText4.equals(""))){ 
      
      this.addWhereOrAnd();
      queryBuf.append("(");
      
      // Search by project description text1
      boolean textCriteriaAdded = false;
      if (projectDescriptionText1 != null && !projectDescriptionText1.equals("")){
        queryBuf.append(" project.description like ");
        queryBuf.append("'%");
        queryBuf.append(projectDescriptionText1);
        queryBuf.append("%'");
        textCriteriaAdded = true;
      }
      
      // Search by project description text2
      if (projectDescriptionText2 != null && !projectDescriptionText2.equals("")){
        if (textCriteriaAdded) {
          if (matchAnyProjectDescriptionText.equals("Y")) {
            this.addWhereOrOr();          
          } else {
            this.addWhereOrAnd();
          }          
        }
        queryBuf.append(" project.description like ");
        queryBuf.append("'%");
        queryBuf.append(projectDescriptionText2);
        queryBuf.append("%'");
        textCriteriaAdded = true;
      } 
      
      //    Search by project description text3
      if (projectDescriptionText3 != null && !projectDescriptionText3.equals("")){
        if (textCriteriaAdded) {
          if (matchAnyProjectDescriptionText.equals("Y")) {
            this.addWhereOrOr();          
          } else {
            this.addWhereOrAnd();
          }          
        }
        queryBuf.append(" project.description like ");
        queryBuf.append("'%");
        queryBuf.append(projectDescriptionText3);
        queryBuf.append("%'");
        textCriteriaAdded = true;
      } 
      
      //    Search by project description text4
      if (projectDescriptionText4 != null && !projectDescriptionText4.equals("")){
        if (textCriteriaAdded) {
          if (matchAnyProjectDescriptionText.equals("Y")) {
            this.addWhereOrOr();          
          } else {
            this.addWhereOrAnd();
          }          
        }
        queryBuf.append(" project.description like ");
        queryBuf.append("'%");
        queryBuf.append(projectDescriptionText4);
        queryBuf.append("%'");
        textCriteriaAdded = true;
      } 

      queryBuf.append(")");
    }
    
    // Search by project experiment design codes
    if (experimentDesignCodes != null && experimentDesignCodes.size() > 0) {
      this.addWhereOrAnd();
      queryBuf.append(" ede.codeExperimentDesign in (");
     
      for(Iterator i = experimentDesignCodes.iterator(); i.hasNext();) {
        String code = (String)i.next();
        queryBuf.append(" '" + code + "'");
        if (i.hasNext()) {
          queryBuf.append(", ");
        }        
      }
      queryBuf.append(")");
    }
    // Search by project experiment factor codes
    if (experimentFactorCodes != null && experimentFactorCodes.size() > 0) {
      this.addWhereOrAnd();
      queryBuf.append(" efe.codeExperimentFactor in (");
     
      for(Iterator i = experimentFactorCodes.iterator(); i.hasNext();) {
        String code = (String)i.next();
        queryBuf.append(" '" + code + "'");
        if (i.hasNext()) {
          queryBuf.append(", ");
        }        
      }
      queryBuf.append(")");
    }
  }

  private void addRequestCriteria() {

    // Show empty project folders only if requested.
    if (showEmptyProjectFolders.equals("Y")) {
      this.addWhereOrAnd();
      queryBuf.append("( req.idRequest is null OR ( 1=1 ");
    } else {
      this.addWhereOrAnd();
      queryBuf.append(" req.idRequest is not null ");
    }
    // Search by user 
    if (idAppUser != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idSubmitter = ");
      queryBuf.append(idAppUser);
    } 
    //  Search by idRequest 
    if (idRequest != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idRequest = ");
      queryBuf.append(idRequest);
    } 
    //  Search by Project 
    if (idProject != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idProject = ");
      queryBuf.append(idProject);
    } 
    //  Search by RequestCategory 
    if (codeRequestCategory != null && !codeRequestCategory.equals("")){
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory = '");
      queryBuf.append(codeRequestCategory);
      queryBuf.append("'");
    }
    //  Search by  Application 
    if (codeApplication != null && !codeApplication.equals("")){
      this.addWhereOrAnd();
      queryBuf.append(" req.codeApplication = '");
      queryBuf.append(codeApplication);
      queryBuf.append("'");
    } 
    //  External experiments
    if (showExternalExperiments != null && showExternalExperiments.equals("N")){
      this.addWhereOrAnd();
      queryBuf.append(" req.isExternal != 'Y'");
    } 
    
    //  External experiments (only)
    if (isExternalOnly != null && isExternalOnly.equals("Y")){
      this.addWhereOrAnd();
      queryBuf.append(" req.isExternal = 'Y'");
    } 
    
    // Search for requests submitted in last week
    if (lastWeek.equals("Y")) {

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.DAY_OF_YEAR, -7);
      java.sql.Date lastWeek = new java.sql.Date(cal.getTimeInMillis());
      
      this.addWhereOrAnd();
      queryBuf.append(" req.createDate >= '");
      queryBuf.append(this.formatDate(lastWeek, this.DATE_OUTPUT_SQL));
      queryBuf.append("'");
    }
    // Search for requests submitted in last month
    if (lastMonth.equals("Y")) {

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, -1);
      java.sql.Date lastMonth = new java.sql.Date(cal.getTimeInMillis());
      
      this.addWhereOrAnd();
      queryBuf.append(" req.createDate >= '");
      queryBuf.append(this.formatDate(lastMonth, this.DATE_OUTPUT_SQL));
      queryBuf.append("'");
    }
    // Search for requests submitted in last 3 months
    if (lastThreeMonths.equals("Y")) {

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, -3);
      java.sql.Date last3Month = new java.sql.Date(cal.getTimeInMillis());
      
      this.addWhereOrAnd();
      queryBuf.append(" req.createDate >= '");
      queryBuf.append(this.formatDate(last3Month, this.DATE_OUTPUT_SQL));
      queryBuf.append("'");
    }
    // Search for requests submitted in last year
    if (lastYear.equals("Y")) {

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.YEAR, -1);
      java.sql.Date lastYear = new java.sql.Date(cal.getTimeInMillis());
      
      this.addWhereOrAnd();
      queryBuf.append(" req.createDate >= '");
      queryBuf.append(this.formatDate(lastYear, this.DATE_OUTPUT_SQL));
      queryBuf.append("'");
    }    
    
    // Search for Solexa requests
    if (isNextGenSeq.equals("Y")) {
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory IN (");
      
      List requestCategories = dictionaryHelper.getRequestCategoryList();
      int count = 0;
      for (Iterator i = requestCategories.iterator(); i.hasNext();) {
        RequestCategory requestCategory = (RequestCategory)i.next();
        if (requestCategory.isNextGenSeqRequestCategory()) {
          if (count > 0) {
            queryBuf.append(", ");            
          }
          
          queryBuf.append("'");
          queryBuf.append(requestCategory.getCodeRequestCategory());
          queryBuf.append("'");    
          count++;
        }
        
      }
     
      queryBuf.append(") ");
    }  else if (isBioanalyzer.equals("Y")) {
      // Search for bioanalyzer requests
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory = '");
      queryBuf.append(RequestCategory.QUALITY_CONTROL_REQUEST_CATEGORY);
      queryBuf.append("'");
    } else if (isMicroarray.equals("Y")) {
      // Search for microarray requests
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory in (");
      queryBuf.append("'");
      queryBuf.append(RequestCategory.AFFYMETRIX_MICROARRAY_REQUEST_CATEGORY);
      queryBuf.append("', ");
      queryBuf.append("'");
      queryBuf.append(RequestCategory.INHOUSE_MICROARRAY_REQUEST_CATEGORY);
      queryBuf.append("', ");
      queryBuf.append("'");
      queryBuf.append(RequestCategory.NIMBLEGEN_MICROARRAY_REQUEST_CATEGORY);
      queryBuf.append("', ");
      queryBuf.append("'");
      queryBuf.append(RequestCategory.OTHER_MICROARRAY_REQUEST_CATEGORY);
      queryBuf.append("', ");
      queryBuf.append("'");
      queryBuf.append(RequestCategory.AGILIENT_1_COLOR_MICROARRAY_REQUEST_CATEGORY);
      queryBuf.append("', ");
      queryBuf.append("'");
      queryBuf.append(RequestCategory.AGILIENT_MICROARRAY_REQUEST_CATEGORY);
      queryBuf.append("') ");
    }  else if (isCapSeq.equals("Y")) {
      // Search for capillary sequencing requests
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory = '");
      queryBuf.append(RequestCategory.CAPILLARY_SEQUENCING_REQUEST_CATEGORY);
      queryBuf.append("'");
    } else if (isMitSeq.equals("Y")) {
      // Search for mitochondrial sequencing requests
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory = '");
      queryBuf.append(RequestCategory.MITOCHONDRIAL_DLOOP_SEQ_REQUEST_CATEGORY);
      queryBuf.append("'");
    } else if (isFragAnal.equals("Y")) {
      // Search for fragment analysis requests
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory = '");
      queryBuf.append(RequestCategory.FRAGMENT_ANALYSIS_REQUEST_CATEGORY);
      queryBuf.append("'");
    } else if (isCherryPick.equals("Y")) {
      // Search for cherry picking requests
      this.addWhereOrAnd();
      queryBuf.append(" req.codeRequestCategory = '");
      queryBuf.append(RequestCategory.CHERRY_PICKING_REQUEST_CATEGORY);
      queryBuf.append("'");
    }   

    // Close paren for showing empty folders or
    if (showEmptyProjectFolders.equals("Y")) {
      queryBuf.append(" ) )");
    }
  }
  
  private void addSlideProductCriteria() {
    //  Search by idSlideProduct 
    if (idSlideProduct != null){
      this.addWhereOrAnd();
      queryBuf.append(" sp.idSlideProduct = ");
      queryBuf.append(idSlideProduct);
    }
    
    //  Search by idOrganism (of slide product)
    if (idOrganism != null && searchOrganismOnSlideProduct != null && searchOrganismOnSlideProduct.equalsIgnoreCase("Y")){
      this.addWhereOrAnd();
      queryBuf.append(" sp.idOrganism = ");
      queryBuf.append(idOrganism);
    } 
  }
  
  private void addSampleCriteria() {
    
    //  Search by organism (of sample)
    if (idOrganism != null && searchOrganismOnSample != null && searchOrganismOnSample.equalsIgnoreCase("Y")){
      this.addWhereOrAnd();
      queryBuf.append(" sample.idOrganism =");
      queryBuf.append(idOrganism);
    } 
  }

  
  private void addSecurityCriteria() {

    if (this.allExperiments != null && this.allExperiments.equals("Y")) {
      boolean scopeToGroup = false;
      secAdvisor.buildSpannedSecurityCriteria(queryBuf, "project", "req", "collab", addWhere, "req.codeVisibility", scopeToGroup, "req.idRequest");
    } else if (this.publicExperimentsInOtherGroups != null && this.publicExperimentsInOtherGroups.equalsIgnoreCase("Y")) {
      addWhere = secAdvisor.addPublicOnlySecurityCriteria(queryBuf, "req", addWhere);
    } else {
      boolean scopeToGroup = true;
      secAdvisor.buildSpannedSecurityCriteria(queryBuf, "project", "req", "collab", addWhere, "req.codeVisibility", scopeToGroup, "req.idRequest");

    }
  }

  private void addAnalysisExperimentSecurityCriteria() {
    
    if (this.publicExperimentsInOtherGroups != null && this.publicExperimentsInOtherGroups.equalsIgnoreCase("Y")) {
      secAdvisor.addPublicOnlySecurityCriteria(queryBuf, "req", addWhere);
    } else {
      if (secAdvisor.hasPermission(secAdvisor.CAN_ACCESS_ANY_OBJECT)) {
        
      }  else {
        addWhere = secAdvisor.buildSecurityCriteria(queryBuf, "req", "collab", addWhere, true, true);
      }
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
  
  protected boolean addWhereOrOr() {
    if (addWhere) {
      queryBuf.append(" WHERE ");
      addWhere = false;
    } else {
      queryBuf.append(" OR ");
    }
    return addWhere;
  }

  
  public Integer getIdLab() {
    return idLab;
  }

  
  public Integer getIdUser() {
    return idAppUser;
  }

  

  
  public void setIdLab(Integer idLab) {
    this.idLab = idLab;
  }

  
  public void setIdUser(Integer idAppUser) {
    this.idAppUser = idAppUser;
  }

 
  
  public Integer getIdAppUser() {
    return idAppUser;
  }

  
  public void setIdAppUser(Integer idAppUser) {
    this.idAppUser = idAppUser;
  }

  
  public Integer getIdRequest() {
    return idRequest;
  }

  
  public void setIdRequest(Integer idRequest) {
    this.idRequest = idRequest;
  }

  
  public Integer getIdProject() {
    return idProject;
  }

  
  public void setIdProject(Integer idProject) {
    this.idProject = idProject;
  }

  
  public String getCodeApplication() {
    return codeApplication;
  }

  
  public void setCodeApplication(String codeApplication) {
    this.codeApplication = codeApplication;
  }

  
  public String getCodeRequestCategory() {
    return codeRequestCategory;
  }

  
  public void setCodeRequestCategory(String codeRequestCategory) {
    this.codeRequestCategory = codeRequestCategory;
  }

  
  public Integer getIdSlideProduct() {
    return idSlideProduct;
  }

  
  public void setIdSlideProduct(Integer idSlideProduct) {
    this.idSlideProduct = idSlideProduct;
  }

  
  public String getProjectDescriptionText1() {
    return projectDescriptionText1;
  }

  
  public void setProjectDescriptionText1(String projectDescriptionText1) {
    this.projectDescriptionText1 = projectDescriptionText1;
  }

  
  public String getProjectDescriptionText2() {
    return projectDescriptionText2;
  }

  
  public void setProjectDescriptionText2(String projectDescriptionText2) {
    this.projectDescriptionText2 = projectDescriptionText2;
  }

  
  public String getProjectDescriptionText3() {
    return projectDescriptionText3;
  }

  
  public void setProjectDescriptionText3(String projectDescriptionText3) {
    this.projectDescriptionText3 = projectDescriptionText3;
  }

  
  public String getMatchAllProjectDescriptionText() {
    return matchAllProjectDescriptionText;
  }

  
  public void setMatchAllProjectDescriptionText(
      String matchAllProjectDescriptionText) {
    this.matchAllProjectDescriptionText = matchAllProjectDescriptionText;
  }

  
  public String getMatchAnyProjectDescriptionText() {
    return matchAnyProjectDescriptionText;
  }

  
  public void setMatchAnyProjectDescriptionText(
      String matchAnyProjectDescriptionText) {
    this.matchAnyProjectDescriptionText = matchAnyProjectDescriptionText;
  }


  
  public List getExperimentDesignCodes() {
    return experimentDesignCodes;
  }

  
  public void setExperimentDesignCodes(List experimentDesignCodes) {
    this.experimentDesignCodes = experimentDesignCodes;
  }

  
  public List getExperimentFactorCodes() {
    return experimentFactorCodes;
  }

  
  public void setExperimentFactorCodes(List experimentFactorCodes) {
    this.experimentFactorCodes = experimentFactorCodes;
  }

  
  public String getProjectDescriptionText4() {
    return projectDescriptionText4;
  }

  
  public void setProjectDescriptionText4(String projectDescriptionText4) {
    this.projectDescriptionText4 = projectDescriptionText4;
  }

  
  public Integer getIdOrganism() {
    return idOrganism;
  }

  
  public void setIdOrganism(Integer idOrganism) {
    this.idOrganism = idOrganism;
  }

  
  public String getSearchOrganismOnSample() {
    return searchOrganismOnSample;
  }

  
  public void setSearchOrganismOnSample(String searchOrganismOnSample) {
    this.searchOrganismOnSample = searchOrganismOnSample;
  }

  
  public String getSearchOrganismOnSlideProduct() {
    return searchOrganismOnSlideProduct;
  }

  
  public void setSearchOrganismOnSlideProduct(String searchOrganismOnSlideProduct) {
    this.searchOrganismOnSlideProduct = searchOrganismOnSlideProduct;
  }

  
  
  public String getShowSamples() {
    return showSamples;
  }

  
  public void setShowSamples(String showSamples) {
    this.showSamples = showSamples;
  }

  
  public String getShowCategory() {
    return showCategory;
  }

  
  public void setShowCategory(String showCategory) {
    this.showCategory = showCategory;
  }

  
  public String getLastWeek() {
    return lastWeek;
  }

  
  public void setLastWeek(String lastWeek) {
    this.lastWeek = lastWeek;
  }

  
  public String getLastMonth() {
    return lastMonth;
  }

  
  public void setLastMonth(String lastMonth) {
    this.lastMonth = lastMonth;
  }

  
  public String getLastYear() {
    return lastYear;
  }

  
  public void setLastYear(String lastYear) {
    this.lastYear = lastYear;
  }

  
  public String getpublicExperimentsInOtherGroups() {
    return publicExperimentsInOtherGroups;
  }

  
  public void setPublicExperimentsInOtherGroups(String publicExperimentsInOtherGroups) {
    this.publicExperimentsInOtherGroups = publicExperimentsInOtherGroups;
  }


  
  public String getIsMicroarray() {
    return isMicroarray;
  }


  
  public void setIsMicroarray(String isMicroarray) {
    this.isMicroarray = isMicroarray;
  }


  
  
  
  public String getIsBioanalyzer() {
    return isBioanalyzer;
  }


  
  public void setIsBioanalyzer(String isBioanalyzer) {
    this.isBioanalyzer = isBioanalyzer;
  }


  
  public String getIsCapSeq() {
    return isCapSeq;
  }


  public void setIsCapSeq(String isCapSeq) {
    this.isCapSeq = isCapSeq;
  }


  public String getIsMitSeq() {
    return isMitSeq;
  }


  public void setIsMitSeq(String isMitSeq) {
    this.isMitSeq = isMitSeq;
  }


  public String getIsFragAnal() {
    return isFragAnal;
  }


  public void setIsFragAnal(String isFragAnal) {
    this.isFragAnal = isFragAnal;
  }


  public String getIsCherryPick() {
    return isCherryPick;
  }


  public void setIsCherryPick(String isCherryPick) {
    this.isCherryPick = isCherryPick;
  }


  public String getLastThreeMonths() {
    return lastThreeMonths;
  }


  
  public void setLastThreeMonths(String lastThreeMonths) {
    this.lastThreeMonths = lastThreeMonths;
  }


  
  public String getShowExternalExperiments() {
    return showExternalExperiments;
  }


  
  public void setShowExternalExperiments(String showExternalExperiments) {
    this.showExternalExperiments = showExternalExperiments;
  }


  public String getIsExternalOnly() {
    return isExternalOnly;
  }


  public void setIsExternalOnly(String isExternalOnly) {
    this.isExternalOnly = isExternalOnly;
  }


  public String getIsNextGenSeq() {
    return isNextGenSeq;
  }


  public void setIsNextGenSeq(String isNextGenSeq) {
    this.isNextGenSeq = isNextGenSeq;
  }


  public String getAllExperiments() {
    return allExperiments;
  }


  public void setAllExperiments(String allExperiments) {
    this.allExperiments = allExperiments;
  }
  

  public String getShowEmptyProjectFolders() {
    return showEmptyProjectFolders;
  }

  public void setShowEmptyProjectFolders(String show) {
    showEmptyProjectFolders = show;
  }
  
  
}
