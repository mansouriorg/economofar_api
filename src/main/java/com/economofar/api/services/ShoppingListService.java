package com.economofar.api.services;

import com.economofar.api.dtos.PriceTrendResponse;
import com.economofar.api.dtos.ShoppingItemRequest;
import com.economofar.api.dtos.ShoppingItemUpdateRequest;
import com.economofar.api.dtos.ShoppingListResponse;
import com.economofar.api.models.ShoppingListItem;
import com.economofar.api.models.User;
import com.economofar.api.repositories.ShoppingListItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and changes the shopping list of a user. Every call answers with the whole list, priced
 * from the tickets of the user (see ShoppingListCalculator).
 *
 * The methods are not wrapped in one transaction on purpose: a failure of the price lookup must
 * never undo a change of the list.
 */
@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingListService.class);

    private final ShoppingListItemRepository shoppingListItemRepository;
    private final PriceTrackerService priceTrackerService;

    public ShoppingListResponse get(User user) {
        requireUser(user);
        return build(user);
    }

    public ShoppingListResponse add(User user, ShoppingItemRequest request) {
        requireUser(user);

        String name = ShoppingListCalculator.cleanName(request == null ? null : request.getName());
        int quantity = ShoppingListCalculator.checkQuantity(request == null ? null : request.getQuantity());

        List<ShoppingListItem> current = shoppingListItemRepository.findByUserOrderByIdAsc(user);
        ShoppingListItem existing = null;

        for (ShoppingListItem item : current) {
            if (ShoppingListCalculator.sameItem(item.getName(), name)) {
                existing = item;
                break;
            }
        }

        if (existing != null) {
            if (existing.isChecked()) {
                existing.setChecked(false);
                existing.setQuantity(quantity);
            } else {
                existing.setQuantity(Math.min(
                        ShoppingListCalculator.MAX_QUANTITY,
                        existing.getQuantity() + quantity
                ));
            }

            shoppingListItemRepository.save(existing);
        } else {
            if (current.size() >= ShoppingListCalculator.MAX_ITEMS) {
                throw new IllegalArgumentException("La liste est pleine (100 articles maximum). Retirez des articles pour en ajouter.");
            }

            shoppingListItemRepository.save(new ShoppingListItem(user, name, quantity));
        }

        return build(user);
    }

    public ShoppingListResponse update(User user, Long id, ShoppingItemUpdateRequest request) {
        requireUser(user);

        if (request == null || (request.getQuantity() == null && request.getChecked() == null)) {
            throw new IllegalArgumentException("Rien à modifier.");
        }

        ShoppingListItem item = shoppingListItemRepository
                .findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article introuvable"));

        if (request.getQuantity() != null) {
            item.setQuantity(ShoppingListCalculator.checkQuantity(request.getQuantity()));
        }

        if (request.getChecked() != null) {
            item.setChecked(request.getChecked());
        }

        shoppingListItemRepository.save(item);

        return build(user);
    }

    /**
     * Removing an item that is already gone is not an error: the answer is the current list.
     */
    public ShoppingListResponse remove(User user, Long id) {
        requireUser(user);

        Optional<ShoppingListItem> item = shoppingListItemRepository.findByIdAndUser(id, user);

        item.ifPresent(shoppingListItemRepository::delete);

        return build(user);
    }

    public ShoppingListResponse clearChecked(User user) {
        requireUser(user);

        shoppingListItemRepository.deleteAll(shoppingListItemRepository.findByUserAndCheckedTrue(user));

        return build(user);
    }

    private ShoppingListResponse build(User user) {
        List<ShoppingListCalculator.Entry> entries = new ArrayList<>();

        for (ShoppingListItem item : shoppingListItemRepository.findByUserOrderByIdAsc(user)) {
            entries.add(new ShoppingListCalculator.Entry(
                    item.getId(),
                    item.getName(),
                    item.getQuantity(),
                    item.isChecked()
            ));
        }

        List<PriceTrendResponse> prices = new ArrayList<>();

        try {
            prices.addAll(priceTrackerService.track(user).items());
        } catch (RuntimeException exception) {
            log.warn("Shopping list prices unavailable: {}", exception.getMessage());
        }

        return ShoppingListCalculator.build(entries, prices, LocalDate.now());
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
    }
}
