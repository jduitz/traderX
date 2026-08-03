package finos.traderx.tradeservice.model;

public class PositionSnapshot {
  private Integer accountId;
  private Integer accountid;
  private String security;
  private Integer quantity;

  public Integer getAccountId() {
    return accountId != null ? accountId : accountid;
  }

  public void setAccountId(Integer accountId) {
    this.accountId = accountId;
  }

  public void setAccountid(Integer accountid) {
    this.accountid = accountid;
  }

  public String getSecurity() {
    return security;
  }

  public void setSecurity(String security) {
    this.security = security;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }
}
