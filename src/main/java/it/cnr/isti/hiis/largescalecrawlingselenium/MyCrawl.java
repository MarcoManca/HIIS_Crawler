package it.cnr.isti.hiis.largescalecrawlingselenium;

import static it.cnr.isti.hiis.largescalecrawlingselenium.LageScaleCrawlingSeleniumMain.FILTERS;
import it.cnr.isti.hiis.largescalecrawlingselenium.exception.CrawlingException;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.AccessibilityDeclaration;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.CrawlingResult;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.SecurityDetails;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.SecurityState;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.status;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;

/**
 *
 * @author Marco Manca
 */
public class MyCrawl extends WebCrawler implements Callable<CrawlingResult> {
    
    /* Lista domini ai quali devono appartenere le pagine da trovare.
     * inizialmente il dominio è quello della seed url, poi in caso di redirect
     * si aggiunge il nuovo dominio verso cui la pagina è indirizzata
     */
    private List<URL> allowedDomainList;
    /* Lista pagine trovate */
    private List<String> urlList;
    /* Creo lista pagine già visitate  */
    List<String> pageVisited;
    /* Creo coda di pagine da visitare (non synch, ogni thread ha la sua lista di pagine da visitare) */
    Queue<String> pageToVisit;
    /* lista pagine visitate e rimosse dalla url list perchè non rispettano i requisiti */
    List<String> pageRemoved;
    /* hashmap che colleziona le eccezioni TIPO ECCEZIONE:LISTA URLS che l'hanno sollevate */
    private HashMap<String, List<String>> exceptions;

    private AccessibilityDeclaration accessibilityDecl;
    private SecurityDetails securityDetails;

    public MyCrawl(String seedUrl, String ipaCode, int maxPageNumber,
            WebDriver seleniumDriver, int crawlThreadIdx) throws MalformedURLException {         
        super(seedUrl, ipaCode, maxPageNumber, seleniumDriver, crawlThreadIdx);        
        this.urlList = new LinkedList<>();
        this.pageToVisit = new LinkedList<>();
        this.pageToVisit.add(super.seedUrl);
        this.pageVisited = new LinkedList<>();
        this.pageRemoved = new LinkedList<>();
        this.allowedDomainList = super.allowedDomainList;
        this.exceptions = new HashMap<>(4);        
    }

    @Override
    public CrawlingResult call() {
        long startTime = Calendar.getInstance().getTimeInMillis();
        try {            
            status _status = status.OK;
            String msg = "";
            try {
                start();
            } catch (CrawlingException ex) {
                _status = status.EXCEPTION;
                msg = ex.getType();
            }
            long endTime = Calendar.getInstance().getTimeInMillis();
            long elapsedTime = (endTime - startTime) / 1000;
            //il driver non viene chiuso qui, ma viene riutilizzato per il prossimo sito
    //        try {
    //            this.driver.quit();
    //        } catch (org.openqa.selenium.TimeoutException ex) {
    //        } catch (org.openqa.selenium.WebDriverException e) {
    //        }
            CrawlingResult res;
            if (_status.equals(status.OK)) {
                res = new CrawlingResult(super.originalRootUrl, super.ipaCode, urlList, elapsedTime, accessibilityDecl, securityDetails, super.crawlThreadIdx);
            } else {
                res = new CrawlingResult(super.originalRootUrl, super.ipaCode, urlList, elapsedTime, msg, accessibilityDecl, securityDetails, super.crawlThreadIdx);
            }
            res.setExceptions(exceptions);
            return res;
        } catch(Exception ex) {
            long endTime = Calendar.getInstance().getTimeInMillis();
            long elapsedTime = (endTime - startTime) / 1000;
            return new CrawlingResult(super.originalRootUrl, super.ipaCode, urlList, elapsedTime, ex.getMessage(), accessibilityDecl, securityDetails, super.crawlThreadIdx);
        }
    }

    @Override
    public void start() throws CrawlingException {
        if (this.allowedDomainList.isEmpty()) {
            return;
        }
        //visit the seed url (aka rootUrl)
        shouldVisit(this.pageToVisit.remove(), true);
        if (!this.exceptions.isEmpty()) {
            String msg = "";
            int i = 0;
            for (String ex : this.exceptions.keySet()) {
                if (i > 0) {
                    msg = msg.concat(" - ");
                }
                msg = msg.concat(ex);
            }
            throw new CrawlingException(msg);
        }
        /* in should visit ci sono casi in cui si controllano condizioni 
         * che se non sono rispettate comportano la chiamata di should visit su un'altra url.
         * questo crea una serie di chiamate l'una dentro l'altra che potrebbero incasinare lo heap
         * meglio mettere un while con condizioni:
         * urlList.size() < super.maxPageNumber && pageToVisit.size() > 0
         * dentro should visit semplicemente fare return se le condizioni non sono rispettate
         */
        while (urlList.size() < super.maxPageNumber && pageToVisit.size() > 0) {
            try {
                shouldVisit(pageToVisit.remove(), false);
                Thread.sleep(LageScaleCrawlingSeleniumMain.politnessMillisecondsDelay);
            } catch (InterruptedException ex) {
                Logger.getLogger(MyCrawl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void shouldVisit(String urlToVisit, boolean isHomePage) {
        if (urlList.size() >= super.maxPageNumber) {
            /* non ho necessità di visitare altre pagine se ho già raggiunto il massimo delle pagine che mi servivano */
            return;
        }
        
        /*verificare che non l'ho già visitata e in caso visitare la prossima pagina dalla coda */
        if (this.pageVisited.contains(urlToVisit) /*|| urlList.contains(urlToVisit)*/) {
            return;
        }
        URL url;
        try {
            url = new URL(urlToVisit);
        } catch (MalformedURLException ex) {
            return;
        }
        //System.out.println("Should Visit ".concat(url.toString()));
        if (this.notIsSameDomain(url)
                || FILTERS.matcher(url.toString()).matches()
                || this.notIsLink(url.toString()) /*|| url.toString().contains("wp-json/")*/) {
            /* different domain || estensione file non ammessa, visitare la prossima pagina dalla coda */
            return;
        }
        //https://stackoverflow.com/questions/5664808/difference-between-webdriver-get-and-webdriver-navigate#:~:text=So%20the%20main%20difference%20between,to%20load%20fully%20or%20completely.        
        try {
            driver.get(url.toString());            
            it.cnr.isti.hiis.largescalecrawlingselenium.model.ResponseReceived responseReceived = 
                    getResponseReceived(driver.manage().logs().get("performance"), driver.getCurrentUrl());
                        
            if (responseReceived.getHttpCode() != HttpURLConnection.HTTP_OK
                    && responseReceived.getHttpCode() != HttpURLConnection.HTTP_NOT_MODIFIED
                    && responseReceived.getHttpCode() != HttpURLConnection.HTTP_MOVED_TEMP
                    && responseReceived.getHttpCode() != HttpURLConnection.HTTP_MOVED_PERM
                    && responseReceived.getHttpCode() != HttpURLConnection.HTTP_SEE_OTHER
                    && //http 307 Temporary Redirect https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/307
                    responseReceived.getHttpCode() != 307
                    && //http 308 Permanent Redirect https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/308
                    responseReceived.getHttpCode() != 308) {
                urlList.remove(url.toString());
                pageRemoved.add(url.toString());
                if (!exceptions.containsKey("HTTP_CODE_".concat(String.valueOf(responseReceived.getHttpCode())))) {
                    exceptions.put("HTTP_CODE_".concat(String.valueOf(responseReceived.getHttpCode())), new ArrayList<>(5));
                }
                exceptions.get("HTTP_CODE_".concat(String.valueOf(responseReceived.getHttpCode()))).add(url.toString());
                //System.out.println("HTTP code " + String.valueOf(responseReceived.getHttpCode()) + " url " + url.toString());
                return;
            }
            if (!isContentTypeAllowed(responseReceived.getContentType())) {
                urlList.remove(url.toString());
                pageRemoved.add(url.toString());
                return;
            }
                    
            if (isHomePage) {
                /* check on https certificate version */
                    this.securityDetails = responseReceived.getSecurityDetails();
            }
        } catch (WebDriverException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("ERR_NAME_NOT_RESOLVED")) {
                pageVisited.add(url.toString());
                urlList.remove(url.toString());
                pageRemoved.add(url.toString());
                //error page not found
                if (!this.exceptions.containsKey("ERR_NAME_NOT_RESOLVED")) {
                    this.exceptions.put("ERR_NAME_NOT_RESOLVED", new ArrayList<>(2));
                }
                this.exceptions.get("ERR_NAME_NOT_RESOLVED").add(url.toString());
            } else if (ex.getMessage() != null && 
                    (ex.getMessage().contains("ERR_CONNECTION_REFUSED") ||
                    ex.getMessage().contains("ERR_CONNECTION_RESET"))) {
                if(isHomePage) {
                    /* connection refused nella home page -> se il protocollo è https
                     * e provo con http
                    */                 
                    if (urlToVisit.startsWith("https")) {
                        super.seedUrl = urlToVisit.replace("https", "http");
                        this.shouldVisit(super.seedUrl, isHomePage);
                    } else if(ex.getMessage().contains("ERR_CONNECTION_REFUSED")){
                        if (!this.exceptions.containsKey("ERR_CONNECTION_REFUSED")) {
                            this.exceptions.put("ERR_CONNECTION_REFUSED", new ArrayList<>(2));
                        }
                        this.exceptions.get("ERR_CONNECTION_REFUSED").add(url.toString());
                    } else if(ex.getMessage().contains("ERR_CONNECTION_RESET")){
                        if (!this.exceptions.containsKey("ERR_CONNECTION_RESET")) {
                            this.exceptions.put("ERR_CONNECTION_RESET", new ArrayList<>(2));
                        }
                        this.exceptions.get("ERR_CONNECTION_RESET").add(url.toString());
                    }
                } else {
                    if(ex.getMessage().contains("ERR_CONNECTION_REFUSED")){
                        if (!this.exceptions.containsKey("ERR_CONNECTION_REFUSED")) {
                            this.exceptions.put("ERR_CONNECTION_REFUSED", new ArrayList<>(2));
                        }
                        this.exceptions.get("ERR_CONNECTION_REFUSED").add(url.toString());
                    } else if(ex.getMessage().contains("ERR_CONNECTION_RESET")){
                        if (!this.exceptions.containsKey("ERR_CONNECTION_RESET")) {
                            this.exceptions.put("ERR_CONNECTION_RESET", new ArrayList<>(2));
                        }
                        this.exceptions.get("ERR_CONNECTION_RESET").add(url.toString());
                    }
                }
            } else if (ex.getClass().equals(org.openqa.selenium.TimeoutException.class)) {
                if (!this.exceptions.containsKey("TIMEOUT_EXCEPTION")) {
                    this.exceptions.put("TIMEOUT_EXCEPTION", new ArrayList<>(2));
                }
                this.exceptions.get("TIMEOUT_EXCEPTION").add(url.toString());
            } else {
                if (!this.exceptions.containsKey("WEBDRIVER_EXCEPTION")) {
                    this.exceptions.put("WEBDRIVER_EXCEPTION", new ArrayList<>(2));
                }
                this.exceptions.get("WEBDRIVER_EXCEPTION").add(url.toString());
            }
            return;
        }        
        if (!url.toString().equals(driver.getCurrentUrl())) {
            //System.out.println("Different url: "+url.toString()+" current "+driver.getCurrentUrl());
            if (!pageVisited.contains(url.toString())) {
                pageVisited.add(url.toString());
            }
            try {
                URL currentUrl = new URL(driver.getCurrentUrl());
                /* Se la url corrente non è dello stesso dominio di quella che ho caricato, 
                                             * allora c'è stato un redirect e aggiorno la lista dei domini accettati
                 */
                if (notIsSameDomain(currentUrl) && !currentUrl.getHost().contains("youtube")) {
                    //System.out.println("Current url not same domain ".concat(currentUrl.toString()));
                    if (!this.allowedDomainList.contains(currentUrl)) {
                        //System.out.println("Add new allowed domain");
                        this.allowedDomainList.add(currentUrl);
                    }
                    if (this.urlList.size() < maxPageNumber
                            && !pageRemoved.contains(currentUrl.toString()) && !this.urlList.contains(currentUrl.toString())) {
                        this.urlList.add(currentUrl.toString());
                    }
                }
            } catch (MalformedURLException ex) {
            }
        }
        /* visito solo pagine che rispettano i vincoli:
             * no estensioni js, jpeg, etc;
             * no url di domini diversi
         */
        visit(driver.getCurrentUrl(), isHomePage);

    }

    @Override
    public void visit(String url, boolean isHomePage) {
        //System.out.println("Visiting ".concat(url));
        /* l'aggiungo alla lista delle pagine visitate */
        pageVisited.add(url);
        /* la url è stata aggiunta alla lista delle pageremoved perchè non rispetta qualche condizione 
         * oppure perchè al caricamento ha restituito uno status code != 200
         */
        if (pageRemoved.contains(url)) {
            return;
        }
        if (driver.getTitle() != null && driver.getTitle().equals("404 Not Found")) {
            //page not found            
            this.urlList.remove(url);
            pageRemoved.add(url);
            return;
        }
        if (!this.urlList.contains(url)) {
            this.urlList.add(url);
        }
        /* controllo richiesto da agid per verificare che i siti della pubblica amministrazione includano la dichiarazione di accessibilità */
        if(isHomePage) {
            List<WebElement> linkElements = driver.findElements(By.xpath("//a[starts-with(@href,'https://form.agid.gov.it')]"));
            if(!linkElements.isEmpty()) {
                if(linkElements.size() > 1) {                    
                    for(WebElement el : linkElements) {
                        if(el.getText()!= null && !el.getText().toLowerCase().startsWith("obiettiv")) {
                            if(el.getText().equals("")) {
                                try {
                                    WebElement childElement = el.findElement(By.tagName("img"));
                                    if(childElement != null) {
                                        //il link ha un'img
                                        accessibilityDecl = new AccessibilityDeclaration(true, "img", el.getAttribute("href").trim());                                        
                                    } else {
                                        accessibilityDecl = new AccessibilityDeclaration(true, el.getText().trim(), el.getAttribute("href").trim());                                        
                                    }
                                } catch (NoSuchElementException ex) {
                                    accessibilityDecl = new AccessibilityDeclaration(true, el.getText().trim(), el.getAttribute("href").trim());                                    
                                }
                            } else {
                                accessibilityDecl = new AccessibilityDeclaration(true, el.getText().trim(), el.getAttribute("href").trim());                                
                            }
                            break;
                        }
                    } 
                } else {
                    try {
                        if("".equals(linkElements.get(0).getText().trim())) {
                            WebElement childElement = linkElements.get(0).findElement(By.tagName("img"));
                            if (childElement != null) {
                                //il link ha un'img
                                accessibilityDecl = new AccessibilityDeclaration(true, "img", linkElements.get(0).getAttribute("href").trim());
                            } else {
                                accessibilityDecl = new AccessibilityDeclaration(true, linkElements.get(0).getText().trim(), linkElements.get(0).getAttribute("href").trim());
                            }
                        } else {
                            accessibilityDecl = new AccessibilityDeclaration(true, linkElements.get(0).getText().trim(), linkElements.get(0).getAttribute("href").trim());
                        }
                    } catch (NoSuchElementException ex) {
                        accessibilityDecl = new AccessibilityDeclaration(true, linkElements.get(0).getText().trim(), linkElements.get(0).getAttribute("href").trim());
                    }                    
                }
            }
        }
        /* prendo tutti i link contenuti nei tag a e nav */
        List<WebElement> linkElements = driver.findElements(By.tagName("a"));
        //linkElements.addAll(driver.findElements(By.tagName("nav")));
        //System.out.println("URL ".concat(url).concat(" ").concat(String.valueOf(linkElements.size())));
        for (WebElement link : linkElements) {
            if (urlList.size() >= super.maxPageNumber) {
                return;
            }            
            /* aggiungo i link alla coda in modo che vengano visitati con la politica FIFO */
            try {
                URL linkUrlObj = new URL(link.getAttribute("href"));
                String linkUrl = link.getAttribute("href");
                                    
                if (linkUrl != null
                        && linkUrl.startsWith("http")
                        && !FILTERS.matcher(linkUrl).matches()
                        && !this.notIsSameDomain(linkUrlObj)
                        && !linkUrl.contains("wp-json/")
                        && !linkUrl.contains("wp-includes/")
                        && !linkUrl.contains("mime-type=text%2Fjavascript")) {                    
                    
                    /* https://giove.isti.cnr.it/#ancora 
                     * la parte dopo # viene chiamato fragment
                     * questa pagina è la stessa di 
                     * https://giove.isti.cnr.it/
                     * quindi rimuovo il fragment e controllo che la pagina non sia già stata visitata
                     */
                    URI uri = linkUrlObj.toURI();                    
                    if (uri.getFragment() != null) {
                        if ("".equals(uri.getFragment())) {
                            linkUrl = linkUrl.replace("#", "");
                        } else {
                            linkUrl = linkUrl.replace("#" + uri.getFragment(), "");
                        }
                    }                    
                    if (!this.urlList.contains(linkUrl)
                            && !pageRemoved.contains(linkUrl)) {
                        this.urlList.add(linkUrl);
                        /* page removed contiene la lista della pagine rimosse da url list perchè non rispetto i requisiti
                             * come content type oppure perchè non esistono error 404
                             * evito di aggiungere nuovamente il link se so che già ci sono problemi
                        */
                        pageToVisit.add(linkUrl);
                    }                                                            
                }
            } catch (MalformedURLException | org.openqa.selenium.StaleElementReferenceException
                    | org.openqa.selenium.UnhandledAlertException | URISyntaxException ex) {                
            }            

        }
    }
    
    private it.cnr.isti.hiis.largescalecrawlingselenium.model.ResponseReceived
         getResponseReceived(LogEntries logs, String currentUrl) {
        it.cnr.isti.hiis.largescalecrawlingselenium.model.ResponseReceived resReceived = null;
        for (Iterator<LogEntry> it = logs.iterator(); it.hasNext();) {
            LogEntry entry = it.next();
            try {
                JSONObject json = new JSONObject(entry.getMessage());                
                JSONObject message = json.getJSONObject("message");
                String method = message.getString("method");
                if (method != null
                        && "Network.requestWillBeSent".equals(method)) {
                    JSONObject params = message.getJSONObject("params");
                    //JSONObject request = params.getJSONObject("request");
                    if (params.has("redirectResponse")) {
                        JSONObject redirectResponse = params.getJSONObject("redirectResponse");
                        URL originalUrl;
                        URL newLocation;
                        try {
                            //response url (original ) --> http://www.uslumbria2.it/pagine/per-i-dipendenti-000	                                
                            originalUrl = new URL(redirectResponse.getString("url"));
                            //Location => https://www.uslumbria2.it/pagine/per-i-dipendenti-000
                            newLocation = new URL(redirectResponse.getJSONObject("headers").getString("Location"));
                            //System.out.println("Redirect " + newLocation.toString());
                            if (!originalUrl.getProtocol().equals(newLocation.getProtocol())
                                    && originalUrl.getHost().equals(newLocation.getHost())) {
                                //se il redirect ha semplicemente cambiato il protocolo da http a https ma l'host è lo stesso 
                                //non aggiungo alla lista dei domini  
                                continue;
                            } else if (originalUrl.getHost().equals(newLocation.getHost())) {
                                //il protocollo e l'host sono gli stessi, controllo che non abbia semplicemente aggiunto il www
                                if (!originalUrl.getAuthority().equals(newLocation.getAuthority())) {
                                    //originalUrl.getAuthority() = uslumbria2.it
                                    //newLocation.getAuthority() = www.uslumbria2.it
                                    //non aggiungo alla lista dei domini  
                                    continue;
                                }
                            }
                            /* un sito aveva un redirect verso il canale di youtube e 
                                 * in questo caso si aggiungeva youtube alla lista dei domini validi
                                 * e si crawlavano anche le pagine di youtube
                             */
                            if (newLocation.getHost().contains("youtube.com")) {
                                continue;
                            }
                            //System.out.println("Request will be sent ".concat(newLocation.toString()));
                            if (!allowedDomainList.contains(newLocation)) {
                                //System.out.println("Redirect "+newLocation);
                                allowedDomainList.add(newLocation);
                            }
                        } catch (JSONException | MalformedURLException | org.openqa.selenium.StaleElementReferenceException
                                | org.openqa.selenium.UnhandledAlertException ex) {
                            //Logger.getLogger(MyCrawl.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                } else if (method != null
                        && "Network.responseReceived".equals(method)) {
                    JSONObject params = message.getJSONObject("params");
                    JSONObject response = params.getJSONObject("response");
                    String mimeType = "";                    
                    if(response.has("mimeType")) {
                        mimeType = response.getString("mimeType");
                    }
                    //int status = -1;
                    //in 636 siti nella responde non c'era lo status 
                    //per cui il crawling si bloccava, ho deciso di 
                    //mettere lo status di default a 200 così evito problemi
                    int status = -1;
                    if(response.has("status")) {
                        status = response.getInt("status");
                    }
                    boolean https = false;
                    if (response.has("url") && !response.getString("url").startsWith("data:")) {
                        https = response.getString("url").startsWith("https");
                    } else {
                        https = currentUrl.startsWith("https");
                    }
                    SecurityDetails sd;
                    SecurityState securityStateEnum = SecurityState.INSECURE;
                    if(response.has("securityState")) {
                        String securityState = response.getString("securityState");                        
                        try {
                            securityStateEnum = SecurityState.valueOf(securityState.toUpperCase());
                        } catch(IllegalArgumentException ex) {}                        
                    } 
                    if (response.has("securityDetails")) {
                        JSONObject securityDetailsObj = response.getJSONObject("securityDetails");
                        String cipher = "unknown";
                        if (securityDetailsObj.has("cipher")) {
                            cipher = securityDetailsObj.getString("cipher");
                        }
                        String issuer = "unknown";
                        if (securityDetailsObj.has("issuer")) {
                            issuer = securityDetailsObj.getString("issuer");
                        }
                        String protocol = "unknown";
                        if (securityDetailsObj.has("protocol")) {
                            protocol = securityDetailsObj.getString("protocol");
                        }
                        long validTo = System.currentTimeMillis();
                        if (securityDetailsObj.has("validTo")) {
                            validTo = securityDetailsObj.getInt("validTo");
                        }
                        sd = new SecurityDetails(https, securityStateEnum, cipher,
                                issuer,
                                protocol,
                                new Date(validTo * 1000));
                        return new it.cnr.isti.hiis.largescalecrawlingselenium.model.ResponseReceived(mimeType, status, sd);
                    } else {
                        SecurityDetails securityDetails1 = new SecurityDetails();
                        securityDetails1.setState(securityStateEnum);
                        sd = securityDetails1;                        
                    }
                    resReceived = new it.cnr.isti.hiis.largescalecrawlingselenium.model.ResponseReceived(mimeType, status, sd);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if(resReceived == null) {
            return new it.cnr.isti.hiis.largescalecrawlingselenium.model.ResponseReceived();
        } else {
            return resReceived;
        }
    }
    
    private SecurityDetails getSecurityDetails(LogEntries logs) {
        for (Iterator<LogEntry> it = logs.iterator(); it.hasNext();) {
            LogEntry entry = it.next();
            try {
                JSONObject json = new JSONObject(entry.getMessage());

                JSONObject message = json.getJSONObject("message");
                String method = message.getString("method");

                if (method != null
                        && "Network.responseReceived".equals(method)) {
                    JSONObject params = message.getJSONObject("params");

                    JSONObject response = params.getJSONObject("response");
                        
                    if(response.has("securityState")) {
                        String securityState = response.getString("securityState");
                        SecurityState securityStateEnum = SecurityState.INSECURE;
                        try {
                            securityStateEnum = SecurityState.valueOf(securityState.toUpperCase());
                        } catch(IllegalArgumentException ex) {}
                        if(response.has("securityDetails")) {
                            JSONObject securityDetailsObj = response.getJSONObject("securityDetails"); 
                            String cipher = "unknown";
                            if(securityDetailsObj.has("cipher")) {
                                cipher = securityDetailsObj.getString("cipher");
                            }
                            String issuer = "unknown";
                            if(securityDetailsObj.has("issuer")) {
                                issuer = securityDetailsObj.getString("issuer");
                            }
                            String protocol = "unknown";
                            if(securityDetailsObj.has("protocol")) {
                                protocol = securityDetailsObj.getString("protocol");
                            }
                            long validTo = System.currentTimeMillis();
                            if(securityDetailsObj.has("validTo")) {
                                validTo = securityDetailsObj.getInt("validTo");
                            }
                            boolean https = false;
                            if(response.has("url")) {
                                https = response.getString("url").startsWith("https");
                            }
                            return new SecurityDetails(https, securityStateEnum, cipher, 
                                    issuer, 
                                    protocol,
                                    new Date(validTo*1000));
                        } else {
                            SecurityDetails securityDetails1 = new SecurityDetails();
                            securityDetails1.setState(securityStateEnum);
                            return securityDetails1;
                        }
                    } else {
                        return new SecurityDetails();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return new SecurityDetails();
    }

}
