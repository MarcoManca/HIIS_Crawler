package it.cnr.isti.hiis.largescalecrawlingselenium.model;

/**
 *
 * @author Marco Manca
 */
public class AccessibilityDeclaration {
    private final boolean hasAccessibilityDeclaration;
    private final String accessibilityDeclarationText;
    private final String accessibilityDeclarationLink;

    public AccessibilityDeclaration(boolean hasAccessibilityDeclaration, 
            String accessibilityDeclarationText, String accessibilityDeclarationLink) {
        this.hasAccessibilityDeclaration = hasAccessibilityDeclaration;
        this.accessibilityDeclarationText = accessibilityDeclarationText;
        this.accessibilityDeclarationLink = accessibilityDeclarationLink;
    }

    public boolean hasAccessibilityDeclaration() {
        return hasAccessibilityDeclaration;
    }

    public String getAccessibilityDeclarationText() {
        return accessibilityDeclarationText;
    }

    public String getAccessibilityDeclarationLink() {
        return accessibilityDeclarationLink;
    }
    
    
}
