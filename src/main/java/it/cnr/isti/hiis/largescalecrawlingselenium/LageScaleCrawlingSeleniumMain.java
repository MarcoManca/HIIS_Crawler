package it.cnr.isti.hiis.largescalecrawlingselenium;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.CrawlingResult;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.LoadURLSRes;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.SecurityDetails;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.status;
import jakarta.mail.Session;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
//import javax.mail.PasswordAuthentication;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.openqa.selenium.WebDriverException;
import it.cnr.isti.hiis.largescalecrawlingselenium.model.SecurityState;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
/**
 *
 * @author Marco Manca
 */
public class LageScaleCrawlingSeleniumMain {
    public static Pattern FILTERS = Pattern.compile(".*(\\.(css.*|js|js?|js&|gif.*|jpg.*|jpeg.*|svg.*"
            + "|ods.*|doc.*|xsl.*|txt.*"
            + "|png.*|mp3.*|mp4.*"
            + "|woff.*"
            + "|ttf.*"
            + "|wmv.*|avi.*|mov.*|mpg.*|mpeg.*|3gp.*|"
            + "zip.*|gz.*|pdf.*|svg.*|json.*|ico.*))($|\\?)");
    
    private static String evalId = "2023-06-08";
    //indica se si sta lavorando in locale o in remoto, questa variabile cambia l'url del db mongo a cui connettersi.    
    private static final boolean remote = true;
    //indica quanti siti vengono crawlati in parallelo
    private static final int concurrentCrawling = 48;
    //indica il numeor massimo di pagine che il cralwer deve trovare
    public static int maxPages = 250;
        
    public static boolean useProxy = false;    
    public static String proxyUrl = "";
    public static String proxyPort = "";
    
    public static int politnessMillisecondsDelay = 500;
    
    private static List<WebDriver> seleniumDriverPool;
    
    public static MongoCollection<Document> exceptionsCollection;
    public static MongoCollection<Document> driverExceptionsCollection;
      
    public static void main(String[] args) throws MalformedURLException, ExecutionException {
        String mongoAddress = System.getenv("mongoAddress");
        String mongoUser = System.getenv("mongoUser");
        String mongoPsw = System.getenv("mongoPsw");
        String mongoDB = System.getenv("mongoDB");
                
        //numero totale di crawling thread running
        int numCrawlingRunning = 0;
        int numCrawlingThreadLaunched = 0;
        //numero totale di operazioni di crawling finite
        int numCrawlingFinished = 0;
        //idx delle url scorse nel for
        int crawlThreadIdx = 0;
                
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        
        MongoClient mongoClient;
        if (!remote) {
            //local
            mongoClient = new MongoClient("localhost", 27017);
        } else {
            //remote
            ServerAddress mongoAddr = new ServerAddress(mongoAddress, 27017);
            MongoCredential credential = MongoCredential.createCredential(mongoUser, mongoDB, mongoPsw.toCharArray());
            mongoClient = new MongoClient(mongoAddr, Arrays.asList(credential));            
        }
        MongoDatabase database = mongoClient.getDatabase("agid").withCodecRegistry(pojoCodecRegistry);;

        if (!database.listCollectionNames().into(new ArrayList<>()).contains("crawler")) {
            database.createCollection("crawler");
        }
        MongoCollection<Document> crawlerCollection = database.getCollection("crawler");

        if (!database.listCollectionNames().into(new ArrayList<>()).contains("exceptions")) {
            database.createCollection("exceptions");
        }
        exceptionsCollection = database.getCollection("exceptions");

        if (!database.listCollectionNames().into(new ArrayList<>()).contains("driver_exceptions")) {
            database.createCollection("driver_exceptions");
        }
        driverExceptionsCollection = database.getCollection("driver_exceptions");
        
        List<LoadURLSRes> sites = loadURLS(crawlerCollection, evalId);

        long startTime = Calendar.getInstance().getTimeInMillis();

        ExecutorService crawlExecutor = Executors.newFixedThreadPool(concurrentCrawling);
        ExecutorCompletionService<CrawlingResult> crawlExecCompletionService = new ExecutorCompletionService<>(crawlExecutor);

        seleniumDriverPool = createSeleniumDriverPool(concurrentCrawling);
        
        listUrls:
        for (int i = 0; i < sites.size() || numCrawlingRunning > 0; i++) {
            try {
                if (i < sites.size()) {
                    WebDriver seleniumDriver = null;
                    if (crawlThreadIdx >= 0 && crawlThreadIdx < seleniumDriverPool.size()) {
                        seleniumDriver = seleniumDriverPool.get(crawlThreadIdx);
                    } else {
                        /* crawlThreadIdx e' uguale a -1
                             * indica una situazione in cui all'interno di uno dei thread MyCrawl
                             * si e' creata una situazione imprevvista e si è sollevata un'eccezione
                             * non sapendo qual'è il thread fallito non so quale driver posso
                             * riutilizzare, quindi creo un driver nuovo e lo aggiungo alla lista
                         */
                        System.out.println("crawlThreadIdx = ".concat(String.valueOf(crawlThreadIdx)).concat(": creo nuovo driver per il crawler"));
                        List<WebDriver> driverList = createSeleniumDriverPool(1);
                        if(driverList.isEmpty()) {
                            continue;
                        }
                        seleniumDriver = createSeleniumDriverPool(1).get(0);
                        seleniumDriverPool.add(seleniumDriver);
                        crawlThreadIdx = seleniumDriverPool.size()-1;
                    }                                        
                    //CrawlerController myCrawl = new CrawlerController(sites.get(i).getUrl(), sites.get(i).getIpaCode());
                    sites.get(i).setIdx(crawlThreadIdx);
                    MyCrawl myCrawl = new MyCrawl(sites.get(i).getUrl(), 
                            sites.get(i).getIpaCode(), maxPages, 
                            seleniumDriver, crawlThreadIdx);                    
                    crawlExecCompletionService.submit(myCrawl);
                    numCrawlingRunning++;
                    numCrawlingThreadLaunched++;
                    crawlThreadIdx++;
                    //System.out.println(new Date().toString()+" LUNCHING NEW TH "+doc.getString("ipaCode")+" - NOW RUNNING CRAWLER THREADS "+String.valueOf(numCrawlingRunning));                        
                } else {
                    System.out.println(new Date().toString() + " No More URL to load, wait for " + String.valueOf(numCrawlingRunning) + " crawling threads to finish");
                }
                if (numCrawlingThreadLaunched >= concurrentCrawling && numCrawlingRunning > 0) {
                    //lancio il crawling di concurrentCrawling siti in contemporanea, poi come leggo la fine del crawling e scrivo i risultati e ne lancio un'altro
                    //oppure ho lanciato il crawling di tutti i siti della lista e non ne devo lanciare altri, quindi aspetto che finiscano quelli lanciati                    
                    CrawlingResult crawlingRes = null;
                    try {
                        //System.out.println(new Date().toString()+" WAITING FOR CRAWLER THREADS RUNNING "+String.valueOf(numCrawlingRunning));
                        crawlingRes = crawlExecCompletionService.take().get(10, TimeUnit.MINUTES);
                        numCrawlingRunning--;
                        //System.out.println(new Date().toString()+" CRAWLER END "+crawlingRes.getIpaCode()+", RUNNING NOW "+String.valueOf(numCrawlingRunning));
                    } catch (TimeoutException te) {
                        numCrawlingRunning--;
                        crawlThreadIdx = -1;
                        
                        Document document = new Document();
                        document.put("status", "CRAWLING_EXCEPTION");
                        document.put("evalId", evalId);
                        document.put("exception_type", "TIMEOUT_EXCEPTION");
                        document.put("when", new Date());                        
                        exceptionsCollection.insertOne(document);                        
                        continue;
                    } catch (Exception ex) {
                        crawlThreadIdx = -1;
                        String msg = "";
                        if(ex.getMessage() != null && !ex.getMessage().isEmpty()) {
                            msg = ex.getMessage();
                        } else {
                            msg = convertStackTraceToString(ex);
                        }
                        System.out.println("Exception ".concat(msg));
                       
                        Document document = new Document();
                        document.put("status", "CRAWLING_EXCEPTION");
                        document.put("evalId", evalId);
                        document.put("exception_type", "GENERIC_EXCEPTION");
                        document.put("exception_stacktrace", msg);
                        document.put("when", new Date());                        
                        exceptionsCollection.insertOne(document); 
                        continue;
                    }
                    if(crawlingRes != null) {
                        crawlThreadIdx = crawlingRes.getCrawlThreadIndex();
                    } else {
                        continue;
                    }
                    if (crawlingRes.getStatus().equals(status.EXCEPTION) &&
                            crawlingRes.getPagesList().size() < 50) {
                        
                        UpdateResult updateQueryResult = crawlerCollection.updateOne(
                                Filters.and(
                                        Filters.eq("evalId", evalId),
                                        Filters.eq("ipaCode", crawlingRes.getIpaCode())
                                ),
                                Updates.combine(
                                        Updates.set("status", "CRAWLING_EXCEPTION"),                                        
                                        Updates.set("exception_type", crawlingRes.getExceptionMsg().concat("_EXCEPTION")),
                                        Updates.set("exception_stacktrace", crawlingRes.getExceptionMsg()),
                                        Updates.set("exception_list", crawlingRes.getExceptions()),
                                        Updates.set("when", new Date())));
                        
                        Document document = new Document();
                        document.put("status", "CRAWLING_EXCEPTION");
                        document.put("ipaCode", crawlingRes.getIpaCode());
                        document.put("evalId", evalId);
                        document.put("exception_type", crawlingRes.getExceptionMsg().concat("_EXCEPTION"));
                        document.put("exception_stacktrace", crawlingRes.getExceptionMsg());
                        document.put("exception_list", crawlingRes.getExceptions());
                        document.put("when", new Date());
                        exceptionsCollection.insertOne(document);
                        continue;
                    } else if (crawlingRes.getPagesList().isEmpty()) {
                        UpdateResult updateQueryResult = crawlerCollection.updateOne(
                                Filters.and(
                                    Filters.eq("evalId", evalId),
                                    Filters.eq("ipaCode", crawlingRes.getIpaCode())
                                ),
                                Updates.combine(
                                        Updates.set("status", "CRAWLING_EXCEPTION"),
                                        //Updates.set("evalId", evalId),
                                        Updates.set("exception_type", "NO_PAGES_FOUND_EXCEPTION"),
                                        Updates.set("when", new Date())));
                        
                        Document document = new Document();
                        document.put("status", "CRAWLING_EXCEPTION");
                        document.put("evalId", evalId);
                        document.put("ipaCode", crawlingRes.getIpaCode());
                        document.put("exception_type", "NO_PAGES_FOUND_EXCEPTION");
                        document.put("when", new Date());
                        exceptionsCollection.insertOne(document);
                        continue;
                    }                    
                    if(crawlingRes.getSecurityDetails() == null) {
                        crawlingRes.setSecurityDetails(new SecurityDetails());
                    }
                    UpdateResult updateQueryResult = crawlerCollection.updateOne(
                            Filters.and(
                                    Filters.eq("evalId", evalId),
                                    Filters.eq("ipaCode", crawlingRes.getIpaCode())
                                ),
                            Updates.combine(
                                    Updates.unset("exception_type"),
                                    Updates.unset("exception_stacktrace"),
                                    Updates.unset("status_code"),
                                    Updates.set("status", "CRAWLING_END"),                                    
                                    Updates.set("url", crawlingRes.getRootUrl()),
                                    Updates.set("time_elapsed", crawlingRes.getTime_elapsed()),
                                    Updates.set("crawled_pages", crawlingRes.getPagesList()),
                                    Updates.set("crawled_pages_size", crawlingRes.getPagesList().size()),
                                    Updates.set("evaluated", false),                                    
                                    Updates.set("exception_list", crawlingRes.getExceptions()),                                    
                                    Updates.set("hasAccessibilityDeclaration", crawlingRes.getAccessibilityDeclaration().hasAccessibilityDeclaration()),                                    
                                    Updates.set("accessibilityDeclarationText", crawlingRes.getAccessibilityDeclaration().getAccessibilityDeclarationText()),                                    
                                    Updates.set("accessibilityDeclarationLink", crawlingRes.getAccessibilityDeclaration().getAccessibilityDeclarationLink()),                                    
                                    Updates.set("https", crawlingRes.getSecurityDetails().isHttps()),
                                    Updates.set("https_secure", SecurityState.SECURE.compareTo(crawlingRes.getSecurityDetails().getState()) == 0),                                    
                                    Updates.set("securityDetails", crawlingRes.getSecurityDetails()),                                    
                                    Updates.set("when", new Date())));                                    
                    numCrawlingFinished++;

                    if (numCrawlingFinished >= 30000) {
                        break listUrls;
                    }
                }
            } catch (WebDriverException | MalformedURLException ex) {
                crawlThreadIdx = sites.get(i).getIdx();
                String msg = "";
                if (ex.getMessage() != null && !ex.getMessage().isEmpty()) {
                    msg = ex.getMessage();
                } else {
                    msg = convertStackTraceToString(ex);
                }
                System.out.println("Exception2 ".concat(msg));
                String exceptionType = "";
                if(ex instanceof WebDriverException) {
                    exceptionType = "WEBDRIVER_EXCEPTION";
                } else if(ex instanceof MalformedURLException) {
                    exceptionType = "MALFORMED_URL";
                } else {
                    exceptionType = ex.getClass().toString();
                }
                
                Document document = new Document();
                document.put("status", "CRAWLING_EXCEPTION");
                document.put("evalId", evalId);
                document.put("exception_type", "WEBDRIVER_EXCEPTION");
                document.put("exception_stacktrace", msg);
                document.put("when", new Date());
                exceptionsCollection.insertOne(document);
            }
        }

        long endTime = Calendar.getInstance().getTimeInMillis();
        long elapsedTime = (endTime - startTime) / 1000;
        Document document = new Document();
        document.put("status", "ALL_CRAWL_END");
        document.put("numCralwedSites", numCrawlingFinished);
        document.put("time_in_sec", elapsedTime);
        document.put("when", new Date());        
        crawlerCollection.insertOne(document);

        crawlExecutor.shutdownNow();

        mongoClient.close();
        
        if (seleniumDriverPool != null) {
            int closeDriverFail = LageScaleCrawlingSeleniumMain.closeSeleniumDriverPool(seleniumDriverPool);
            if (closeDriverFail > 0) {
                document = new Document();
                document.put("status", "WEBDRIVER_FAIL_TO_QUIT");                
                document.put("evalId", evalId);
                document.put("driver_still_open", closeDriverFail);
                document.put("when", new Date());
                driverExceptionsCollection.insertOne(document);
            }

        }
        
        sendEmail("CRAWLING TASK END ".concat(String.valueOf(numCrawlingFinished)).concat(" sites in ").concat(String.valueOf(elapsedTime)).concat(" seconds"));

    }

    public static void sendEmail(String msg) {
        try {
            String host = "smtp-srv.isti.cnr.it";
            String from = "mauve-validation@isti.cnr.it";

            Properties props = new Properties();
            props.put("mail.transport.protocol", "smpt");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", host);
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", "587");

            Authenticator authenticator = new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(System.getenv("mailUser"), System.getenv("mailPsw"));
                }
            };

            Session session = Session.getInstance(props, authenticator);
            Message message = new MimeMessage(session);

            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("marco.manca@isti.cnr.it"));
            
            message.setSubject("MAUVE++ - Large Scale Crawling");

            message.setText(msg);

            Transport.send(message); //Invio del messaggio

            System.out.println("Sent message successfully....");
        } catch (MessagingException e) {
            System.out.println(e.getMessage());
        }
    }

    public static List<LoadURLSRes> loadURLS(MongoCollection<Document> crawlerCollection, String evalId) {
        Bson filterAnd1 = Filters.and(Filters.eq("evalId", evalId), Filters.eq("status", "CRAWLING_CONFIG"));
        FindIterable<Document> sites = crawlerCollection.find(filterAnd1);
        MongoCursor<Document> iterator = sites.iterator();
        List<LoadURLSRes> sitesList = new ArrayList<>(30000);
        while (iterator.hasNext()) {
            Document doc = iterator.next();
            sitesList.add(new LoadURLSRes(doc.getString("ipaCode"), doc.getString("url")));
        }
        iterator.close();
        return sitesList;
    }
    
    public static String convertStackTraceToString(Throwable throwable) {
        try (StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            return sw.toString();
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }    
    
    public static List<WebDriver> createSeleniumDriverPool(int maxConcurrentCrawlerRunning) {
        List<WebDriver> driverList = new ArrayList<>(maxConcurrentCrawlerRunning);
        int numOfIssuesInDriverCreation = 0;
        for (int i = 0; i < maxConcurrentCrawlerRunning; i++) {
            WebDriver webDriver = createWebDriver();
            if (webDriver != null) {
                driverList.add(webDriver);
            } else {
                numOfIssuesInDriverCreation++;
            }
        }
        for (int i = 0; i < numOfIssuesInDriverCreation; i++) {
            WebDriver webDriver = createWebDriver();
            if (webDriver != null) {
                driverList.add(webDriver);
            }
        }
        return driverList;
    }

    private static WebDriver createWebDriver() {
        try {
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
            if (System.getProperty("os.name") != null && isUnix(System.getProperty("os.name").toLowerCase())) {
                opt.addArguments("user-data-dir=/data/crawler/tmp/");
            }
            opt.addArguments("--headless");
            opt.addArguments("user-agent=\"--Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36\"");
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
            if (useProxy) {
                opt.addArguments("--proxy-server=" + proxyUrl + ":" + proxyPort);
            }
            opt.setExperimentalOption("prefs", prefs);

            LoggingPreferences logPrefs = new LoggingPreferences();
            logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
            opt.setCapability("goog:loggingPrefs", logPrefs);
            //https://stackoverflow.com/questions/47410707/invalid-capabilities-in-alwaysmatch-unhandledpromptbehavior-is-type-boolean-ins
            opt.setCapability("unhandledPromptBehavior", UnexpectedAlertBehaviour.ACCEPT);
            return new ChromeDriver(opt);

        } catch (org.openqa.selenium.SessionNotCreatedException ex) {
            return null;
        }
    }

    public static boolean isUnix(String os) {
        return (os.indexOf("nix") >= 0
                || os.indexOf("nux") >= 0
                || os.indexOf("aix") > 0);
    }

    public static int closeSeleniumDriverPool(List<WebDriver> seleniumDriverPool) {
        int closeDriverFail = 0;
        for (WebDriver driver : seleniumDriverPool) {
            try {
                driver.close();
                driver.quit();
            } catch (org.openqa.selenium.WebDriverException ex) {
                closeDriverFail++;
                Document document = new Document();
                document.put("status", "WEBDRIVER_FAIL_TO_QUIT");                
                document.put("evalId", evalId);
                document.put("exception_type", ex.getClass().toString());
                if(ex.getMessage() != null) {
                    document.put("exception_msg", ex.getMessage());
                }
                document.put("when", new Date());
                exceptionsCollection.insertOne(document);
            } 
        }
        return closeDriverFail;
    }
}
