package com.economofar.api.services;

import com.economofar.api.services.DemoDataFactory.LineSpec;
import com.economofar.api.services.DemoDataFactory.ReceiptSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns the receipts of the demo account into the inputs that the calculators take, the way the
 * services do with the saved tickets.
 */
final class DemoInputs {

    private DemoInputs() {
    }

    static UUID id(int index) {
        return new UUID(0L, index + 1L);
    }

    static List<RepurchaseCalculator.Line> repurchaseLines(List<ReceiptSpec> receipts) {
        List<RepurchaseCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < receipts.size(); i++) {
            ReceiptSpec receipt = receipts.get(i);

            for (LineSpec line : receipt.lines()) {
                lines.add(new RepurchaseCalculator.Line(
                        id(i).toString(), line.name(), receipt.merchant(), receipt.purchaseDate().toLocalDate(),
                        line.quantity(), line.unitPrice(), line.total()
                ));
            }
        }

        return lines;
    }

    static List<ItemPricesCalculator.Line> itemPriceLines(List<ReceiptSpec> receipts) {
        List<ItemPricesCalculator.Line> lines = new ArrayList<>();

        for (int i = 0; i < receipts.size(); i++) {
            ReceiptSpec receipt = receipts.get(i);

            for (LineSpec line : receipt.lines()) {
                lines.add(new ItemPricesCalculator.Line(
                        id(i).toString(), line.name(), receipt.merchant(), receipt.purchaseDate().toLocalDate(),
                        line.quantity(), line.unitPrice(), line.total()
                ));
            }
        }

        return lines;
    }

    static List<ShopsCalculator.Row> shopRows(List<ReceiptSpec> receipts) {
        List<ShopsCalculator.Row> rows = new ArrayList<>();

        for (int i = 0; i < receipts.size(); i++) {
            ReceiptSpec receipt = receipts.get(i);

            rows.add(new ShopsCalculator.Row(
                    id(i).toString(), receipt.merchant(), receipt.category(), receipt.total(),
                    receipt.purchaseDate().toLocalDate()
            ));
        }

        return rows;
    }
}
