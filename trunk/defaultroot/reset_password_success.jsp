<html>

<head>
	<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
	<link rel="stylesheet" href="css/login.css" type="text/css" />
	<title>Reset your Password</title>
</head>

<%
String message = (String) ((request.getAttribute("message") != null)?request.getAttribute("message"):"");

//Set Cache-Control to no-cache.
response.setHeader("Cache-Control", "max-age=0, must-revalidate");

session.removeAttribute("j_username");
session.removeAttribute("j_password");
session.removeAttribute("User");
session.removeAttribute("user");
session.removeAttribute("username");
session.removeAttribute("gnomexSecurityAdvisor");
session.removeAttribute("logined"); 
session.removeAttribute("context"); 

session.invalidate();
   
%>

<body>


<div id="content" align="center" bgcolor="white">
<div class="header-bar" >
  <div class="rightMenu" >
      <a href="gnomexFlex.jsp">Sign in</a> |    
      <a href="change_password.jsp">Change password</a> |    
      <a href="select_core.jsp">Sign up for an account</a> 
  </div>
</div>

 <div class="containerMessage">
    <h3>Password reset.</h3>
    Your new password has been emailed to you.
 </div> 
</div>
</body>
</html>