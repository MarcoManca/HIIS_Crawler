package it.cnr.isti.hiis.largescalecrawlingselenium.model;

import java.util.Date;

/**
 *
 * @author Marco Manca
 */
public class SecurityDetails {
    private boolean https;
    private SecurityState state;
    private final String cipher;
    private final String issuer;
    private final String protocol;
    private Date validTo;
    private boolean expired;

    public SecurityDetails(boolean https, SecurityState state, String cipher, String issuer, String protocol, Date validTo) {
        this.https = https;
        this.state = state;
        this.cipher = cipher;
        this.issuer = issuer;
        this.protocol = protocol;
        this.validTo = validTo;
        Date today = new Date();
        if(validTo.before(today)) {
            this.expired = true;
        } else {
            this.expired = false;
        }
    }

    public SecurityDetails() {
        this.https = false;
        this.cipher = "";
        this.issuer = "";
        this.protocol = "";
        this.state = SecurityState.INSECURE;  
        this.expired = true;
    }

    
    public String getCipher() {
        return cipher;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getProtocol() {
        return protocol;
    }

    public Date getValidTo() {
        return validTo;
    }

    public boolean isExpired() {
        return expired;
    }
    
    public SecurityState getState() {
        return state;
    }

    public void setState(SecurityState state) {
        this.state = state;
    }

    public void setValidTo(Date validTo) {
        this.validTo = validTo;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public boolean isHttps() {
        return https;
    }

    public void setHttps(boolean https) {
        this.https = https;
    }
    
    
}
