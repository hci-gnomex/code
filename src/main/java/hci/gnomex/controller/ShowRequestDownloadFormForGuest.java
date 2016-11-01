package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.security.UnknownPermissionException;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.HibernateSession;
import hci.gnomex.utility.PropertyDictionaryHelper;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import org.apache.log4j.Logger;
public class ShowRequestDownloadFormForGuest extends GNomExCommand implements Serializable {

	private static Logger LOG = Logger.getLogger(ShowRequestDownloadFormForGuest.class);

	public String SUCCESS_JSP = "/getHTML.jsp";

	private Integer idRequest;
	private String requestNumbers;
	private String serverName;
	private String baseURL;

	private boolean createdSecurityAdvisor = false;
	private SecurityAdvisor secAdvisor = null;

	public void validate() {
	}

	public void loadCommand(HttpServletRequest request, HttpSession session) {

		if (request.getParameter("idRequest") != null) {
			idRequest = new Integer(request.getParameter("idRequest"));
		}

		if (request.getParameter("requestNumbers") != null) {
			requestNumbers = request.getParameter("requestNumbers");
		}

		if (idRequest == null && requestNumbers == null) {
			this.addInvalidField("requestNumbers or idRequest", "requestNumber or idRequest is required");
		}

		serverName = request.getServerName();

		baseURL = (request.isSecure() ? "https://" : "http://") + serverName + ":" + request.getServerPort() + request.getContextPath();

	}

	public Command execute() throws RollBackCommandException {
		Session sess = null;
		try {

			sess = HibernateSession.currentReadOnlySession(getUsername());

			// Get security advisor, create one hasn't already been created for this session.
			secAdvisor = this.getSecAdvisor();
			if (secAdvisor == null) {
				secAdvisor = SecurityAdvisor.createGuest();
				createdSecurityAdvisor = true;
			}

			// Get the experiment(s)
			List experiments = ShowRequestDownloadForm.getExperiments(sess, idRequest, requestNumbers);
			if (experiments == null || experiments.size() == 0) {
				this.addInvalidField("no experiment", "Request not found");
			}

			if (this.isValid()) {

				// Format an HTML page with links to download the files
				String baseDirFlowCell = PropertyDictionaryHelper.getInstance(sess).getDirectory(serverName, null, PropertyDictionaryHelper.PROPERTY_FLOWCELL_DIRECTORY);
				Document doc = ShowRequestDownloadForm.formatDownloadHTML(sess, secAdvisor, experiments, serverName, baseDirFlowCell, baseURL);

				XMLOutputter out = new org.jdom.output.XMLOutputter();
				out.setOmitEncoding(true);
				this.xmlResult = out.outputString(doc);
				this.xmlResult = this.xmlResult.replaceAll("&amp;", "&");
				this.xmlResult = this.xmlResult.replaceAll("�", "&micro");

			}

			if (isValid()) {
				setResponsePage(this.SUCCESS_JSP);
			} else {
				setResponsePage(this.ERROR_JSP);
			}

		} catch (UnknownPermissionException e) {
			LOG.error("An exception has occurred in ShowRequestDownloadFormForGuest ", e);

			throw new RollBackCommandException(e.getMessage());

		} catch (NamingException e) {
			LOG.error("An exception has occurred in ShowRequestDownloadFormForGuest ", e);

			throw new RollBackCommandException(e.getMessage());

		} catch (SQLException e) {
			LOG.error("An exception has occurred in ShowRequestDownloadFormForGuest ", e);

			throw new RollBackCommandException(e.getMessage());

		} catch (Exception e) {
			LOG.error("An exception has occurred in ShowRequestDownloadFormForGuest ", e);

			throw new RollBackCommandException(e.getMessage());
		} finally {
			try {
				if (sess != null) {
					//closeHibernateSession;
				}
			} catch (Exception e) {
				LOG.error("An exception has occurred in ShowRequestDownloadFormForGuest ", e);
			}
		}

		return this;
	}

	/**
	 * The callback method called after the loadCommand, and execute methods, this method allows you to manipulate the HttpServletResponse object prior to
	 * forwarding to the result JSP (add a cookie, etc.)
	 *
	 * @param request
	 *            The HttpServletResponse for the command
	 * @return The processed response
	 */
	public HttpServletResponse setResponseState(HttpServletResponse response) {
		return response;
	}

	/**
	 * The callback method called after the loadCommand and execute methods allowing you to do any post-execute processing of the HttpSession. Should be used to
	 * add/remove session data resulting from the execution of this command
	 *
	 * @param session
	 *            The HttpSession
	 * @return The processed HttpSession
	 */
	public HttpSession setSessionState(HttpSession session) {
		if (createdSecurityAdvisor) {
			session.setAttribute(SecurityAdvisor.SECURITY_ADVISOR_SESSION_KEY, secAdvisor);
		}
		return session;
	}

}