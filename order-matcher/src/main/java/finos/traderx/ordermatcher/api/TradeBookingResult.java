package finos.traderx.ordermatcher.api;

import java.math.BigDecimal;

public class TradeBookingResult {
    private BookedTrade trade;

    public BookedTrade getTrade() { return trade; }
    public void setTrade(BookedTrade trade) { this.trade = trade; }

    public static class BookedTrade {
        private String id;
        private String state;
        private BigDecimal price;
        private String rejectionReason;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    }
}
