package finos.traderx.ordermatcher.api;

public class InstrumentMetadata {
    private String instrumentKey;
    private String displayName;
    private String assetClass;
    private String securityType;
    private Boolean matured;
    private DebtEconomics debtEconomics;

    public String getInstrumentKey() { return instrumentKey; }
    public void setInstrumentKey(String instrumentKey) { this.instrumentKey = instrumentKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }
    public String getSecurityType() { return securityType; }
    public void setSecurityType(String securityType) { this.securityType = securityType; }
    public Boolean getMatured() { return matured; }
    public void setMatured(Boolean matured) { this.matured = matured; }
    public DebtEconomics getDebtEconomics() { return debtEconomics; }
    public void setDebtEconomics(DebtEconomics debtEconomics) { this.debtEconomics = debtEconomics; }

    public boolean isTreasury() {
        return "US_TREASURY".equals(assetClass) && "Debt".equals(securityType);
    }

    public static class DebtEconomics {
        private String maturityDate;
        public String getMaturityDate() { return maturityDate; }
        public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }
    }
}
