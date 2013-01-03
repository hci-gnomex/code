package hci.gnomex.model;

import hci.dictionary.model.DictionaryEntry;
import hci.dictionary.model.NullDictionaryEntry;
import hci.dictionary.utility.DictionaryManager;

import java.io.Serializable;



public class PropertyDictionary extends DictionaryEntry implements Serializable {
  
  public static final String        CORE_FACILITY_NAME                  = "core_facility_name";
  public static final String        CONTACT_EMAIL_CORE_FACILITY         = "contact_email_core_facility";
  public static final String        CONTACT_EMAIL_BIOINFORMATICS        = "contact_email_bioinformatics";
  public static final String        CONTACT_NAME_CORE_FACILITY          = "contact_name_core_facility";
  public static final String        CONTACT_PHONE_CORE_FACILITY         = "contact_phone_core_facility";
  public static final String        CONTACT_EMAIL_SOFTWARE_TESTER       = "contact_email_software_tester";
  public static final String        CONTACT_EMAIL_SOFTWARE_BUGS         = "contact_email_software_bugs";
  public static final String        CONTACT_EMAIL_CORE_FACILITY_WORKAUTH = "contact_email_core_facility_workauth";
  public static final String        CONTACT_EMAIL_CORE_FACILITY_WORKAUTH_REMINDER = "contact_email_core_facility_workauth_reminder";
  public static final String        REPLY_EMAIL_CORE_FACILITY_WORKAUTH_REMINDER = "reply_email_core_facility_workauth_reminder";
  public static final String        FLOWCELL_DIRECTORY_FLAG             = "flowcell_directory_flag";
  public static final String        LAST_UPLOAD_NOTIFICATION            = "last_upload_notification";
  public static final String        QC_DIRECTORY                        = "qc_directory";
  public static final String        LUCENE_INDEX_DIRECTORY              = "lucene_index_directory";
  public static final String        LUCENE_EXPERIMENT_INDEX_DIRECTORY   = "lucene_experiment_index_directory";
  public static final String        LUCENE_PROTOCOL_INDEX_DIRECTORY     = "lucene_protocol_index_directory";
  public static final String        LUCENE_ANALYSIS_INDEX_DIRECTORY     = "lucene_analysis_index_directory";
  public static final String        LUCENE_DATATRACK_INDEX_DIRECTORY    = "lucene_datatrack_index_directory";
  public static final String        LUCENE_TOPIC_INDEX_DIRECTORY        = "lucene_topic_index_directory";
  public static final String        LUCENE_GLOBAL_INDEX_DIRECTORY       = "lucene_global_index_directory";
  public static final String        BILLING_CORE_FACILITY_BUSINESS_UNIT = "billing_core_facility_business_unit"; 
  public static final String        BILLING_CORE_FACILITY_ACCOUNT       = "billing_core_facility_account";
  public static final String        BILLING_CORE_FACILITY_PO_ACCOUNT    = "billing_core_facility_po_account";
  public static final String        BILLING_CORE_FACILITY_FUND          = "billing_core_facility_fund";
  public static final String        BILLING_CORE_FACILITY_ORG           = "billing_core_facility_org";
  public static final String        BILLING_CORE_FACILITY_ACTIVITY      = "billing_core_facility_activity";
  public static final String        BILLING_PO_ACCOUNT                  = "billing_po_account";
  public static final String        BILLING_GL_JOURNAL_ID_CORE_FACILITY = "billing_gl_journal_id_core_facility";
  public static final String        BILLING_GL_JOURNAL_LINE_REF_CORE_FACILITY = "billing_gl_journal_line_ref_core_facility";
  public static final String        DATASET_PRIVACY_EXPIRATION          = "dataset_privacy_expiration";
  public static final String        DATASET_PRIVACY_EXPIRATION_WARNING  = "dataset_privacy_expiration_warning";
  public static final String        DATASET_PRIVACY_EXPIRATION_REPLY_EMAIL  = "dataset_privacy_expiration_reply_email";
  public static final String        EXPERIMENT_SUBMISSION_DEFAULT_MODE  = "experiment_submission_default_mode";
  public static final String        TEMP_DIRECTORY                      = "temp_directory";
  public static final String        BST_LINKAGE_SUPPORTED               = "bst_linkage_supported";  
  public static final String        BST_LINKAGE_GNOMEX_URL              = "bst_linkage_gnomex_url";
  public static final String        FDT_SUPPORTED                       = "fdt_supported";
  public static final String        HTTP_PORT                           = "http_port";
  public static final String        FDT_USER                            = "fdt_user";
  public static final String        FDT_GROUP                           = "fdt_group";
  public static final String        UNIVERSITY_USER_AUTHENTICATION      = "university_user_authentication"; 
  public static final String        USAGE_USER_VISIBILITY               = "usage_user_visibility";
  public static final String        EXTERNAL_DATA_SHARING_SITE          = "external_data_sharing_site";
  public static final String        SITE_TITLE                          = "site_title";
  public static final String        SITE_WELCOME_MESSAGE                = "site_welcome_message";
  public static final String        SHOW_USAGE_ON_STARTUP               = "show_usage_on_startup";
  public static final String        SEQUENCE_ALIGNMENT_SUPPORTED        = "sequence_alignment_supported";      
  public static final String        SEQUENCE_ALIGNMENT_SERVER_URL       = "sequence_alignment_server_url";      
  public static final String        TOPICS_SUPPORTED                    = "topics_supported";
  public static final String        DATATRACK_FILESERVER_URL            = "datatrack_fileserver_url";
  public static final String        DATATRACK_FILESERVER_WEB_CONTEXT    = "datatrack_fileserver_web_context";
  public static final String        DEFAULT_VISIBILITY_EXPERIMENT       = "default_visibility_experiment";
  public static final String        DEFAULT_VISIBILITY_ANALYSIS         = "default_visibility_analysis";
  public static final String        DEFAULT_VISIBILITY_DATATRACK        = "default_visibility_datatrack";
  public static final String        DEFAULT_VISIBILITY_TOPIC            = "default_visibility_topic";
  public static final String        GENERIC_NO_REPLY_EMAIL              = "generic_no_reply_email";
  public static final String        PERFORMANCE_LOG_ENABLED             = "performance_log_enabled";
  public static final String        PERFORMANCE_LOG_CUTOFF_MILLISECONDS = "performance_log_cutoff_milliseconds";
  public static final String        EXPERIMENT_DEFAULT_VIEW             = "experiment_default_view";
  public static final String        EXPERIMENT_VIEW_LIMIT               = "experiment_view_limit";
  public static final String        GET_REQUEST_NUMBER_PROCEDURE        = "get_request_number_procedure";
  public static final String        ID_DEFAULT_INSTITUTION              = "id_default_institution";
  public static final String        SITE_LOGO                           = "site_logo";
  public static final String        CONFIGURABLE_BILLING_ACCOUNTS       = "configurable_billing_accounts";
  public static final String        INVOICE_NOTE_1                      = "invoice_note_1";
  public static final String        INVOICE_NOTE_2                      = "invoice_note_2";
  public static final String        DISK_USAGE_FOR_EXPERIMENTS          = "disk_usage_for_experiments";
  public static final String        DISK_USAGE_FOR_ANALYSIS             = "disk_usage_for_analysis";
  public static final String        DISK_USAGE_INCREMENT                = "disk_usage_increment";
  public static final String        DISK_USAGE_MONTHLY_CHARGE_PER_GB    = "disk_usage_monthly_charge_per_gb";
  public static final String        DISK_USAGE_FREE_PER_INCREMENT       = "disk_usage_free_per_increment";
  public static final String        DISK_USAGE_FREE_GB                  = "disk_usage_free_gb";
  public static final String        DISK_USAGE_ANALYSIS_GRACE_PERIOD_IN_MONTHS = "disk_usage_analysis_grace_period_in_months";
  public static final String        DISK_USAGE_EXPERIMENT_GRACE_PERIOD_IN_MONTHS = "disk_usage_experiment_grace_period_in_months";
  public static final String        DISK_USAGE_PRICE_CATEGORY_NAME      = "disk_usage_price_category_name";

  public static final String        OPTION_USER_USER_VISIBILITY_NONE    = "none";
  public static final String        OPTION_USER_USER_VISIBILITY_MASKED  = "masked";
  public static final String        OPTION_USER_USER_VISIBILITY_FULL    = "full";


  private Integer       idPropertyDictionary;
  private String        propertyName;
  private String        propertyValue;
  private String        propertyDescription;
  private String        forServerOnly;
  private Integer       idCoreFacility;

  public Integer getIdPropertyDictionary() {
    return idPropertyDictionary;
  }
  
  public void setIdPropertyDictionary(Integer idPropertyDictionary) {
    this.idPropertyDictionary = idPropertyDictionary;
  }
  
  public String getValue() {
    return idPropertyDictionary.toString();
  }
 
  public String getDisplay() {
    String postfix = "";
    if (getIdCoreFacility() != null) {
      postfix = DictionaryManager.getDisplay("hci.gnomex.model.CoreFacility", getIdCoreFacility().toString());
      if (postfix == null) {
        postfix = "";
      } else {
        postfix = "(" + postfix + ")";
      }
    }
    return getPropertyName() + postfix;
  }

  
  public String getPropertyName() {
    return propertyName;
  }

  
  public void setPropertyName(String propertyName) {
    this.propertyName = propertyName;
  }

  
  public String getPropertyValue() {
    return propertyValue;
  }

  
  public void setPropertyValue(String propertyValue) {
    this.propertyValue = propertyValue;
  }

  
  public String getPropertyDescription() {
    return propertyDescription;
  }

  
  public void setPropertyDescription(String propertyDescription) {
    this.propertyDescription = propertyDescription;
  }

  
  public String getForServerOnly() {
    return forServerOnly;
  }

  
  public void setForServerOnly(String forServerOnly) {
    this.forServerOnly = forServerOnly;
  }

  public Integer getIdCoreFacility() {
    return idCoreFacility;
  }
  
  public void setIdCoreFacility(Integer id) {
    idCoreFacility = id;
  }
}