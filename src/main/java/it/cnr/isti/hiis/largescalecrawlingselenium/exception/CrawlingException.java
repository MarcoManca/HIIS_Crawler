package it.cnr.isti.hiis.largescalecrawlingselenium.exception;

/**
 *
 * @author Marco Manca
 */
public class CrawlingException extends Exception{
    private String type;

    public CrawlingException(String message) {
        super(message);
        this.type = message;
    }

    public String getType() {
        return type;
    }
        
}
