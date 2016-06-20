package com.fed.saml.sp.protocol.authn.handlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.bouncycastle.util.encoders.Base64;
import org.opensaml.Configuration;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.EncryptedAssertion;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.encryption.Decrypter;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.encryption.DecryptionException;
import org.opensaml.xml.encryption.InlineEncryptedKeyResolver;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.fed.saml.sp.protocol.utils.Constants;
import com.fed.saml.sp.protocol.utils.Credentials;
import com.fed.saml.sp.protocol.utils.OpenSAMLUtils;

public class SAMLAssertion {
	private static Logger logger = LoggerFactory.getLogger(SAMLAssertion.class);

	private Response response;

	public SAMLAssertion(Response response) {
		this.response = response;
	}

	public Assertion getAssertionFromResponse() {
    	Assertion assertion = null;
		List<EncryptedAssertion> encryptedAssertionList = response.getEncryptedAssertions();
		if (!encryptedAssertionList.isEmpty()) {

			// decrypt and check integrity of ArtifactResponse
			EncryptedAssertion encryptedAssertion = getEncryptedAssertion(response);
			assertion = decryptAssertion(encryptedAssertion);
			verifyAssertionSignature(assertion);
			logger.info("Decrypted Assertion: ");
			OpenSAMLUtils.logSAMLObject(assertion);

			// print saml message attributes
			logAuthenticationInstant(assertion);
			logAuthenticationMethod(assertion);
			logSAMLAttributes(assertion);
		} else {
			List<Assertion> assertionList = response.getAssertions();
			if (!assertionList.isEmpty()) {
				// decrypt and check integrity of ArtifactResponse
				verifyAssertionSignature(assertionList.get(0));
				logger.info("Decrypted Assertion: ");
				OpenSAMLUtils.logSAMLObject(assertionList.get(0));

				// print saml message attributes
				logAuthenticationInstant(assertionList.get(0));
				logAuthenticationMethod(assertionList.get(0));
				logSAMLAttributes(assertionList.get(0));
			}
		}
		return assertion;
    }
	
	private EncryptedAssertion getEncryptedAssertion(Response response) {
        //Response response = (Response)artifactResponse.getMessage();
        return response.getEncryptedAssertions().get(0);
    }
    
	private Assertion decryptAssertion(EncryptedAssertion encryptedAssertion) {
    	Assertion decryptedAssertion = null;
	    if(encryptedAssertion != null) {
	        StaticKeyInfoCredentialResolver keyInfoCredentialResolver = new StaticKeyInfoCredentialResolver(Credentials.getSPCredential(Constants.SP_KEY_ALIAS));
	
	        Decrypter decrypter = new Decrypter(null, keyInfoCredentialResolver, new InlineEncryptedKeyResolver());
	        decrypter.setRootInNewDocument(true);
	
	        try {
	            return decrypter.decrypt(encryptedAssertion);
	        } catch (DecryptionException e) {
	            throw new RuntimeException(e);
	        }
    	}
    	return decryptedAssertion;
    }
	
	private void verifyAssertionSignature(Assertion assertion) {
    	if(assertion != null) {
    		if (!assertion.isSigned()) {
    			throw new RuntimeException("The SAML Assertion was not signed");
    		}

	        try {
	            SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
	            profileValidator.validate(assertion.getSignature());
	
	            SignatureValidator sigValidator = new SignatureValidator(Credentials.getIdPCredential(Constants.IDP_KEY_ALIAS));
	
	            sigValidator.validate(assertion.getSignature());
	
	            logger.info("SAML Assertion signature verified");
	        } catch (ValidationException e) {
	        	e.printStackTrace();
	        	logger.error(e.getMessage());
	            throw new RuntimeException(e);
	        }
    	}
    }
	
	private void logAuthenticationInstant(Assertion assertion) {
        if (assertion != null && assertion.getAuthnStatements() != null) {
        	logger.info("Authentication instant: " + assertion.getAuthnStatements().get(0).getAuthnInstant());
        	//sessionIndex = assertion.getAuthnStatements().get(0).getSessionIndex();
        }
    }

	private void logAuthenticationMethod(Assertion assertion) {
        if (assertion != null && assertion.getAuthnStatements() != null) {
        	logger.info("Authentication method: " + assertion.getAuthnStatements().get(0)
                .getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef());
        }
    }
    
	private void logSAMLAttributes(Assertion assertion) {
        Map<String, String> results = new HashMap<String, String>();
        if (assertion != null && assertion.getAttributeStatements() != null) {

            List<AttributeStatement> attributeStatementList = assertion.getAttributeStatements();
            
            for (AttributeStatement statement : attributeStatementList) {
                List<Attribute> attributesList = statement.getAttributes();
                for (Attribute attribute : attributesList) {
                    Element value = attribute.getAttributeValues().get(0).getDOM();
                    String attributeValue = value.getTextContent();
                    results.put(attribute.getFriendlyName(), attributeValue);
                }
            }

        }
        logger.info("SAML Attributes: " + results);
    }
}
