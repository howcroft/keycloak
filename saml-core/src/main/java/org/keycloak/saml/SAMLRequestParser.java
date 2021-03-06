package org.keycloak.saml;

import org.jboss.logging.Logger;
import org.keycloak.common.util.StreamUtil;
import org.keycloak.saml.common.PicketLinkLogger;
import org.keycloak.saml.common.PicketLinkLoggerFactory;
import org.keycloak.saml.processing.api.saml.v2.request.SAML2Request;
import org.keycloak.saml.processing.api.saml.v2.response.SAML2Response;
import org.keycloak.saml.processing.core.saml.v2.common.SAMLDocumentHolder;
import org.keycloak.saml.processing.web.util.PostBindingUtil;
import org.keycloak.saml.processing.web.util.RedirectBindingUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class SAMLRequestParser {
    private static final PicketLinkLogger logger = PicketLinkLoggerFactory.getLogger();
    protected static Logger log = Logger.getLogger(SAMLRequestParser.class);

    public static SAMLDocumentHolder parseRequestRedirectBinding(String samlMessage) {
        InputStream is;
        is = RedirectBindingUtil.base64DeflateDecode(samlMessage);
        if (log.isDebugEnabled()) {
            String message = null;
            try {
                message = StreamUtil.readString(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.debug("SAML Redirect Binding");
            log.debug(message);
            is = new ByteArrayInputStream(message.getBytes());

        }
        SAML2Request saml2Request = new SAML2Request();
        try {
            saml2Request.getSAML2ObjectFromStream(is);
            return saml2Request.getSamlDocumentHolder();
        } catch (Exception e) {
            logger.samlBase64DecodingError(e);
        }
        return null;

    }

    public static SAMLDocumentHolder parseRequestPostBinding(String samlMessage) {
        InputStream is;
        byte[] samlBytes = PostBindingUtil.base64Decode(samlMessage);
        if (log.isDebugEnabled()) {
            String str = new String(samlBytes);
            log.debug("SAML POST Binding");
            log.debug(str);
        }
        is = new ByteArrayInputStream(samlBytes);
        SAML2Request saml2Request = new SAML2Request();
        try {
            saml2Request.getSAML2ObjectFromStream(is);
            return saml2Request.getSamlDocumentHolder();
        } catch (Exception e) {
            logger.samlBase64DecodingError(e);
        }
        return null;
    }

    public static SAMLDocumentHolder parseResponsePostBinding(String samlMessage) {
        byte[] samlBytes = PostBindingUtil.base64Decode(samlMessage);
        log.debug("SAML POST Binding");
        return parseResponseDocument(samlBytes);
    }

    public static SAMLDocumentHolder parseResponseDocument(byte[] samlBytes) {
        if (log.isDebugEnabled()) {
            String str = new String(samlBytes);
            log.debug(str);
        }
        InputStream is = new ByteArrayInputStream(samlBytes);
        SAML2Response response = new SAML2Response();
        try {
            response.getSAML2ObjectFromStream(is);
            return response.getSamlDocumentHolder();
        } catch (Exception e) {
            logger.samlBase64DecodingError(e);
        }
        return null;
    }

    public static SAMLDocumentHolder parseResponseRedirectBinding(String samlMessage) {
        InputStream is = RedirectBindingUtil.base64DeflateDecode(samlMessage);
        if (log.isDebugEnabled()) {
            String message = null;
            try {
                message = StreamUtil.readString(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.debug("SAML Redirect Binding");
            log.debug(message);
            is = new ByteArrayInputStream(message.getBytes());

        }
        SAML2Response response = new SAML2Response();
        try {
            response.getSAML2ObjectFromStream(is);
            return response.getSamlDocumentHolder();
        } catch (Exception e) {
            logger.samlBase64DecodingError(e);
        }
        return null;

    }


}
