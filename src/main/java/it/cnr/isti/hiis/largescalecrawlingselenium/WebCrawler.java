package it.cnr.isti.hiis.largescalecrawlingselenium;

import it.cnr.isti.hiis.largescalecrawlingselenium.exception.CrawlingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

/**
 *
 * @author Marco Manca
 */
public abstract class WebCrawler {
    public String originalRootUrl;
    public String seedUrl;
    public String ipaCode;
    public List<URL> allowedDomainList;
    public int maxPageNumber;
    public WebDriver driver = null;
    public int crawlThreadIdx;
    
    public WebCrawler(String seedUrl, String ipaCode, int maxPageNumber,
            WebDriver seleniumDriver, int crawlThreadIdx) throws MalformedURLException {
        this.originalRootUrl = seedUrl;
        if(!seedUrl.startsWith("http")) {
            this.seedUrl = "https://".concat(seedUrl);
        } else {
            this.seedUrl = seedUrl;
        }        
        this.ipaCode = ipaCode;
        try {
            this.allowedDomainList = new ArrayList<>(10);
            this.allowedDomainList.add(new URL(this.seedUrl));
        } catch (MalformedURLException ex) {
            throw ex;
        }
        this.maxPageNumber = maxPageNumber;   
        this.crawlThreadIdx = crawlThreadIdx;
        if (seleniumDriver == null) {
            System.setProperty("webdriver.chrome.driver", System.getenv("webdriverchromedriver"));
            //https://stackoverflow.com/questions/40305669/selenium-webdriver-3-0-1-chromedriver-exe-2-25-whitelisted-ips
            System.setProperty("webdriver.chrome.whitelistedIps", "");

            Map<String, Object> prefs = new HashMap<String, Object>();
            // browser setting to disable image --> Headless chrome doesn't support preferrences setting
            prefs.put("profile.default_content_settings.images", 2);
            prefs.put("profile.managed_default_content_settings.images", 2);
            //block all downloads
            //https://stackoverflow.com/questions/59491516/disable-all-downloads-with-chromedriver-and-selenium
            prefs.put("download_restrictions", 3);
            prefs.put("download.default_directory", "/dev/null");

            ChromeOptions opt = new ChromeOptions();
            opt.addArguments("user-data-dir=/data/crawler/tmp/");
            opt.addArguments("--headless");
            opt.addArguments("user-agent=\"--Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.90 Safari/537.36\"");
            opt.addArguments("--disable-gpu");
            opt.addArguments("--blink-settings=imagesEnabled=false");
            //https://stackoverflow.com/a/50642913
            opt.addArguments("--no-sandbox"); // Bypass OS security model
            //https://stackoverflow.com/questions/73021320/sessionnotcreatedexception-could-not-start-a-new-session-response-code-500-err
            opt.addArguments("--disable-dev-shm-usage");
            opt.addArguments("--ignore-ssl-errors=yes");
            opt.addArguments("--ignore-certificate-errors");
            //https://stackoverflow.com/questions/40305669/selenium-webdriver-3-0-1-chromedriver-exe-2-25-whitelisted-ips
            opt.addArguments("--whitelisted-ips=''");
            //https://stackoverflow.com/questions/75678572/java-io-ioexception-invalid-status-code-403-text-forbidden
            opt.addArguments("--remote-allow-origins=*");
            if (LageScaleCrawlingSeleniumMain.useProxy) {
                opt.addArguments("--proxy-server=" + LageScaleCrawlingSeleniumMain.proxyUrl + ":" + LageScaleCrawlingSeleniumMain.proxyPort);
            }
            opt.setExperimentalOption("prefs", prefs);
            
            LoggingPreferences logPrefs = new LoggingPreferences();
            logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
            opt.setCapability("goog:loggingPrefs", logPrefs);
            
            driver = new ChromeDriver(opt);
        } else {
            this.driver = seleniumDriver;
        }
        
    }

    public abstract void start() throws CrawlingException;

    public abstract void shouldVisit(String url, boolean isHomePage) throws CrawlingException;

    public abstract void visit(String url, boolean isHomePage);

    public boolean notIsSameDomain(URL href) {
        String newUrlHost = href.getHost().replaceAll("^w[a-z0-9]*\\1?.", "");
        boolean foundSameDomain = false;
        for(URL rootUrl : this.allowedDomainList) {
            String tmpRootUrl = rootUrl.getHost().replaceAll("^w[a-z0-9]*\\1?.", "");            
            //Col contains root https://giove.isti.cnr.it - href https://www.cnr.it/ sono lo stesso dominio, mettere equals
            if (tmpRootUrl.equals(newUrlHost)) {                
                foundSameDomain = true;
                break;
            }
        }                
        if(foundSameDomain) {
//            System.out.println("Same domain: ".concat(href.toString()).concat(" host ").concat(href.getHost()));
        } else {
//            System.out.println("NOT Same domain: ".concat(href.getHost()));
        }
        return !foundSameDomain;
    }
        
    public boolean isContentTypeAllowed(String contentType) {
        if (contentType.toLowerCase().startsWith("text/html")) {
            return true;
        } else if (contentType.toLowerCase().startsWith("text/xml")
                || contentType.toLowerCase().startsWith("application")
                || contentType.toLowerCase().startsWith("image/x-icon")
                || contentType.toLowerCase().startsWith("text/css")
                || contentType.toLowerCase().startsWith("text/xml+oembed")
                || contentType.toLowerCase().startsWith("text/calendar")
                || contentType.toLowerCase().startsWith("text/ecmascript")
                || contentType.toLowerCase().startsWith("video/")
                || contentType.toLowerCase().startsWith("font/")
                || contentType.toLowerCase().startsWith("text/js")) {
            return false;
        } else {
            return true;
        }
    }
    
    public boolean notIsLink(String url) {
        if(url.startsWith("http")) {
            return false;
        } else {
            return true;
        }
    }

    public String getSeedUrl() {
        return seedUrl;
    }

    public String getIpaCode() {
        return ipaCode;
    }

    public String getOriginalRootUrl() {
        return originalRootUrl;
    }
    
    
}
