package org.sms.project.login;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.PublicKey;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.opensaml.xml.util.Base64;
import org.opensaml.saml2.core.Artifact;
import org.opensaml.saml2.core.Response;
import org.sms.SysConstants;
import org.sms.component.idfactory.UUIDFactory;
import org.sms.organization.user.entity.User;
import org.sms.project.encrypt.rsa.RSACoder;
import org.sms.project.helper.SSOHelper;
import org.sms.project.helper.SessionHelper;
import org.sms.saml.service.SamlService;
import org.sms.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * @author Sunny
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {
  
  @Autowired
  private SamlService samlService;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
    HttpSession session = request.getSession();
    final Artifact idpArtifact = samlService.buildArtifact();
    Object url = session.getAttribute(SysConstants.ACTION_KEY);
    if (url == null) {
      url = SysConstants.DEFAULT_SP_RECEIVESPARTIFACT_URL;
    }
    final Response samlResponse = samlService.buildResponse(UUIDFactory.INSTANCE.getUUID());
    User user = (User) SessionHelper.get(request, SysConstants.LOGIN_USER);
    samlService.addAttribute(samlResponse, user);
    SSOHelper.INSTANCE.put(idpArtifact.getArtifact(), samlResponse);
    
    /**
     * 制作Ticket
     */
    String ticket = this.buildTicket(user);
    /**
     * 验证成功以后需要向客户端记录Cookie
     */
    String cookieTicket = encryptTicket(ticket);
    this.addSSOCookie(response, cookieTicket);
    response.sendRedirect(url.toString() + SysConstants.METHOD_SPILT_CHAR + SysConstants.ARTIFACT_KEY + SysConstants.PARAM_VALUE + samlService.buildXMLObjectToString(idpArtifact));
  }
  
  private String encryptTicket(String ticket) {
    PublicKey publicKey = samlService.getRSAPublicKey();
    try {
      byte[] encryptArray = RSACoder.INSTANCE.encryptByPublicKey(publicKey, ticket.getBytes());
      return Base64.encodeBytes(encryptArray);
    } catch (Exception e) {
      throw new RuntimeException("加密数据错误");
    }
  }
  
  private String buildTicket(User user) {
    StringBuilder ticketBuilder = new StringBuilder();
    ticketBuilder.append(user.getId());
    ticketBuilder.append(SysConstants.TICKET_SPILT);
    ticketBuilder.append(user.getEmail());
    ticketBuilder.append(SysConstants.TICKET_SPILT);
    ticketBuilder.append(DateUtil.getSpecifiedDayAfter(SysConstants.DEFAULT_EXPIRE));
    return ticketBuilder.toString();
  }
  
  private void addSSOCookie(HttpServletResponse response, String string) throws UnsupportedEncodingException {
    Cookie cookie = new Cookie(SysConstants.IDP_TICKET, URLEncoder.encode(string, SysConstants.CHARSET));
    cookie.setDomain(".soaer.com");
    cookie.setPath("/");
    cookie.setMaxAge(24 * 60 * 60);
    response.addCookie(cookie);
  }
}