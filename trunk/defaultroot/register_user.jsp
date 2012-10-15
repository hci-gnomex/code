<%@ page import="hci.gnomex.utility.HibernateGuestSession" %>
<%@ page import="org.hibernate.Session" %>
<%@ page import="hci.gnomex.model.CoreFacility" %>
<%@ page import="hci.gnomex.model.Lab" %>
<%@ page import="hci.gnomex.model.PropertyDictionary" %>
<%@ page import="hci.gnomex.controller.GNomExFrontController" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<html>

<head>
  <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
  <link rel="stylesheet" href="css/login.css?v1.0" type="text/css" />
  <title>Create a new GNomEx Account</title>
  
<script  type="text/javascript" language="JavaScript">
  function setFocus()
  {
    theform.firstName.focus();
  }
  
  function showHideExternal()
  {
    if (document.theform.uofuAffiliate[0].checked)
    {
      document.getElementById("UofUDiv").style.display = "block";
      document.getElementById("externalDiv").style.display = "none";
      document.getElementById("institute").value = "";
      document.getElementById("userNameExternal").value = "";
      document.getElementById("passwordExternal").value = "";
    }
    else
    {
      document.getElementById("UofUDiv").style.display = "none";
      document.getElementById("externalDiv").style.display = "block";
      document.getElementById("uNID").value = "";
    }
  }
  
  function showNewLab()
  {
    document.getElementById("labDropdown").selectedIndex = 0;
    document.getElementById("newLabDiv").style.display = "block";  
  }
  
  function hideNewLab()
  {
    document.getElementById("newLabDiv").style.display = "none";  
  }
  
  
  function checkAlphaNumeric(e)
  {
     var KeyID = e.keyCode;
     if ((KeyID >= 8 && KeyID <= 9) || (KeyID >= 35 && KeyID <=39) || (KeyID == 46)){
        return;
     } 
     if (KeyID == 0) {
        KeyID = e.which;
     }
     if(KeyID<32||(KeyID>=33 && KeyID<=47 )||(KeyID>=58 && KeyID<=64 )||(KeyID>=91 && KeyID<=96)||( KeyID>122)) {
        return false;
     }
  }

<%
String message = (String) ((request.getAttribute("message") != null)?request.getAttribute("message"):"");
if (message == null) {
  message = "";
}

String firstName = (String) ((request.getParameter("firstName") != null)?request.getParameter("firstName"):"");
if (firstName == null) {
  firstName = "";
}

String lastName = (String) ((request.getParameter("lastName") != null)?request.getParameter("lastName"):"");
if (lastName == null) {
  lastName = "";
}

String email = (String) ((request.getParameter("email") != null)?request.getParameter("email"):"");
if (email == null) {
  email = "";
}

String phone = (String) ((request.getParameter("phone") != null)?request.getParameter("phone"):"");
if (phone == null) {
  phone = "";
}

String facilityRadio = (String) ((request.getParameter("facilityRadio") != null)?request.getParameter("facilityRadio"):"");
if (facilityRadio == null) {
  facilityRadio = "";
}

String labDropdown = (String) ((request.getParameter("labDropdown") != null)?request.getParameter("labDropdown"):"");
if (labDropdown == null) {
  labDropdown = "";
}

List labs = null;
List facilities = null;

// We can't obtain a hibernate session unless webcontextpath is initialized.  See HibernateSession.
String webContextPath = getServletConfig().getServletContext().getRealPath("/");
GNomExFrontController.setWebContextPath(webContextPath);

boolean showUserNameChoice = false;
Session sess = null;
try {
  sess = HibernateGuestSession.currentGuestSession("guest");
  PropertyDictionary propUniversityUserAuth = (PropertyDictionary)sess.createQuery("from PropertyDictionary p where p.propertyName='" + PropertyDictionary.UNIVERSITY_USER_AUTHENTICATION + "'").uniqueResult();
  if (propUniversityUserAuth != null && propUniversityUserAuth.getPropertyValue() != null && propUniversityUserAuth.getPropertyValue().equals("Y")) {
    showUserNameChoice = true;
  }  
  
  labs = sess.createQuery("from Lab l where l.isActive = 'Y' order by l.lastName, l.firstName").list();
  facilities = CoreFacility.getActiveCoreFacilities(sess);
  
} catch (Exception e){
  message = "Cannot obtain property " + PropertyDictionary.UNIVERSITY_USER_AUTHENTICATION + " " + e.toString() + " sess=" + sess;
} finally {
  try {
    HibernateGuestSession.closeGuestSession();
  } catch (Exception e) {
  }  
}

%>

</script>
</head>

<body onload="setFocus()">




<div id="content" align="center" bgcolor="white">

    <div class="header-bar" >
      <div class="rightMenu" >
          <a href="gnomexFlex.jsp">Login</a> | 
          <a href="change_password.jsp">Change password</a> |    
          <a href="reset_password.jsp">Reset password</a> |    
          <a href="register_user.jsp">Create a new account</a> 
      </div>
    </div>

    <form name="theform" method="POST" action="PublicSaveSelfRegisteredAppUser.gx" >

  <div class="boxWide">
    <h3>Create a new account</h3>

      <div class="col1"><div class="right">First name</div></div>
      <div class="col2"><input id="firstName" type="text" class="textWide" name="firstName" value="<%=firstName%>"  ></div>

      <div class="col1"><div class="right">Last name</div></div>
      <div class="col2"><input type="text" class="textWide" name="lastName" id="lastName" value="<%=lastName%>"  /></div>

      <div class="col1"><div class="right">Email</div></div>
      <div class="col2"><input type="text" class="textWide"   name="email" id="email" value="<%=email%>"  /></div>

      <div class="col1"><div class="right">Phone</div></div>
      <div class="col2"><input type="text" class="textWide" name="phone" id="phone" value="<%=phone%>"  /></div>
      
      <div class="empty"></div>
      <br>
      <div id="coreFacilityDiv"><div class="col1"><div class="left">
      <table border=0 width="425" class="facilities">
        <tr>
          <td colspan="2">
          Choose Core Facility
          </td>
        </tr>
        <%
          Iterator facilityIter = facilities.iterator();
          while (facilityIter.hasNext()) {
            CoreFacility facility = (CoreFacility) facilityIter.next();
        %>
        <tr>
          <td width="240">
          <label><input type="radio" name="facilityRadio" id="facilityRadio<%=facility.getIdCoreFacility()%>" value="<%=facility.getIdCoreFacility()%>"/> <%=facility.getFacilityName()%></label>
          </td>
          <td width="185">
            <%
              if (facility.getDescription() != null) {%>
                  <%=facility.getDescription()%>
            <%}%>
          </td>
        </tr> 
            <%}%>
      </table>
      </div></div>
      </div>       
      <div class="empty"></div>
      
      <div id="labDiv">
        <div class="col1"><div class="right">Choose Lab</div></div>
        <div class="col2"> 
            <select name="labDropdown" onchange="hideNewLab()" id="labDropdown" style="width:200" >
              <option value="0">  </option>
              <%
              Iterator i = labs.iterator();
              while (i.hasNext()) {
                Lab l = (Lab) i.next();
                String isSelected = "";
                if(labDropdown.length() > 0 && labDropdown.compareTo(""+l.getIdLab())==0) {
                  isSelected = "selected";
                }
              %>
                <option value="<%=l.getIdLab()%>" <%=isSelected%>><%=l.getName()%></option>
              <%}%>
            </select>
        <a class="button" onclick="showNewLab()">New lab...</a>
        </div>
      </div>
       
      <div id="newLabDiv" style="display:none;">   
        <div class="col1"><div class="right">Lab Name</div></div>
        <div class="col2"><input type="text" class="textWide"  name="newLab" onkeypress="return checkAlphaNumeric(event)"/></div>

        <div class="col1"><div class="right">Department</div></div>
        <div class="col2"><input type="text" class="textWide"  name="department" /></div>
      </div>

      

<% if (showUserNameChoice) { %>
    <div class="empty"></div>
    <div id="userChoiceDiv">
      <div class="col1Wide" ><div class="right"> Are you affiliated with the University of Utah?</div></div>
      <div class="col2"><INPUT TYPE="radio" id="uofuAffiliate_y" NAME="uofuAffiliate" VALUE="y" onClick="showHideExternal();">Yes</div>
      <div class="col2"><INPUT TYPE="radio" id="uofuAffiliate_n" NAME="uofuAffiliate" VALUE="n" onClick="showHideExternal();">No</div>
    </div>
<% }  %>
      <div class="emptySmall"></div>
      <div id="UofUDiv" style="display:none;width:100%;">
        <div id="univUserNameArea1" class="col1"><div class="right">University ID</div></div>
        <div id="univUserNameArea2" class="col2"><input type="text" class="textWide" name="uNID" id="uNID"></div>
        <div class="col1"><div class="right"> </div></div>
        <div class="col2"><note>Format should be a "u" followed by 7 digits (u0000000)</note></div>
      </div>

      <div id="externalDiv" style="display:none">
        <div class="col1"><div class="right">Institute</div></div>
        <div class="col2"><input type="text" class="textWide" name="institute" id="institute" /></div>
        
        <div id="externalUserNameArea1" class="col1"><div class="right">User name</div></div>
        <div id="externalUserNameArea2" class="col2"><input type="text" class="textWide" name="userNameExternal" id="userNameExternal"></div>

    
        <div id="externalPasswordArea1" class="col1"><div class="right">Password</div></div>
        <div id="externalPasswordArea2" class="col2"><input type="password" name="passwordExternal" id="passwordExternal" class="textWide"></div>
      </div>

      <div style="float:left;"><div class="message"> <strong><%= message %></strong></div></div>
      <div>   
          <div class="buttonPanel"><input type="submit" class="submit" value="Submit" /></div>
      </div>

  </div>

</div>
    <input type="hidden" name="responsePageSuccess" value="/register_user_success.jsp"/>
    <input type="hidden" name="responsePageError" value="/register_user.jsp"/>

<script  type="text/javascript" language="JavaScript">
<%
if (facilityRadio.length() > 0) {
%>
  document.getElementById("facilityRadio<%=facilityRadio%>").checked = true;
<%
}
if (facilities != null && facilities.size() > 1) {
%>
  document.getElementById("coreFacilities").style.display = "block";
<%
} else {
%>
  document.getElementById("coreFacilities").style.display = "none";
<%
}
%>
<%
if (!showUserNameChoice) {
%>
  document.getElementById("externalDiv").style.display = "block";
<%
}
%>
</script>
    </form>


</body>
</html>