package hci.gnomex.controller;

import hci.gnomex.constants.Constants;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.utility.HibernateSession;
import hci.gnomex.utility.PropertyDictionaryHelper;
import hci.gnomex.utility.ServletUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.hibernate.Session;

public class UploadSequenceURLServlet extends HttpServlet {
private static Logger LOG = Logger.getLogger(UploadSequenceURLServlet.class);

protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

	// Restrict commands to local host if request is not secure
	if (!ServletUtil.checkSecureRequest(req)) {
		ServletUtil.reportServletError(res, "Secure connection is required. Prefix your request with 'https'");
		return;
	}

	Session sess = null;

	try {

		//
		// COMMENTED OUT CODE:
		// boolean isLocalHost = req.getServerName().equalsIgnoreCase("localhost") || req.getServerName().equals("127.0.0.1");
		// String baseURL = "http"+ (isLocalHost ? "://" : "s://") + req.getServerName() + req.getContextPath();
		//
		// To fix upload problem (missing session in upload servlet for FireFox, Safari), encode session in URL
		// for upload servlet. Also, use non-secure (http: rather than https:) when making http request;
		// otherwise, existing session is not accessible to upload servlet.
		//
		//

		sess = HibernateSession.currentReadOnlySession(req.getUserPrincipal().getName());
		String portNumber = PropertyDictionaryHelper.getInstance(sess).getQualifiedProperty(
				PropertyDictionary.HTTP_PORT, req.getServerName());
		if (portNumber == null) {
			portNumber = "";
		} else {
			portNumber = ":" + portNumber;
		}

		String baseURL = "http" + "://" + req.getServerName() + portNumber + req.getContextPath();
		String URL = baseURL + "/UploadSequenceFileServlet.gx";
		// Encode session id in URL so that session maintains for upload servlet when called from
		// Flex upload component inside FireFox, Safari
		URL += ";jsessionid=" + req.getRequestedSessionId();

		// Get the valid file extensions
		StringBuffer fileExtensions = new StringBuffer();
		for (int x = 0; x < Constants.SEQUENCE_FILE_EXTENSIONS.length; x++) {
			if (fileExtensions.length() > 0) {
				fileExtensions.append(";");
			}
			fileExtensions.append("*" + Constants.SEQUENCE_FILE_EXTENSIONS[x]);
		}

		res.setContentType("application/xml");
		res.getOutputStream().println(
				"<UploadURL url='" + URL + "'" + " fileExtensions='" + fileExtensions.toString() + "'" + "/>");

	} catch (Exception e) {
		LOG.error("Error in UploadSequenceURLServlet", e);

	} finally {
		if (sess != null) {
			try {
				HibernateSession.closeSession();
			} catch (Exception e) {
				LOG.error("Error in UploadSequenceURLServlet", e);
			}
		}
	}
}
}
