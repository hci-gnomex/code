package hci.gnomex.model;



import hci.hibernate3utils.HibernateDetailObject;



public class AnalysisExperimentItem extends HibernateDetailObject {
  
  private Integer        idAnalysisExperimentItem;
  private Integer        idAnalysis;
  private Analysis       analysis;
  private Hybridization  hybridization;
  private SequenceLane   sequenceLane;
  private Integer        idHybridization;
  private Integer        idSequenceLane;
  
  public Integer getIdAnalysisExperimentItem() {
    return idAnalysisExperimentItem;
  }
  
  public void setIdAnalysisExperimentItem(Integer idAnalysisExperimentItem) {
    this.idAnalysisExperimentItem = idAnalysisExperimentItem;
  }
  
  public Integer getIdAnalysis() {
    return idAnalysis;
  }
  
  public void setIdAnalysis(Integer idAnalysis) {
    this.idAnalysis = idAnalysis;
  }
  
  public Analysis getAnalysis() {
    return analysis;
  }
  
  public void setAnalysis(Analysis analysis) {
    this.analysis = analysis;
  }
  
  public Hybridization getHybridization() {
    return hybridization;
  }
  
  public void setHybridization(Hybridization hybridization) {
    this.hybridization = hybridization;
  }
  
  public SequenceLane getSequenceLane() {
    return sequenceLane;
  }
  
  public void setSequenceLane(SequenceLane sequenceLane) {
    this.sequenceLane = sequenceLane;
  }
  
  public void registerMethodsToExcludeFromXML() {
    this.excludeMethodFromXML("getAnalysis");
  }

  
  public Integer getIdHybridization() {
    return idHybridization;
  }

  
  public void setIdHybridization(Integer idHybridization) {
    this.idHybridization = idHybridization;
  }

  
  public Integer getIdSequenceLane() {
    return idSequenceLane;
  }

  
  public void setIdSequenceLane(Integer idSequenceLane) {
    this.idSequenceLane = idSequenceLane;
  }
  
 
}