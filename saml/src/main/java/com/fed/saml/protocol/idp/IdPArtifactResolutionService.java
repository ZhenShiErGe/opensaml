package com.fed.saml.protocol.idp;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.xml.security.utils.EncryptionConstants;
import org.joda.time.DateTime;
import org.opensaml.common.SAMLObject;
import org.opensaml.saml2.core.ArtifactResolve;
import org.opensaml.saml2.core.ArtifactResponse;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AttributeValue;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.AuthnContext;
import org.opensaml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.EncryptedAssertion;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.NameIDType;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Status;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml2.encryption.Encrypter;
import org.opensaml.ws.soap.soap11.Body;
import org.opensaml.ws.soap.soap11.Envelope;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.encryption.EncryptionException;
import org.opensaml.xml.encryption.EncryptionParameters;
import org.opensaml.xml.encryption.KeyEncryptionParameters;
import org.opensaml.xml.io.Marshaller;
import org.opensaml.xml.io.MarshallerFactory;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.parse.XMLParserException;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.schema.impl.XSStringBuilder;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureConstants;
import org.opensaml.xml.signature.SignatureException;
import org.opensaml.xml.signature.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fed.saml.protocol.sp.SPCredentials;
import com.fed.saml.protocol.utils.OpenSAMLUtils;

/**
 * Created by Privat on 4/6/14.
 */
public class IdPArtifactResolutionService extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(IdPArtifactResolutionService.class);

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
    	logger.info("In doPost() of IdPArtifactResolutionService");
    	logger.info("Got ArtifactResolve request via SOAP from SPAssertionConsumerService");
    	
    	// validate the ArtifactResolve request
    	
    	// check the SAML artifact which is equal to one sent by IdP
    	ArtifactResolve artifactResolve = unmarshallArtifactResolve(req.getInputStream());
        
        String sentSamlArtifact = OpenSAMLUtils.getSAML2ArtifactType0004(IdPConstants.ENDPOINT_INDEX, IdPConstants.SOURCE_ID, IdPConstants.MSG_HANDLE);
        String receivedArtifact = artifactResolve.getArtifact().getArtifact();
        
    	if (sentSamlArtifact.equals(receivedArtifact)) {
    		logger.info("Received artifact is equal to artifact sent by IdP");
    	
    		ArtifactResponse artifactResponse = buildArtifactResponse();
			artifactResponse.setInResponseTo(artifactResolve.getID());
	
	        // SOAP reply
	        printSAMLObject(wrapInSOAPEnvelope(artifactResponse), resp.getWriter());
	    	logger.info("Replied ArtifactResponse via SOAP to SPAssertionConsumerService");
    	} else {
    		logger.info("Received artifact is NOT equal to artifact sent by IdP");
    		ArtifactResponse artifactErrorResponse = buildErrorArtifactResponse();
    		artifactErrorResponse.setInResponseTo(artifactResolve.getID());
	
    		// SOAP reply
	        printSAMLObject(wrapInSOAPEnvelope(artifactErrorResponse), resp.getWriter());
    		logger.info("Replied Error ArtifactResponse via SOAP to SPAssertionConsumerService");
    	}
    }

    public static ArtifactResolve unmarshallArtifactResolve(final InputStream input) {
        try {
            BasicParserPool ppMgr = new BasicParserPool();
            ppMgr.setNamespaceAware(true);

            Document soap = ppMgr.parse(input);

            Element soapRoot = soap.getDocumentElement();

            UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(soapRoot);

            Envelope soapEnvelope = (Envelope)unmarshaller.unmarshall(soapRoot);

            return (ArtifactResolve)soapEnvelope.getBody().getUnknownXMLObjects().get(0);
        } catch (XMLParserException e) {
            throw new RuntimeException(e);
        } catch (UnmarshallingException e) {
            throw new RuntimeException(e);
        }

    }

    public static org.w3c.dom.Element marshallSAMLObject(final SAMLObject object) {
        org.w3c.dom.Element element = null;
        try {
            MarshallerFactory unMarshallerFactory = Configuration.getMarshallerFactory();

            Marshaller marshaller = unMarshallerFactory.getMarshaller(object);

            element = marshaller.marshall(object);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("The class does not implement the interface XMLObject", e);
        } catch (MarshallingException e) {
            throw new RuntimeException(e);
        }

        return element;
    }
    
    private ArtifactResponse buildArtifactResponse() {
    	logger.info("In buildArtifactResponse() of IdPArtifactResolutionService");

        ArtifactResponse artifactResponse = OpenSAMLUtils.buildSAMLObject(ArtifactResponse.class);

        Issuer issuer = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer.setValue(IdPConstants.IDP_ENTITY_ID);
        artifactResponse.setIssuer(issuer);
        artifactResponse.setIssueInstant(new DateTime());
        artifactResponse.setDestination(IdPConstants.ASSERTION_CONSUMER_SERVICE);

        artifactResponse.setID(OpenSAMLUtils.generateSecureRandomId());

        Status status = OpenSAMLUtils.buildSAMLObject(Status.class);
        StatusCode statusCode = OpenSAMLUtils.buildSAMLObject(StatusCode.class);
        statusCode.setValue(StatusCode.SUCCESS_URI);
        status.setStatusCode(statusCode);
        artifactResponse.setStatus(status);

        Response response = OpenSAMLUtils.buildSAMLObject(Response.class);
        response.setDestination(IdPConstants.ASSERTION_CONSUMER_SERVICE);
        response.setIssueInstant(new DateTime());
        response.setID(OpenSAMLUtils.generateSecureRandomId());
        Issuer issuer2 = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer2.setValue(IdPConstants.IDP_ENTITY_ID);

        response.setIssuer(issuer2);

        Status status2 = OpenSAMLUtils.buildSAMLObject(Status.class);
        StatusCode statusCode2 = OpenSAMLUtils.buildSAMLObject(StatusCode.class);
        statusCode2.setValue(StatusCode.SUCCESS_URI);
        status2.setStatusCode(statusCode2);

        response.setStatus(status2);

        artifactResponse.setMessage(response);

        Assertion assertion = buildAssertion();

        signAssertion(assertion);
        EncryptedAssertion encryptedAssertion = encryptAssertion(assertion);

        response.getEncryptedAssertions().add(encryptedAssertion);
        return artifactResponse;
    }

    private ArtifactResponse buildErrorArtifactResponse() {
    	logger.info("In buildErrorArtifactResponse() of IdPArtifactResolutionService");

        ArtifactResponse artifactResponse = OpenSAMLUtils.buildSAMLObject(ArtifactResponse.class);

        Issuer issuer = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer.setValue(IdPConstants.IDP_ENTITY_ID);
        artifactResponse.setIssuer(issuer);
        artifactResponse.setIssueInstant(new DateTime());
        artifactResponse.setDestination(IdPConstants.ASSERTION_CONSUMER_SERVICE);

        artifactResponse.setID(OpenSAMLUtils.generateSecureRandomId());

        Status status = OpenSAMLUtils.buildSAMLObject(Status.class);
        StatusCode statusCode = OpenSAMLUtils.buildSAMLObject(StatusCode.class);
        statusCode.setValue(StatusCode.REQUESTER_URI);
        status.setStatusCode(statusCode);
        artifactResponse.setStatus(status);

        Response response = OpenSAMLUtils.buildSAMLObject(Response.class);
        response.setDestination(IdPConstants.ASSERTION_CONSUMER_SERVICE);
        response.setIssueInstant(new DateTime());
        response.setID(OpenSAMLUtils.generateSecureRandomId());
        Issuer issuer2 = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer2.setValue(IdPConstants.IDP_ENTITY_ID);

        response.setIssuer(issuer2);

        Status status2 = OpenSAMLUtils.buildSAMLObject(Status.class);
        StatusCode statusCode2 = OpenSAMLUtils.buildSAMLObject(StatusCode.class);
        statusCode2.setValue(StatusCode.REQUEST_UNSUPPORTED_URI);
        status2.setStatusCode(statusCode2);

        response.setStatus(status2);

        artifactResponse.setMessage(response);

        //Assertion assertion = buildAssertion();

        //signAssertion(assertion);
        //EncryptedAssertion encryptedAssertion = encryptAssertion(assertion);

        //response.getEncryptedAssertions().add(encryptedAssertion);
        return artifactResponse;
    }

    
    private EncryptedAssertion encryptAssertion(Assertion assertion) {
        EncryptionParameters encryptionParameters = new EncryptionParameters();
        encryptionParameters.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES128);

        KeyEncryptionParameters keyEncryptionParameters = new KeyEncryptionParameters();
        keyEncryptionParameters.setEncryptionCredential(SPCredentials.getSPCredential(""));
        keyEncryptionParameters.setAlgorithm(EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP);

        Encrypter encrypter = new Encrypter(encryptionParameters, keyEncryptionParameters);
        encrypter.setKeyPlacement(Encrypter.KeyPlacement.INLINE);

        try {
            EncryptedAssertion encryptedAssertion = encrypter.encrypt(assertion);
            return encryptedAssertion;
        } catch (EncryptionException e) {
            throw new RuntimeException(e);
        }
    }

    private void signAssertion(Assertion assertion) {
        Signature signature = OpenSAMLUtils.buildSAMLObject(Signature.class);
        signature.setSigningCredential(IdPCredentials.getCredential());
        signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1);
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        assertion.setSignature(signature);

        try {
            Configuration.getMarshallerFactory().getMarshaller(assertion).marshall(assertion);
        } catch (MarshallingException e) {
            throw new RuntimeException(e);
        }

        try {
            Signer.signObject(signature);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    private Assertion buildAssertion() {

        Assertion assertion = OpenSAMLUtils.buildSAMLObject(Assertion.class);

        Issuer issuer = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer.setValue(IdPConstants.IDP_ENTITY_ID);
        assertion.setIssuer(issuer);
        assertion.setIssueInstant(new DateTime());

        assertion.setID(OpenSAMLUtils.generateSecureRandomId());

        Subject subject = OpenSAMLUtils.buildSAMLObject(Subject.class);
        assertion.setSubject(subject);

        NameID nameID = OpenSAMLUtils.buildSAMLObject(NameID.class);
        nameID.setFormat(NameIDType.TRANSIENT);
        nameID.setValue("Some NameID value");
        nameID.setSPNameQualifier("SP name qualifier");
        nameID.setNameQualifier("Name qualifier");

        subject.setNameID(nameID);

        subject.getSubjectConfirmations().add(buildSubjectConfirmation());

        assertion.setConditions(buildConditions());

        assertion.getAttributeStatements().add(buildAttributeStatement());

        assertion.getAuthnStatements().add(buildAuthnStatement());

        return assertion;
    }

    private SubjectConfirmation buildSubjectConfirmation() {
        SubjectConfirmation subjectConfirmation = OpenSAMLUtils.buildSAMLObject(SubjectConfirmation.class);
        subjectConfirmation.setMethod(SubjectConfirmation.METHOD_BEARER);

        SubjectConfirmationData subjectConfirmationData = OpenSAMLUtils.buildSAMLObject(SubjectConfirmationData.class);
        subjectConfirmationData.setInResponseTo("Made up ID");
        subjectConfirmationData.setNotBefore(new DateTime().minusDays(2));
        subjectConfirmationData.setNotOnOrAfter(new DateTime().plusDays(2));
        subjectConfirmationData.setRecipient(IdPConstants.ASSERTION_CONSUMER_SERVICE);

        subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);

        return subjectConfirmation;
    }

    private AuthnStatement buildAuthnStatement() {
        AuthnStatement authnStatement = OpenSAMLUtils.buildSAMLObject(AuthnStatement.class);
        AuthnContext authnContext = OpenSAMLUtils.buildSAMLObject(AuthnContext.class);
        AuthnContextClassRef authnContextClassRef = OpenSAMLUtils.buildSAMLObject(AuthnContextClassRef.class);
        authnContextClassRef.setAuthnContextClassRef(AuthnContext.SMARTCARD_AUTHN_CTX);
        authnContext.setAuthnContextClassRef(authnContextClassRef);
        authnStatement.setAuthnContext(authnContext);

        authnStatement.setAuthnInstant(new DateTime());

        return authnStatement;
    }

    private Conditions buildConditions() {
        Conditions conditions = OpenSAMLUtils.buildSAMLObject(Conditions.class);
        conditions.setNotBefore(new DateTime().minusDays(2));
        conditions.setNotOnOrAfter(new DateTime().plusDays(2));
        AudienceRestriction audienceRestriction = OpenSAMLUtils.buildSAMLObject(AudienceRestriction.class);
        Audience audience = OpenSAMLUtils.buildSAMLObject(Audience.class);
        audience.setAudienceURI(IdPConstants.ASSERTION_CONSUMER_SERVICE);
        audienceRestriction.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(audienceRestriction);
        return conditions;
    }

    private AttributeStatement buildAttributeStatement() {
        AttributeStatement attributeStatement = OpenSAMLUtils.buildSAMLObject(AttributeStatement.class);

        Attribute attributeUserName = OpenSAMLUtils.buildSAMLObject(Attribute.class);

        XSStringBuilder stringBuilder = (XSStringBuilder)Configuration.getBuilderFactory().getBuilder(XSString.TYPE_NAME);
        XSString userNameValue = stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
        userNameValue.setValue("bob");

        attributeUserName.getAttributeValues().add(userNameValue);
        attributeUserName.setName("username");
        attributeStatement.getAttributes().add(attributeUserName);

        Attribute attributeLevel = OpenSAMLUtils.buildSAMLObject(Attribute.class);
        XSString levelValue = stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
        levelValue.setValue("999999999");

        attributeLevel.getAttributeValues().add(levelValue);
        attributeLevel.setName("telephone");
        attributeStatement.getAttributes().add(attributeLevel);

        return attributeStatement;

    }

    public static Envelope wrapInSOAPEnvelope(final XMLObject xmlObject) {
    	logger.info("In wrapInSOAPEnvelope() of IdPArtifactResolutionService");

        Envelope envelope = OpenSAMLUtils.buildSAMLObject(Envelope.class);
        Body body = OpenSAMLUtils.buildSAMLObject(Body.class);

        body.getUnknownXMLObjects().add(xmlObject);

        envelope.setBody(body);

        return envelope;
    }


    public static void printSAMLObject(final XMLObject object, final PrintWriter writer) {
    	logger.info("In printSAMLObject() of IdPArtifactResolutionService");

        try {
            DocumentBuilder builder;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            builder = factory.newDocumentBuilder();

            org.w3c.dom.Document document = builder.newDocument();
            Marshaller out = Configuration.getMarshallerFactory().getMarshaller(object);
            out.marshall(object, document);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(writer);
            DOMSource source = new DOMSource(document);
            transformer.transform(source, result);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (MarshallingException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

}
