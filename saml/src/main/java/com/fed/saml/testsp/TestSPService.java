package com.fed.saml.testsp;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSPService extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(TestSPService.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	logger.info("In doGet() of TestSPService");
    	logger.info("Finally reached TestSP");
    	
    	// receive subject_id from session.
    	String subjectId = (String) req.getSession().getAttribute("subject_id");
    	
        resp.setContentType("text/html");
        resp.getWriter().append("<h1>*** SAML Authentication Successful ***</h1>");
        resp.getWriter().append("Hi <b>" + subjectId + "</b>, You are authenticated by SAML IdP and now at the requested resource at SP.");
    }
}
