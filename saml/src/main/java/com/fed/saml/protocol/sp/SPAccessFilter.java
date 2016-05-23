package com.fed.saml.protocol.sp;

import java.io.IOException;
import java.security.Provider;
import java.security.Security;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The filter intercepts the user and start the SAML authentication if it is not authenticated
 */
public class SPAccessFilter implements Filter {
    private static Logger logger = LoggerFactory.getLogger(SPAccessFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Configuration.validateJCEProviders();
        Configuration.validateNonSunJAXP();
        
        for (Provider jceProvider : Security.getProviders()) {
            logger.info(jceProvider.getInfo());
        }

        try {
            logger.info("Bootstrapping in init() of SPAccessFilter");
            DefaultBootstrap.bootstrap();
        } catch (ConfigurationException e) {
            throw new RuntimeException("Bootstrapping failed");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest)request;
        HttpServletResponse httpServletResponse = (HttpServletResponse)response;
        logger.info("In doFilter() of SPAccessFilter");
        if (httpServletRequest.getSession().getAttribute(SPConstants.AUTHENTICATED_SESSION_ATTRIBUTE) != null) {
        	logger.info("SAML User Authentication is done");
            chain.doFilter(request, response);
        } else {
        	logger.info("Have to do SAML user authentication");
        	logger.info("Redirect to AuthnRequestService");
        	setRequestedResourceInSession(httpServletRequest);
            httpServletResponse.sendRedirect("sp/authnrequestservice");
        }
        return;
    }
    
    // to prevent infinite looping between filter and 'sp/authnrequestservice'
    private void setRequestedResourceInSession(HttpServletRequest request) {
        request.getSession().setAttribute(SPConstants.REQUESTED_RESOURCE_SESSION_ATTRIBUTE, request.getRequestURL().toString());
    }
    
    @Override
    public void destroy() {

    }
}