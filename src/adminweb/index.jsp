<%@ page pageEncoding="ISO-8859-1"%>
<%@page errorPage="errorpage.jsp"  import="se.anatom.ejbca.webdist.webconfiguration.EjbcaWebBean,se.anatom.ejbca.ra.raadmin.GlobalConfiguration, se.anatom.ejbca.webdist.webconfiguration.WebLanguages"%>
<html>
<jsp:useBean id="ejbcawebbean" scope="session" class="se.anatom.ejbca.webdist.webconfiguration.EjbcaWebBean" />
<jsp:setProperty name="ejbcawebbean" property="*" /> 
<%   // Initialize environment
  GlobalConfiguration globalconfiguration = ejbcawebbean.initialize(request,"/administrator"); 
%>
<head>
  <title><%= globalconfiguration.getEjbcaTitle() %></title>
  <base href="<%= ejbcawebbean.getBaseUrl() %>">

  <link rel=STYLESHEET href="<%= ejbcawebbean.getCssFile() %>">
</head>

<frameset rows="131,*" cols="*" frameborder="NO" border="0" framespacing="0"> 
  <frame name="<%= globalconfiguration.HEADERFRAME %>" scrolling="NO" noresize src="<%= globalconfiguration.getHeadBanner() %>" >
  <frameset cols="217,*" frameborder="NO" border="0" framespacing="0" rows="*"> 
    <frame name="<%= globalconfiguration.MENUFRAME %>" noresize scrolling="NO" src="<%= globalconfiguration.getAdminWebPath() +
                                                                                        globalconfiguration.getMenuFilename() %>">
    <frame name="<%= globalconfiguration.MAINFRAME %>" src="<%= globalconfiguration.getAdminWebPath() + globalconfiguration.getMainFilename() %>">
  </frameset>
</frameset>
<noframes>
<body>
  <h1><%= ejbcawebbean.getText("ERRORNOBROWSER") %></h1>
</body>
</noframes>
</html>
