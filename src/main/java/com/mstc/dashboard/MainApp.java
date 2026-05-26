package com.mstc.dashboard;

import javafx.application.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainApp extends Application {
    private TableView<AuctionLot> table = new TableView<>();
    private Label avgLabel = new Label("Weighted Average: 0.00");
    private ObservableList<AuctionLot> dataList = FXCollections.observableArrayList();
    private Label uniqueBiddersLabel = new Label("Species Specific Bidders ");

    // Map to store previous data for auto bid detection
    private Map<String, PreviousData> previousMap = new HashMap<>();

    private static class PreviousData {
        double previousPrice;
        String previousBidder;
        PreviousData(double price, String bidder) {
            this.previousPrice = price;
            this.previousBidder = bidder;
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        setupColumns();

        // Load YAML config (optional) from config/application.yaml
        java.nio.file.Path yamlPath = java.nio.file.Paths.get("config", "application.yaml");
        YamlConfig config = null;
        try {
            config = new YamlConfig(yamlPath);
            System.out.println("Loaded species config: " + config.getSpeciesList().size() + " entries.");
        } catch (Exception e) {
            e.printStackTrace();
            // continue without config if file not present or parse fails
            System.out.println("Species config not loaded, continuing without config.");
        }

        ChromeDriver driver = new ChromeDriver();
        ScraperService scraper = new ScraperService("interested_lots.txt", config);
        driver.get("https://www.mstcecommerce.com/auctionhome/kafd/index.jsp");

        Thread worker = new Thread(() -> {
            scraper.enterAuctionFloor(driver);
            Set<String> globalUniqueBidders = new HashSet<>();
            while (true) {
                List<AuctionLot> results = scraper.getFilteredLots(driver, globalUniqueBidders);

                final String MY_ID = "90633"; // Your ID from HTML
                // --- WEIGHTED AVERAGE CALCULATION ---
                double totalBidPriceSum = 0;
                double totalQuantitySum = 0;
                globalUniqueBidders.clear();

                for (AuctionLot lot : results) {
                    String lotNo = lot.lotNoProperty().get();
                    String currentH1 = scraper.getHighBidderId(driver, lot.getItemRefId());
                    if (currentH1 != null && !currentH1.equals("null")) {
                        lot.currentHighBidderProperty().set(currentH1);
                        lot.isMeH1Property().set(currentH1.equals(MY_ID));

                        System.out.println("Lot " + lotNo + " currentH1: " + currentH1 + " isMeH1: " + lot.isMeH1Property().get());

                        PreviousData prev = previousMap.get(lotNo);
                        if (prev != null) {
                            if (prev.previousBidder.equals(currentH1) && lot.priceValueProperty().get() > prev.previousPrice) {
                                lot.isAutoBidProperty().set(true);
                            } else {
                                lot.isAutoBidProperty().set(false);
                            }
                        } else {
                            lot.isAutoBidProperty().set(false);
                        }

                        // Update previous data
                        previousMap.put(lotNo, new PreviousData(lot.priceValueProperty().get(), currentH1));

                        if (!currentH1.equals(MY_ID)) {
                            globalUniqueBidders.add(currentH1);
                            lot.updateBidderActivity(lotNo, currentH1);
                        }
                    } else {
                        lot.isAutoBidProperty().set(false);
                        lot.isMeH1Property().set(false);
                    }

                    // Refresh active count
                    lot.refreshActiveCount(lotNo);

                    double price = lot.priceValueProperty().get();
                    double qty = lot.quantityProperty().get();

                    if (price > 0 && qty > 0) {
                        totalBidPriceSum += price * qty * 1.354;
                        totalQuantitySum += qty;
                    }
                }

                // Final Formula: (Sum of Bids / Sum of Qty) * 35.315
                double weightedAvg = (totalQuantitySum > 0) ? totalBidPriceSum / (totalQuantitySum * 35.315) : 0.0;

                Platform.runLater(() -> {
                    dataList.setAll(results);
                    table.refresh();
                    avgLabel.setText(String.format("Weighted Avg (Sum Bid / Sum Qty * 35.315): %.2f", weightedAvg));
                    uniqueBiddersLabel.setText("Species Specific Bidders (Excl. Me): " + globalUniqueBidders.size());
                });

                try {
                    refreshAuctionPage(driver);
                } catch (Exception e) {
                    break;
                }
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

        TableColumn<AuctionLot, Boolean> colAutoBid = new TableColumn<>("Is Auto Bid");
        colAutoBid.setCellValueFactory(d -> d.getValue().isAutoBidProperty());
        colAutoBid.setStyle("-fx-alignment: CENTER;");

        TableColumn<AuctionLot, Boolean> colMeH1 = new TableColumn<>("Am I H1?");
        colMeH1.setCellValueFactory(d -> d.getValue().isMeH1Property());
        colMeH1.setStyle("-fx-alignment: CENTER;");
        colMeH1.setCellFactory(column -> new TableCell<AuctionLot, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    if (item) {
                        setStyle("-fx-background-color: green;");
                    } else {
                        setStyle("-fx-background-color: lightcoral;");
                    }
                }
            }
        });

        table.getColumns().addAll(colLot, colQty, colBid, colCft, colActive, colAutoBid, colMeH1);
        table.setItems(dataList);

        // Highlight rows based on sold status and H1 bidder
        table.setRowFactory(tv -> new TableRow<AuctionLot>() {
            private ChangeListener<Boolean> soldListener;
            private ChangeListener<Boolean> h1Listener;

            private void updateStyle() {
                AuctionLot item = getItem();
                if (item != null) {
                    if (item.isSoldProperty().get()) {
                        if (item.isMeH1Property().get()) {
                            setStyle("-fx-background-color: green;");
                        } else {
                            setStyle("-fx-background-color: lightcoral;");
                        }
                    } else if (item.isNoBidProperty().get()) {
                        setStyle("-fx-background-color: #ffff00;");
                    } else {
                        setStyle("");
                    }
                } else {
                    setStyle("");
                }
            }

            @Override protected void updateItem(AuctionLot item, boolean empty) {
                super.updateItem(item, empty);

                // Remove old listeners
                if (soldListener != null) {
                    // Since item changes, we can't remove from old item, but listeners are per row
                }
                if (h1Listener != null) {
                    // Same
                }

                if (item != null) {
                    soldListener = new ChangeListener<Boolean>() {
                        @Override
                        public void changed(ObservableValue<? extends Boolean> obs, Boolean oldVal, Boolean newVal) {
                            Platform.runLater(() -> updateStyle());
                        }
                    };
                    item.isSoldProperty().addListener(soldListener);

                    h1Listener = new ChangeListener<Boolean>() {
                        @Override
                        public void changed(ObservableValue<? extends Boolean> obs, Boolean oldVal, Boolean newVal) {
                            Platform.runLater(() -> updateStyle());
                        }
                    };
                    item.isMeH1Property().addListener(h1Listener);

                    updateStyle();
                } else {
                    setStyle("");
                }
            }
        });
    }

    public static void main(String[] args) { launch(args); }

}