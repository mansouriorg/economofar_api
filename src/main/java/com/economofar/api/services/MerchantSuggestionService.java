package com.economofar.api.services;

import com.economofar.api.dtos.MerchantSuggestionResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Remembers how the user usually files the receipts of a shop (category and payment method),
 * so the validation screen can pre-fill them.
 *
 * Shop names are compared without case, accents, spaces or punctuation, and a name that
 * contains the other one (at least 5 characters) also counts, because OCR rarely reads
 * the same shop name twice in exactly the same way.
 */
@Service
@RequiredArgsConstructor
public class MerchantSuggestionService {

    private static final int MIN_CONTAINS_LENGTH = 5;
    private static final int MAX_MATCHES = 10;
    private static final int MAX_MERCHANT_LENGTH = 255;

    private static final Set<String> PLACEHOLDER_NAMES = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "inconnu"
    );

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public MerchantSuggestionResponse suggest(User user, String merchantName) {
        String merchant = merchantName == null ? "" : merchantName.trim();

        if (user == null || merchant.isEmpty() || merchant.length() > MAX_MERCHANT_LENGTH) {
            return new MerchantSuggestionResponse(merchant, null, null, 0);
        }

        String wanted = normalize(merchant);

        if (wanted.isEmpty() || PLACEHOLDER_NAMES.contains(wanted)) {
            return new MerchantSuggestionResponse(merchant, null, null, 0);
        }

        List<Ticket> matches = new ArrayList<>();

        for (Ticket ticket : ticketRepository.findTop300ByUserAndScanStatusOrderByPurchaseDateDesc(user, ScanStatus.SAVED)) {
            if (sameShop(wanted, normalize(ticket.getMerchantName()))) {
                matches.add(ticket);

                if (matches.size() >= MAX_MATCHES) {
                    break;
                }
            }
        }

        return new MerchantSuggestionResponse(
                merchant,
                mostCommon(matches.stream().map(Ticket::getCategory).toList()),
                mostCommon(matches.stream().map(Ticket::getPaymentMethod).toList()),
                matches.size()
        );
    }

    private boolean sameShop(String wanted, String candidate) {
        if (candidate.isEmpty() || PLACEHOLDER_NAMES.contains(candidate)) {
            return false;
        }

        if (wanted.equals(candidate)) {
            return true;
        }

        return Math.min(wanted.length(), candidate.length()) >= MIN_CONTAINS_LENGTH
                && (wanted.contains(candidate) || candidate.contains(wanted));
    }

    /**
     * Most frequent value. On a tie the most recent one wins, because the list is
     * ordered from the newest ticket to the oldest.
     */
    private String mostCommon(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> originals = new LinkedHashMap<>();

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            String key = value.trim().toLowerCase(Locale.ROOT);
            counts.merge(key, 1, Integer::sum);
            originals.putIfAbsent(key, value.trim());
        }

        String bestKey = null;
        int bestCount = 0;

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestKey = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        return bestKey == null ? null : originals.get(bestKey);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String withoutAccents = Normalizer
                .normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
