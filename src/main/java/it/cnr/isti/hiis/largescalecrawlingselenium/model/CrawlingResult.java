package it.cnr.isti.hiis.largescalecrawlingselenium.model;

import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Marco Manca
 */
public class CrawlingResult {
    private final String rootUrl;
    private final String ipaCode;
    private final List<String> pagesList;    
    private long time_elapsed;
    private status status;
    private String exceptionMsg;    
    private HashMap<String, List<String>> exceptions;
    private AccessibilityDeclaration accessibilityDeclaration;
    private SecurityDetails securityDetails;
    
    private int crawlThreadIndex;
    
    public CrawlingResult(String rootUrl, String ipaCode, List<String> pagesList, long time_elapsed,
            AccessibilityDeclaration accessibilityDeclaration, SecurityDetails securityDetails, int crawlThreadIndex) {        
        this.rootUrl = rootUrl;
        this.ipaCode = ipaCode;
        this.pagesList = pagesList;        
        this.time_elapsed = time_elapsed;        
        this.status = status.OK;        
        this.accessibilityDeclaration = accessibilityDeclaration;
        this.securityDetails = securityDetails;
        this.crawlThreadIndex = crawlThreadIndex;
    }

    public CrawlingResult(String rootUrl, String ipaCode, List<String> pagesList, long time_elapsed, String exceptionMsg,
            AccessibilityDeclaration accessibilityDeclaration, SecurityDetails securityDetails, int crawlThreadIndex) {
        this.rootUrl = rootUrl;
        this.ipaCode = ipaCode;
        this.pagesList = pagesList;
        this.time_elapsed = time_elapsed;
        this.status = status.EXCEPTION;
        this.exceptionMsg = exceptionMsg; 
        this.accessibilityDeclaration = accessibilityDeclaration;
        this.securityDetails = securityDetails;
        this.crawlThreadIndex = crawlThreadIndex;
    }
    
    

    public status getStatus() {
        return status;
    }
        
    public String getRootUrl() {
        return rootUrl;
    }

    public List<String> getPagesList() {
        return pagesList;
    }

    public String getIpaCode() {
        return ipaCode;
    }
    
    public long getTime_elapsed() {
        return time_elapsed;
    }

    public void setTime_elapsed(long time_elapsed) {
        this.time_elapsed = time_elapsed;
    }

    public String getExceptionMsg() {
        return exceptionMsg;
    }

    public HashMap<String, List<String>> getExceptions() {
        return exceptions;
    }

    public void setExceptions(HashMap<String, List<String>> exceptions) {
        this.exceptions = exceptions;
    }

    public AccessibilityDeclaration getAccessibilityDeclaration() {
        if (this.accessibilityDeclaration == null) {
            this.accessibilityDeclaration = new AccessibilityDeclaration(false, "", "");
        }
        return this.accessibilityDeclaration;
    }

    public SecurityDetails getSecurityDetails() {
        return securityDetails;
    }

    public void setSecurityDetails(SecurityDetails securityDetails) {
        this.securityDetails = securityDetails;
    }

    public int getCrawlThreadIndex() {
        return crawlThreadIndex;
    }
    
    
}
