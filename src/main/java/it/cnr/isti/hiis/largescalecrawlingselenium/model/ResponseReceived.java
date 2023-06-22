package it.cnr.isti.hiis.largescalecrawlingselenium.model;

/**
 *
 * @author Marco Manca
 */
public class ResponseReceived {
    private String contentType;
    private int httpCode;
    private SecurityDetails securityDetails;
    
    public ResponseReceived() {
        this.contentType = "";
        this.httpCode = -1;
        this.securityDetails = new SecurityDetails();
    }

    public ResponseReceived(String contentType, int httpCode, SecurityDetails securityDetails) {
        this.contentType = contentType;
        this.httpCode = httpCode;
        this.securityDetails = securityDetails;
    }
        
    public String getContentType() {
        return contentType;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public SecurityDetails getSecurityDetails() {
        return securityDetails;
    }
    
    
    
}
