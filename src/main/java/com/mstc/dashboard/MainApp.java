package com.mstc.dashboard;

import javafx.application.*;
import javafx.collections.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainApp extends Application {
    private TableView<AuctionLot> table = new TableView<>();
    private Label avgLabel = new Label("Weighted Average: 0.00");
    private ObservableList<AuctionLot> dataList = FXCollections.observableArrayList();
    private Label uniqueBiddersLabel = new Label("Global Unique Bidders ");

    @Override
    public void start(Stage stage) throws Exception {
        setupColumns();

        ChromeDriver driver = new ChromeDriver();
        ScraperService scraper = new ScraperService("interested_lots.txt");
        driver.get("https://www.mstcecommerce.com/auctionhome/kafd/index.jsp");

        Thread worker = new Thread(() -> {
            scraper.enterAuctionFloor(driver);
            Set<String> globalUniqueBidders = new HashSet<>();
            while (true) {
                List<AuctionLot> results = scraper.getFilteredLots(driver,globalUniqueBidders);

                 final String MY_ID = "90633"; // Your ID from HTML
                // --- WEIGHTED AVERAGE CALCULATION ---
                double totalBidPriceSum = 0;
                double totalQuantitySum = 0;
                globalUniqueBidders.clear();

                for (AuctionLot lot : results) {
                    // 1. Get current H1 from hidden field
                    String currentH1 = scraper.getHighBidderId(driver, lot.getItemRefId()); // Ensure AuctionLot stores refId
                    if (currentH1 != null && !currentH1.equals(MY_ID) && !currentH1.equals("null")) {
                        globalUniqueBidders.add(currentH1);
                        lot.updateBidderActivity(lot.lotNoProperty().toString(), currentH1);
                    }

                    // 2. Always refresh to expire bidders > 2 mins
                    lot.refreshActiveCount(lot.lotNoProperty().toString());
                    double price = lot.priceValueProperty().get();
                    double qty = lot.quantityProperty().get();

                    if (price > 0 && qty > 0) {
                        totalBidPriceSum += price*qty*1.354;
                        totalQuantitySum += qty;
                    }
                }

                // Final Formula: (Sum of Bids / Sum of Qty) * 35.315
                double weightedAvg = (totalQuantitySum > 0) ? totalBidPriceSum / (totalQuantitySum * 35.315)  : 0.0;

                Platform.runLater(() -> {
                    dataList.setAll(results);
                    avgLabel.setText(String.format("Weighted Avg (Sum Bid / Sum Qty * 35.315): %.2f", weightedAvg));
                    uniqueBiddersLabel.setText("Global Unique Bidders (Excl. Me): " + globalUniqueBidders.size());
                });

                try {
                    refreshAuctionPage(driver);
                     } catch (Exception e) { break; }
            }
        });
        worker.setDaemon(true);
        worker.start();

        stage.setScene(new Scene(new VBox(10, table, avgLabel,uniqueBiddersLabel), 850, 600));
        stage.setTitle("MSTC Auction Monitor");
        stage.show();
    }

    public void  refreshAuctionPage(ChromeDriver driver) {
        // Refresh the current page
        driver.navigate().refresh();

// Best practice: Wait for a specific element to ensure the DOM is ready
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("demoKafd")));
    }

    private void setupColumns() {
        TableColumn<AuctionLot, String> colLot = new TableColumn<>("Lot No");
        colLot.setCellValueFactory(d -> d.getValue().lotNoProperty());

        TableColumn<AuctionLot, Number> colQty = new TableColumn<>("Quantity");
        colQty.setCellValueFactory(d -> d.getValue().quantityProperty());

        TableColumn<AuctionLot, Number> colBid = new TableColumn<>("Bid/Base Price");
        colBid.setCellValueFactory(d -> d.getValue().priceValueProperty());

        TableColumn<AuctionLot, Number> colCft = new TableColumn<>("Unit CFT");
        colCft.setCellValueFactory(d -> d.getValue().cftPriceProperty());

        TableColumn<AuctionLot, Number> colActive = new TableColumn<>("Active Bidders (2m)");
        colActive.setCellValueFactory(d -> d.getValue().activeCountProperty());
        // Optional: Style this column to make it stand out
        colActive.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        table.getColumns().addAll(colLot, colQty, colBid, colCft, colActive);
        table.setItems(dataList);

        // Highlight yellow if using Base Value (No Bid)
        table.setRowFactory(tv -> new TableRow<AuctionLot>() {
            @Override protected void updateItem(AuctionLot item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null && item.isNoBidProperty().get()) setStyle("-fx-background-color: #ffff00;");
                else setStyle("");
            }
        });
    }

    public static void main(String[] args) { launch(args); }

}