package com.economofar.api.services;

import com.economofar.api.dtos.SpendingSplitResponse;
import com.economofar.api.dtos.SpendingSplitRuleRequest;
import com.economofar.api.dtos.SpendingSplitRuleResponse;
import com.economofar.api.models.Income;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.SpendingSplitRule;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.IncomeRepository;
import com.economofar.api.repositories.SpendingSplitRuleRepository;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the saved tickets of a month, the income declared for it and the rules of the person, and asks
 * SpendingSplitCalculator to split the spending into needs, wants and savings. The only thing this
 * service writes is the rules: the group of a category.
 */
@Service
@RequiredArgsConstructor
public class SpendingSplitService {

    static final int MAX_RULES = 200;
    static final String DEFAULT_GROUP = "DEFAULT";

    private final TicketRepository ticketRepository;
    private final IncomeRepository incomeRepository;
    private final SpendingSplitRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public SpendingSplitResponse split(User user, String month) {
        return split(user, month, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public SpendingSplitResponse split(User user, String month, LocalDate today) {
        requireUser(user);

        SpendingSplitCalculator.Period period;

        try {
            period = SpendingSplitCalculator.period(today, month);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        List<CashFlowCalculator.Source> sources = new ArrayList<>();

        for (Income income : incomeRepository.findByUserOrderByStartDateDescIdDesc(user)) {
            sources.add(new CashFlowCalculator.Source(
                    income.getAmount(),
                    income.getStartDate(),
                    income.isRecurring(),
                    income.getEndDate()
            ));
        }

        BigDecimal income = CashFlowCalculator.incomeFor(sources, period.month());

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                period.from().atStartOfDay(),
                period.to().atTime(LocalTime.MAX)
        );

        List<SpendingSplitCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            LocalDateTime purchaseDate = ticket.getPurchaseDate();

            if (purchaseDate == null) {
                continue;
            }

            entries.add(new SpendingSplitCalculator.Entry(
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    purchaseDate.toLocalDate()
            ));
        }

        Map<String, String> rules = new HashMap<>();

        for (SpendingSplitRule rule : ruleRepository.findByUser(user)) {
            rules.put(rule.getCategoryKey(), rule.getBucket());
        }

        return SpendingSplitCalculator.analyse(entries, rules, income, today, month);
    }

    /**
     * Puts a category in a group: "NEED", "WANT" or "EXCLUDED", or "DEFAULT" to take the rule away. The
     * same call twice gives the same result.
     */
    @Transactional
    public SpendingSplitRuleResponse setRule(User user, SpendingSplitRuleRequest request) {
        requireUser(user);

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category and a group are required");
        }

        String key = SpendingSplitCalculator.categoryKey(request.category());

        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category is required");
        }

        if (key.length() > SpendingSplitCalculator.MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The category name is too long");
        }

        String text = request.group() == null ? "" : request.group().trim().toUpperCase(Locale.ROOT);
        String group = text.equals(DEFAULT_GROUP) ? DEFAULT_GROUP : SpendingSplitCalculator.ruleGroup(text);

        if (group == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The group must be NEED, WANT, EXCLUDED or DEFAULT");
        }

        Optional<SpendingSplitRule> existing = ruleRepository.findByUserAndCategoryKey(user, key);

        if (group.equals(DEFAULT_GROUP)) {
            existing.ifPresent(ruleRepository::delete);

            return new SpendingSplitRuleResponse(key, DEFAULT_GROUP);
        }

        if (existing.isPresent()) {
            SpendingSplitRule rule = existing.get();

            rule.setBucket(group);
            rule.setUpdatedAt(LocalDateTime.now());
            ruleRepository.save(rule);

            return new SpendingSplitRuleResponse(key, group);
        }

        if (ruleRepository.countByUser(user) >= MAX_RULES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many rules: " + MAX_RULES + " at most");
        }

        try {
            ruleRepository.save(new SpendingSplitRule(user, key, group));
        } catch (DataIntegrityViolationException e) {
            // The same category was given a rule by another call at the same moment
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This category was changed at the same time, try again");
        }

        return new SpendingSplitRuleResponse(key, group);
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
    }
}
