package com.mstc.dashboard;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.time.Duration;
import java.nio.file.*;
import java.util.*;

public class ScraperService {
    private Set<String> interestedLots;
    public static final int TARGET_ROW = 2;
    private final YamlConfig config;

    public ScraperService(String filePath, YamlConfig config) throws Exception {
        this.interestedLots = new HashSet<>(Files.readAllLines(Paths.get(filePath)));
        this.config = config;
    }

    /**
     * Handles the navigation flow from Login -> Dashboard -> Selection -> Floor
     */
    public void enterAuctionFloor(WebDriver driver) {
        // We set a 5-minute (300 seconds) timeout for the manual login process
        WebDriverWait loginWait = new WebDriverWait(driver, Duration.ofSeconds(300));
        WebDriverWait navWait = new WebDriverWait(driver, Duration.ofSeconds(20));
// 1. Locate and fill Username
        WebElement usernameField = loginWait.until(ExpectedConditions.visibilityOfElementLocated(By.id("userName")));
        usernameField.clear();
        usernameField.sendKeys("ABDULR12");

        // 2. Locate and fill Password
        WebElement passwordField = driver.findElement(By.id("password"));
        passwordField.clear();
        passwordField.sendKeys("amrahut@2zs");
        System.out.println("Flow Started: Waiting for manual login...");
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public List<AuctionLot> getFilteredLots(WebDriver driver,Set<String> globalUniqueBidders) {
        List<AuctionLot> activeLots = new ArrayList<>();
        try {
            // Refined XPath: Targets only rows with data cells
            List<WebElement> rows = driver.findElements(
                    By.xpath("//tr[starts-with(@id, 'tabrow') and count(td) > 0]")
            );

            for (WebElement row : rows) {
                try {
                    // Extract data based on confirmed column indices [cite: 1182, 1184, 1196]
                    String rawLotText = row.findElement(By.xpath("./td[1]")).getText().trim();
                    String lotNo = rawLotText.split("\\s+")[0];
                    lotNo=lotNo.replace(".0","");

                    // Extract species and type values from columns (adjust indices if your HTML differs)
                    String speciesVal = "";
                    try {
                        speciesVal = row.findElement(By.xpath("./td[3]")).getText().trim();
                    } catch (Exception e) { speciesVal = ""; }

                    String typeVal = "";
                    try {
                        typeVal = row.findElement(By.xpath("./td[4]")).getText().trim();
                    } catch (Exception e) { typeVal = ""; }

                   updateGlobalUniqueBidders(driver, lotNo, globalUniqueBidders, row, speciesVal, typeVal);
                    if (interestedLots.contains(lotNo)) {
                        String refId = row.getAttribute("id").replace("tabrow", "");

                        // TD6: Quantity [cite: 1183, 1196]
                        String qtyVal = row.findElement(By.xpath("./td[6]")).getText().trim();

                        // TD9: Base Price [cite: 1184, 1197]
                        String basePriceVal = row.findElement(By.xpath("./td[9]")).getText().trim();

                        // Span ID: lastbidXXXXXXX [cite: 1184, 1197]
                        String lastBidVal = driver.findElement(By.id("lastbid" + refId)).getText().trim();

                        // Check if sold
                        String rowClass = row.getAttribute("class");
                        boolean isSold = rowClass != null && rowClass.contains("soldbid");

                        System.out.println("Lot " + lotNo + " rowClass: " + rowClass + " isSold: " + isSold);

                        activeLots.add(new AuctionLot(lotNo, lastBidVal, basePriceVal, qtyVal,refId, isSold));
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    continue; }
            }
        } catch (Exception e) { /* Handle page transitions */ }
        return activeLots;
    }

    private void updateGlobalUniqueBidders(WebDriver driver, String lotNo, Set<String> globalUniqueBidders, WebElement row, String speciesVal, String typeVal) {

        // If config exists, only count unique bidders for lots that match species/type
        if (this.config != null) {
            boolean match = this.config.isSpeciesAndTypeMatch(speciesVal, typeVal);
            if (!match) {
                return;
            }
        }

        String currentH1 = getHighBidderId(driver,  row.getAttribute("id").replace("tabrow", "")); // Ensure AuctionLot stores refId
        if (currentH1 != null && !currentH1.equals("90633") && !currentH1.equals("null")) {
          
            globalUniqueBidders.add(currentH1);
        }
    }

    // Add this method inside ScraperService class
    public String getHighBidderId(WebDriver driver, String refId) {
        try {
            // Targets the hidden input 'high_buy' associated with the specific item_ref_id
            WebElement hiddenInput = driver.findElement(
                    By.xpath("//input[@name='item_ref_id' and @value='" + refId + "']/following-sibling::input[@name='high_buy']")
            );
            return hiddenInput.getAttribute("value");
        } catch (Exception e) {
            return "null";
        }
    }

}