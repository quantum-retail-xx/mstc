package com.mstc.dashboard;

import javafx.beans.property.*;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionLot {
    private final StringProperty lotNo;
    private final DoubleProperty priceValue; // Holds Last Bid or Base Price
    private final DoubleProperty quantity;
    private final DoubleProperty cftPrice;
    private final BooleanProperty isNoBid;
    private final String itemRefId;

    // Tracks BidderID -> Last Seen Timestamp for 2-minute rolling window
    private final ConcurrentHashMap<String, Instant> activeBiddersMap = new ConcurrentHashMap<>();
    // Structure: Map<LotNumber, Map<BidderID, LastActiveTime>>
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Instant>> lotToBiddersMap = new ConcurrentHashMap<>();
    private final IntegerProperty activeCount = new SimpleIntegerProperty(0);

    public AuctionLot(String lotNo, String lastBidStr, String basePriceStr, String qtyStr, String itemRefId) {
        this.lotNo = new SimpleStringProperty(lotNo);
        this.itemRefId = itemRefId;

        // Check if there is a bid in the 'lastbid' span
        boolean noBid = (lastBidStr == null || lastBidStr.equals("-") || lastBidStr.trim().isEmpty());
        this.isNoBid = new SimpleBooleanProperty(noBid);

        // Fallback Logic: Use Base Price (9th TD) if no bid exists
        double price = parseSafe(noBid ? basePriceStr : lastBidStr);
        double qty = parseSafe(qtyStr);

        this.priceValue = new SimpleDoubleProperty(price);
        this.quantity = new SimpleDoubleProperty(qty);

        // Row-level CFT calculation: (Price * 1.354) / 35.315
        this.cftPrice = new SimpleDoubleProperty(price > 0 ? (price * 1.354) / 35.315 : 0.0);
    }

    private double parseSafe(String val) {
        if (val == null) return 0.0;
        // Trim and remove commas/MSTC markers
        String clean = val.replace(",", "").replace("-", "").trim();
        try {
            return clean.isEmpty() ? 0.0 : Double.parseDouble(clean);
        } catch (Exception e) { return 0.0; }
    }

    public StringProperty lotNoProperty() { return lotNo; }
    public DoubleProperty priceValueProperty() { return priceValue; }
    public DoubleProperty quantityProperty() { return quantity; }
    public DoubleProperty cftPriceProperty() { return cftPrice; }
    public BooleanProperty isNoBidProperty() { return isNoBid; }
    public IntegerProperty activeCountProperty() { return activeCount; }

    public void updateBidderActivity(String lotNo, String bidderId) {
        if(bidderId.equals("90633")){
            return;
        }
        // 1. Get (or create) the map for this specific lot
        ConcurrentHashMap<String, Instant> biddersForThisLot = lotToBiddersMap.computeIfAbsent(
                lotNo, k -> new ConcurrentHashMap<>()
        );

        // 2. Mark this specific bidder as active NOW for this lot
                biddersForThisLot.put(bidderId, Instant.now());
        System.out.println("bidder for lot "+lotNo+"  is :"+bidderId);
        lotToBiddersMap.put(lotNo,biddersForThisLot);
    }

    public void refreshActiveCount(String lotNo) {
        ConcurrentHashMap<String, Instant> biddersForThisLot = lotToBiddersMap.get(lotNo);

        if (biddersForThisLot != null) {
            Instant expiry = Instant.now().minusSeconds(150);

            // 3. Remove inactive bidders ONLY from this lot's list

            biddersForThisLot.entrySet().removeIf(entry -> entry.getValue().isBefore(expiry));

            // 4. Print the exact mapping
            System.out.println("Lot: " + lotNo + " | Active Bidders: " + biddersForThisLot.keySet());
            activeCount.set(biddersForThisLot.size());
        }
    }

    public String getItemRefId() {
        return itemRefId;
    }
}