package com.economofar.api.services;

import com.economofar.api.dtos.IncomeRequest;
import com.economofar.api.dtos.IncomeResponse;
import com.economofar.api.models.Income;
import com.economofar.api.models.User;
import com.economofar.api.repositories.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Declared incomes: a salary, a rent received, a one-off gift...
 */
@Service
@RequiredArgsConstructor
public class IncomeService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    private static final int MAX_INCOMES = 50;
    private static final int MAX_NAME_LENGTH = 60;

    private final IncomeRepository incomeRepository;

    @Transactional(readOnly = true)
    public List<IncomeResponse> list(User user) {
        LocalDate today = LocalDate.now();

        return incomeRepository
                .findByUserOrderByStartDateDescIdDesc(user)
                .stream()
                .map(income -> toResponse(income, today))
                .toList();
    }

    @Transactional
    public IncomeResponse create(User user, IncomeRequest request) {
        LocalDate today = LocalDate.now();

        if (incomeRepository.countByUser(user) >= MAX_INCOMES) {
            throw bad("Vous avez atteint la limite de " + MAX_INCOMES + " revenus");
        }

        Fields fields = read(request, today);

        Income income = new Income(
                user,
                fields.name(),
                fields.amount(),
                fields.startDate(),
                fields.recurring(),
                fields.endDate()
        );

        return toResponse(incomeRepository.save(income), today);
    }

    @Transactional
    public IncomeResponse update(User user, Long id, IncomeRequest request) {
        LocalDate today = LocalDate.now();
        Income income = findOwned(user, id);

        Fields fields = read(request, today);

        income.update(
                fields.name(),
                fields.amount(),
                fields.startDate(),
                fields.recurring(),
                fields.endDate()
        );

        return toResponse(incomeRepository.save(income), today);
    }

    @Transactional
    public void delete(User user, Long id) {
        incomeRepository.delete(findOwned(user, id));
    }

    private Income findOwned(User user, Long id) {
        return incomeRepository
                .findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revenu introuvable"));
    }

    private record Fields(String name, BigDecimal amount, LocalDate startDate, boolean recurring, LocalDate endDate) {
    }

    private static Fields read(IncomeRequest request, LocalDate today) {
        String name = cleanName(request);
        BigDecimal amount = cleanAmount(request);
        boolean recurring = request.getRecurring() != null && request.getRecurring();
        LocalDate startDate = parseStartDate(request.getDate(), today);
        LocalDate endDate = recurring ? parseEndDate(request.getEndDate(), startDate, today) : null;

        return new Fields(name, amount, startDate, recurring, endDate);
    }

    private static IncomeResponse toResponse(Income income, LocalDate today) {
        boolean counts = CashFlowCalculator.counts(
                new CashFlowCalculator.Source(
                        income.getAmount(),
                        income.getStartDate(),
                        income.isRecurring(),
                        income.getEndDate()
                ),
                YearMonth.from(today)
        );

        return new IncomeResponse(
                income.getId(),
                income.getName(),
                income.getAmount(),
                income.getStartDate().toString(),
                income.isRecurring(),
                income.getEndDate() == null ? null : income.getEndDate().toString(),
                counts
        );
    }

    private static String cleanName(IncomeRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw bad("Le nom du revenu est obligatoire");
        }

        String name = request.getName().trim();

        if (name.length() > MAX_NAME_LENGTH) {
            throw bad("Le nom du revenu est trop long");
        }

        return name;
    }

    private static BigDecimal cleanAmount(IncomeRequest request) {
        BigDecimal raw = request == null ? null : request.getAmount();

        if (raw == null) {
            throw bad("Le montant du revenu est obligatoire");
        }

        if (raw.scale() > 100 || raw.scale() < -100) {
            throw bad("Le montant est invalide");
        }

        if (raw.abs().compareTo(MAX_AMOUNT) >= 0) {
            throw bad("Le montant est trop élevé");
        }

        BigDecimal rounded = raw.setScale(2, RoundingMode.HALF_UP);

        if (rounded.signum() <= 0) {
            throw bad("Le montant du revenu doit être supérieur à 0");
        }

        return rounded;
    }

    private static LocalDate parseStartDate(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return today;
        }

        LocalDate date;

        try {
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw bad("La date du revenu est invalide");
        }

        if (date.isBefore(today.minusYears(20))) {
            throw bad("La date du revenu est trop ancienne");
        }

        if (date.isAfter(today.plusYears(1))) {
            throw bad("La date du revenu est trop lointaine");
        }

        return date;
    }

    private static LocalDate parseEndDate(String raw, LocalDate startDate, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        LocalDate date;

        try {
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw bad("La date de fin est invalide");
        }

        if (date.isBefore(startDate)) {
            throw bad("La date de fin doit être après la date de début");
        }

        if (date.isAfter(today.plusYears(50))) {
            throw bad("La date de fin est trop lointaine");
        }

        return date;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
