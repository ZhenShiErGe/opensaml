package com.fed.saml.protocol.sp.utils;

import org.opensaml.common.impl.SecureRandomIdentifierGenerator;
import org.opensaml.saml2.binding.artifact.SAML2ArtifactType0004;
import org.opensaml.ws.soap.soap11.Body;
import org.opensaml.ws.soap.soap11.Envelope;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.io.Marshaller;
import org.opensaml.xml.io.MarshallingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.security.NoSuchAlgorithmException;

public class OpenSAMLUtils {
    private static Logger logger = LoggerFactory.getLogger(OpenSAMLUtils.class);
    private static SecureRandomIdentifierGenerator secureRandomIdGenerator;

    static {
        try {
            secureRandomIdGenerator = new SecureRandomIdentifierGenerator();
        } catch (NoSuchAlgorithmException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static <T> T buildSAMLObject(final Class<T> clazz) {
        T object = null;
        try {
            XMLObjectBuilderFactory builderFactory = Configuration.getBuilderFactory();
            QName defaultElementName = (QName)clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
            object = (T)builderFactory.getBuilder(defaultElementName).buildObject(defaultElementName);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Could not create SAML object");
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Could not create SAML object");
        }

        return object;
    }

    public static String generateSecureRandomId() {
        return secureRandomIdGenerator.generateIdentifier();
    }

    public static void logSAMLObject(final XMLObject object) {
        try {
            DocumentBuilder builder;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            builder = factory.newDocumentBuilder();

            Document document = builder.newDocument();
            Marshaller out = Configuration.getMarshallerFactory().getMarshaller(object);
            out.marshall(object, document);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(document);
            transformer.transform(source, result);
            String xmlString = result.getWriter().toString();

            logger.info(xmlString);
        } catch (ParserConfigurationException e) {
            logger.error(e.getMessage(), e);
        } catch (MarshallingException e) {
            logger.error(e.getMessage(), e);
        } catch (TransformerException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static Envelope wrapInSOAPEnvelope(final XMLObject xmlObject) throws IllegalAccessException {
        Envelope envelope = OpenSAMLUtils.buildSAMLObject(Envelope.class);
        Body body = OpenSAMLUtils.buildSAMLObject(Body.class);

        body.getUnknownXMLObjects().add(xmlObject);

        envelope.setBody(body);

        return envelope;
    }
    
    public static String getSAML2ArtifactType0004(String endpointIndex, String sourceId, String msgHandle) {

        // generate SAML artifact from 3 components and persist it 
        // 3 components: TypeCode, EndpointIndex of artifact resolution endpoint of issuer - "idpArtResolutionIndx"
        // and RemainingArtifact: SourceId = SHA-1 hash of the issuer's entityID - "http://local/samlidp"
        //                        MessageHandle = random bytes sequence that references a SAML message that the artifact issuer is willing to produce on-demand
//        byte[] endpointIndex = "12".getBytes();
//        byte[] sourceId = "http://local/samlidp".getBytes();
//        byte[] msgHandle = "samlMessageHandleRef".getBytes();
        SAML2ArtifactType0004 samlArtifact = new SAML2ArtifactType0004(endpointIndex.getBytes(), sourceId.getBytes(), msgHandle.getBytes());
        return samlArtifact.toString();
    }
    
}
