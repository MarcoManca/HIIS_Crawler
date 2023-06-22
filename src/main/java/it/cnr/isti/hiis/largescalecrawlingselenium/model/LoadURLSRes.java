package it.cnr.isti.hiis.largescalecrawlingselenium.model;

/**
 *
 * @author Marco Manca
 */
public class LoadURLSRes {
    private String ipaCode;
    private String url;
    private int idx;
    public LoadURLSRes(String ipaCode, String url) {
        this.ipaCode = ipaCode;
        this.url = url;
        this.idx = -1;
    }

    public String getIpaCode() {
        return ipaCode;
    }

    public String getUrl() {
        return url;
    }

    public int getIdx() {
        return idx;
    }

    public void setIdx(int idx) {
        this.idx = idx;
    }
    
    
}
