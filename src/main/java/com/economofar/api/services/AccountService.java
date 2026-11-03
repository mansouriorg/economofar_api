package com.economofar.api.services;

import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.AppNotificationRepository;
import com.economofar.api.repositories.IncomeRepository;
import com.economofar.api.repositories.SavingsGoalRepository;
import com.economofar.api.repositories.SpendingEventRepository;
import com.economofar.api.repositories.SpendingEventTicketRepository;
import com.economofar.api.repositories.SpendingSplitRuleRepository;
import com.economofar.api.repositories.ShoppingListItemRepository;
import com.economofar.api.repositories.CategoryBudgetRepository;
import com.economofar.api.repositories.NotificationPreferenceRepository;
import com.economofar.api.repositories.SupportRequestRepository;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.repositories.UserBudgetRepository;
import com.economofar.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final UserBudgetRepository userBudgetRepository;
    private final IncomeRepository incomeRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final SpendingEventRepository spendingEventRepository;
    private final SpendingEventTicketRepository spendingEventTicketRepository;
    private final SpendingSplitRuleRepository spendingSplitRuleRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final AppNotificationRepository appNotificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    /**
     * Permanently deletes the account and ALL its data:
     * tickets + items, receipt images, budget, notifications, preferences, support requests.
     */
    @Transactional
    public void deleteAccount(User principal, String password) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe est obligatoire pour supprimer le compte");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe incorrect");
        }

        List<Ticket> tickets = ticketRepository.findByUserOrderByPurchaseDateDesc(user);

        Set<String> imageUrls = new HashSet<>();

        for (Ticket ticket : tickets) {
            if (ticket.getImageUrl() != null && !ticket.getImageUrl().isBlank()) {
                imageUrls.add(ticket.getImageUrl());
            }
        }

        ticketRepository.deleteByUser(user);
        userBudgetRepository.deleteByUser(user);
        incomeRepository.deleteByUser(user);
        savingsGoalRepository.deleteByUser(user);
        categoryBudgetRepository.deleteByUser(user);
        spendingEventTicketRepository.deleteByUser(user);
        spendingEventRepository.deleteByUser(user);
        spendingSplitRuleRepository.deleteByUser(user);
        shoppingListItemRepository.deleteByUser(user);
        appNotificationRepository.deleteByUser(user);
        notificationPreferenceRepository.deleteByUser(user);
        supportRequestRepository.deleteByUser(user);
        userRepository.delete(user);

        for (String imageUrl : imageUrls) {
            if (ticketRepository.countByImageUrl(imageUrl) == 0) {
                fileStorageService.deleteByUrl(imageUrl);
            }
        }
    }
}
