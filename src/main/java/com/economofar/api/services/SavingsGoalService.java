package com.economofar.api.services;

import com.economofar.api.dtos.SavingsContributionRequest;
import com.economofar.api.dtos.SavingsContributionResponse;
import com.economofar.api.dtos.SavingsGoalRequest;
import com.economofar.api.dtos.SavingsGoalResponse;
import com.economofar.api.models.SavingsContribution;
import com.economofar.api.models.SavingsGoal;
import com.economofar.api.models.User;
import com.economofar.api.repositories.SavingsContributionRepository;
import com.economofar.api.repositories.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Savings goals and their contributions.
 */
@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    private static final int MAX_GOALS = 30;
    private static final int MAX_CONTRIBUTIONS = 1000;
    private static final int RECENT_CONTRIBUTIONS = 30;
    private static final Pattern ICON_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,40}");

    private final SavingsGoalRepository savingsGoalRepository;
    private final SavingsContributionRepository savingsContributionRepository;

    @Transactional(readOnly = true)
    public List<SavingsGoalResponse> list(User user) {
        LocalDate today = LocalDate.now();

        return savingsGoalRepository
                .findByUserOrderByCreatedAtAsc(user)
                .stream()
                .map(goal -> toResponse(goal, today))
                .toList();
    }

    @Transactional
    public SavingsGoalResponse create(User user, SavingsGoalRequest request) {
        LocalDate today = LocalDate.now();

        if (savingsGoalRepository.countByUser(user) >= MAX_GOALS) {
            throw bad("Vous avez atteint la limite de " + MAX_GOALS + " objectifs");
        }

        String name = cleanName(request);
        BigDecimal target = cleanAmount(request == null ? null : request.getTargetAmount(), "Le montant à atteindre doit être supérieur à 0");
        LocalDate deadline = parseDeadline(request, today, null);
        String icon = cleanIcon(request);

        SavingsGoal goal = savingsGoalRepository.save(new SavingsGoal(user, name, target, deadline, icon));

        return toResponse(goal, today);
    }

    @Transactional
    public SavingsGoalResponse update(User user, Long id, SavingsGoalRequest request) {
        LocalDate today = LocalDate.now();
        SavingsGoal goal = findOwned(user, id);

        String name = cleanName(request);
        BigDecimal target = cleanAmount(request == null ? null : request.getTargetAmount(), "Le montant à atteindre doit être supérieur à 0");
        LocalDate deadline = parseDeadline(request, today, goal.getDeadline());
        String icon = cleanIcon(request);

        goal.update(name, target, deadline, icon);

        return toResponse(savingsGoalRepository.save(goal), today);
    }

    @Transactional
    public void delete(User user, Long id) {
        savingsGoalRepository.delete(findOwned(user, id));
    }

    @Transactional
    public SavingsGoalResponse addContribution(User user, Long id, SavingsContributionRequest request) {
        LocalDate today = LocalDate.now();
        SavingsGoal goal = findOwned(user, id);

        if (request == null) {
            throw bad("Le montant est obligatoire");
        }

        BigDecimal amount = cleanSignedAmount(request.getAmount());
        LocalDate date = parseContributionDate(request.getDate(), today);
        String note = cleanNote(request.getNote());

        if (goal.getContributions().size() >= MAX_CONTRIBUTIONS) {
            throw bad("Cet objectif contient trop de versements");
        }

        if (savedAmount(goal).add(amount).signum() < 0) {
            throw bad("Le montant retiré dépasse l'épargne de cet objectif");
        }

        SavingsContribution contribution = savingsContributionRepository.save(
                new SavingsContribution(goal, amount, date, note)
        );

        goal.getContributions().add(contribution);
        goal.touch();

        return toResponse(goal, today);
    }

    @Transactional
    public SavingsGoalResponse deleteContribution(User user, Long id, Long contributionId) {
        LocalDate today = LocalDate.now();
        SavingsGoal goal = findOwned(user, id);

        SavingsContribution found = goal.getContributions()
                .stream()
                .filter(contribution -> contribution.getId() != null && contribution.getId().equals(contributionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versement introuvable"));

        if (savedAmount(goal).subtract(found.getAmount()).signum() < 0) {
            throw bad("Impossible de supprimer ce versement : l'épargne deviendrait négative");
        }

        goal.getContributions().remove(found);
        savingsContributionRepository.delete(found);
        goal.touch();

        return toResponse(goal, today);
    }

    private SavingsGoal findOwned(User user, Long id) {
        return savingsGoalRepository
                .findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif introuvable"));
    }

    private static BigDecimal savedAmount(SavingsGoal goal) {
        BigDecimal saved = BigDecimal.ZERO;

        for (SavingsContribution contribution : goal.getContributions()) {
            saved = saved.add(contribution.getAmount());
        }

        return saved;
    }

    private SavingsGoalResponse toResponse(SavingsGoal goal, LocalDate today) {
        List<SavingsGoalCalculator.Deposit> deposits = new ArrayList<>();

        for (SavingsContribution contribution : goal.getContributions()) {
            deposits.add(new SavingsGoalCalculator.Deposit(contribution.getAmount(), contribution.getContributedOn()));
        }

        SavingsGoalCalculator.Progress progress = SavingsGoalCalculator.compute(
                goal.getTargetAmount(),
                goal.getDeadline(),
                deposits,
                today
        );

        List<SavingsContributionResponse> recent = goal.getContributions()
                .stream()
                .sorted(
                        Comparator.comparing(SavingsContribution::getContributedOn).reversed()
                                .thenComparing(
                                        contribution -> contribution.getId() == null ? 0L : contribution.getId(),
                                        Comparator.reverseOrder()
                                )
                )
                .limit(RECENT_CONTRIBUTIONS)
                .map(contribution -> new SavingsContributionResponse(
                        contribution.getId(),
                        contribution.getAmount(),
                        contribution.getContributedOn().toString(),
                        contribution.getNote()
                ))
                .toList();

        return new SavingsGoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getIcon(),
                goal.getTargetAmount(),
                progress.saved(),
                progress.remaining(),
                progress.percent(),
                goal.getDeadline() == null ? null : goal.getDeadline().toString(),
                progress.daysLeft(),
                progress.monthlyNeeded(),
                progress.monthlyPace(),
                progress.projectedDate() == null ? null : progress.projectedDate().toString(),
                progress.status(),
                goal.getContributions().size(),
                recent
        );
    }

    private static String cleanName(SavingsGoalRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw bad("Le nom de l'objectif est obligatoire");
        }

        String name = request.getName().trim();

        if (name.length() > 100) {
            throw bad("Le nom de l'objectif est trop long");
        }

        return name;
    }

    private static String cleanIcon(SavingsGoalRequest request) {
        if (request == null || request.getIcon() == null || request.getIcon().isBlank()) {
            return null;
        }

        String icon = request.getIcon().trim();

        if (!ICON_PATTERN.matcher(icon).matches()) {
            throw bad("L'icône est invalide");
        }

        return icon;
    }

    /**
     * Null when no date was sent. A new date must not be in the past, but the date already
     * saved on the goal can be sent again unchanged.
     */
    private static LocalDate parseDeadline(SavingsGoalRequest request, LocalDate today, LocalDate current) {
        if (request == null || request.getDeadline() == null || request.getDeadline().isBlank()) {
            return null;
        }

        LocalDate deadline;

        try {
            deadline = LocalDate.parse(request.getDeadline().trim());
        } catch (DateTimeParseException exception) {
            throw bad("La date limite est invalide");
        }

        if (deadline.isBefore(today) && !deadline.equals(current)) {
            throw bad("La date limite ne peut pas être dans le passé");
        }

        if (deadline.isAfter(today.plusYears(50))) {
            throw bad("La date limite est trop lointaine");
        }

        return deadline;
    }

    private static LocalDate parseContributionDate(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return today;
        }

        LocalDate date;

        try {
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw bad("La date du versement est invalide");
        }

        if (date.isAfter(today)) {
            throw bad("La date du versement ne peut pas être dans le futur");
        }

        if (date.isBefore(today.minusYears(10))) {
            throw bad("La date du versement est trop ancienne");
        }

        return date;
    }

    private static String cleanNote(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String note = raw.trim();

        if (note.length() > 200) {
            throw bad("La note est trop longue");
        }

        return note;
    }

    private static BigDecimal cleanAmount(BigDecimal raw, String emptyMessage) {
        BigDecimal amount = cleanSignedAmount(raw, emptyMessage);

        if (amount.signum() <= 0) {
            throw bad(emptyMessage);
        }

        return amount;
    }

    private static BigDecimal cleanSignedAmount(BigDecimal raw) {
        return cleanSignedAmount(raw, "Le montant est obligatoire");
    }

    private static BigDecimal cleanSignedAmount(BigDecimal raw, String emptyMessage) {
        if (raw == null) {
            throw bad(emptyMessage);
        }

        if (raw.scale() > 100 || raw.scale() < -100) {
            throw bad("Le montant est invalide");
        }

        if (raw.abs().compareTo(MAX_AMOUNT) >= 0) {
            throw bad("Le montant est trop élevé");
        }

        BigDecimal rounded = raw.setScale(2, RoundingMode.HALF_UP);

        if (rounded.signum() == 0) {
            throw bad(emptyMessage);
        }

        return rounded;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
