package com.economofar.api.services;

import com.economofar.api.dtos.CashFlowMonthResponse;
import com.economofar.api.dtos.CashFlowResponse;
import com.economofar.api.dtos.CategoryBudgetResponse;
import com.economofar.api.dtos.HealthComponentResponse;
import com.economofar.api.dtos.HealthScoreResponse;
import com.economofar.api.dtos.RecurringExpensesResponse;
import com.economofar.api.dtos.SavingsGoalResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Financial health score, from 0 to 100, made of five parts:
 *
 * savings    (35)  share of the income that is kept over the finished months that hold both an income
 *                  and some spending: 0 % or less gives 0, 20 % or more gives 100
 * budget     (20)  finished months within the monthly budget; 10 % over the budget costs 20 points
 * categories (15)  category limits this month: up to 80 % used gives 100, up to 100 % gives 70,
 *                  up to 120 % gives 30, more gives 0
 * goals      (15)  savings goals: completed or on track 100, no deadline 70, not started 50,
 *                  behind 40, overdue 0
 * recurring  (15)  monthly cost of the regular payments against the average monthly income (finished
 *                  months with an income): up to 30 % gives 100, 70 % or more gives 0
 *
 * A part that cannot be computed yet is left out and the weights of the others are rescaled.
 * The score itself needs at least two parts.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class HealthScoreCalculator {

    public static final String SAVINGS = "savings";
    public static final String BUDGET = "budget";
    public static final String CATEGORIES = "categories";
    public static final String GOALS = "goals";
    public static final String RECURRING = "recurring";

    public static final int MIN_COMPONENTS = 2;
    public static final int MAX_TIPS = 3;
    public static final int TIP_BELOW = 80;

    static final int WEIGHT_SAVINGS = 35;
    static final int WEIGHT_BUDGET = 20;
    static final int WEIGHT_CATEGORIES = 15;
    static final int WEIGHT_GOALS = 15;
    static final int WEIGHT_RECURRING = 15;

    static final double GOOD_SAVINGS_RATE = 20.0;
    static final double LIGHT_RECURRING_SHARE = 30.0;
    static final double HEAVY_RECURRING_SHARE = 70.0;

    private HealthScoreCalculator() {
    }

    public record Input(
            Double savingsRate,
            int savingsMonths,
            BigDecimal monthlyBudget,
            List<BigDecimal> finishedMonthExpenses,
            List<Double> categoryPercentUsed,
            List<String> goalStatuses,
            BigDecimal recurringMonthly,
            BigDecimal averageIncome
    ) {
    }

    public static Input inputFrom(
            CashFlowResponse flow,
            Double monthlyBudget,
            List<CategoryBudgetResponse> categoryBudgets,
            List<SavingsGoalResponse> goals,
            RecurringExpensesResponse recurring
    ) {
        Double savingsRate = null;
        int savingsMonths = 0;
        BigDecimal averageIncome = null;
        List<BigDecimal> expenses = new ArrayList<>();

        if (flow != null && flow.months() != null) {
            BigDecimal incomeSum = BigDecimal.ZERO;
            BigDecimal incomeKept = BigDecimal.ZERO;
            BigDecimal spentKept = BigDecimal.ZERO;
            int incomeMonths = 0;

            for (CashFlowMonthResponse month : flow.months()) {
                if (month == null || month.current()) {
                    continue;
                }

                boolean hasIncome = month.income() != null && month.income().signum() > 0;
                boolean hasSpending = month.expenses() != null && month.expenses().signum() > 0;

                if (hasIncome) {
                    incomeMonths++;
                    incomeSum = incomeSum.add(month.income());
                }

                if (hasSpending) {
                    expenses.add(month.expenses());
                }

                if (hasIncome && hasSpending) {
                    savingsMonths++;
                    incomeKept = incomeKept.add(month.income());
                    spentKept = spentKept.add(month.expenses());
                }
            }

            if (incomeMonths > 0) {
                averageIncome = incomeSum.divide(BigDecimal.valueOf(incomeMonths), 2, RoundingMode.HALF_UP);
            }

            if (savingsMonths > 0) {
                savingsRate = incomeKept.subtract(spentKept)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(incomeKept, 1, RoundingMode.HALF_UP)
                        .doubleValue();
            }
        }

        BigDecimal budget = null;

        if (monthlyBudget != null && !monthlyBudget.isNaN() && !monthlyBudget.isInfinite() && monthlyBudget > 0) {
            budget = BigDecimal.valueOf(monthlyBudget);
        }

        List<Double> percents = new ArrayList<>();

        if (categoryBudgets != null) {
            for (CategoryBudgetResponse categoryBudget : categoryBudgets) {
                if (categoryBudget != null) {
                    percents.add(categoryBudget.percentUsed());
                }
            }
        }

        List<String> statuses = new ArrayList<>();

        if (goals != null) {
            for (SavingsGoalResponse goal : goals) {
                if (goal != null && goal.status() != null) {
                    statuses.add(goal.status());
                }
            }
        }

        BigDecimal recurringMonthly = recurring == null ? null : recurring.monthlyTotal();

        return new Input(
                savingsRate,
                savingsMonths,
                budget,
                expenses,
                percents,
                statuses,
                recurringMonthly,
                averageIncome
        );
    }

    public static HealthScoreResponse compute(Input input) {
        List<HealthComponentResponse> components = new ArrayList<>();

        components.add(savings(input));
        components.add(budget(input));
        components.add(categories(input));
        components.add(goals(input));
        components.add(recurring(input));

        int available = 0;
        int weightSum = 0;
        long weighted = 0;

        for (HealthComponentResponse component : components) {
            if (component.score() != null) {
                available++;
                weightSum += component.weight();
                weighted += (long) component.score() * component.weight();
            }
        }

        Integer score = null;

        if (available >= MIN_COMPONENTS && weightSum > 0) {
            score = BigDecimal.valueOf(weighted)
                    .divide(BigDecimal.valueOf(weightSum), 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        return new HealthScoreResponse(score, level(score), available, components, tips(components));
    }

    public static String level(Integer score) {
        if (score == null) {
            return "none";
        }

        if (score >= 80) {
            return "excellent";
        }

        if (score >= 60) {
            return "good";
        }

        if (score >= 40) {
            return "watch";
        }

        return "fragile";
    }

    private static HealthComponentResponse savings(Input input) {
        String title = "Épargne";
        Double rate = input.savingsRate();

        if (rate == null || rate.isNaN() || rate.isInfinite()) {
            return missing(SAVINGS, title, WEIGHT_SAVINGS,
                    "Ajoutez vos revenus et validez des tickets sur un mois complet pour calculer le taux d'épargne.");
        }

        int score = clamp(rate * 100.0 / GOOD_SAVINGS_RATE);
        String months = input.savingsMonths() > 0 ? " sur " + months(input.savingsMonths()) : "";

        String detail;
        String tip = null;

        if (rate < 0) {
            detail = "Vos dépenses dépassent vos revenus (" + oneDecimal(rate) + " %)" + months + ".";
        } else {
            detail = "Taux d'épargne moyen : " + oneDecimal(rate) + " %" + months + ".";
        }

        if (rate < 0) {
            tip = "Vos dépenses dépassent vos revenus : repérez les catégories à réduire.";
        } else if (rate == 0) {
            tip = "Vous dépensez tout ce que vous gagnez : mettez un petit montant de côté chaque mois.";
        } else if (score < TIP_BELOW) {
            tip = "Essayez de mettre de côté au moins 20 % de vos revenus chaque mois.";
        }

        return new HealthComponentResponse(SAVINGS, title, score, WEIGHT_SAVINGS, detail, tip);
    }

    private static HealthComponentResponse budget(Input input) {
        String title = "Budget mensuel";
        BigDecimal budget = input.monthlyBudget();

        if (budget == null || budget.signum() <= 0) {
            return missing(BUDGET, title, WEIGHT_BUDGET,
                    "Définissez un budget mensuel pour suivre votre discipline.");
        }

        List<BigDecimal> expenses = input.finishedMonthExpenses();

        if (expenses == null || expenses.isEmpty()) {
            return missing(BUDGET, title, WEIGHT_BUDGET,
                    "Disponible dès qu'un mois complet avec des dépenses est terminé.");
        }

        double total = 0;
        int within = 0;

        for (BigDecimal spent : expenses) {
            if (spent.compareTo(budget) <= 0) {
                total += 100;
                within++;
            } else {
                double overPercent = spent.subtract(budget)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(budget, 4, RoundingMode.HALF_UP)
                        .doubleValue();

                total += Math.max(0, 100 - overPercent * 2);
            }
        }

        int score = clamp(total / expenses.size());
        String detail = "Budget respecté " + within + " mois sur " + expenses.size() + ".";
        String tip = score < TIP_BELOW
                ? "Le budget est dépassé certains mois : réduisez les dépenses ou ajustez-le à la réalité."
                : null;

        return new HealthComponentResponse(BUDGET, title, score, WEIGHT_BUDGET, detail, tip);
    }

    private static HealthComponentResponse categories(Input input) {
        String title = "Budgets par catégorie";
        List<Double> percents = input.categoryPercentUsed();

        if (percents == null || percents.isEmpty()) {
            return missing(CATEGORIES, title, WEIGHT_CATEGORIES,
                    "Fixez des limites par catégorie pour mieux maîtriser vos dépenses.");
        }

        double total = 0;
        int within = 0;
        int counted = 0;

        for (Double percent : percents) {
            if (percent == null || percent.isNaN() || percent.isInfinite()) {
                continue;
            }

            counted++;

            if (percent <= 100) {
                within++;
            }

            if (percent <= 80) {
                total += 100;
            } else if (percent <= 100) {
                total += 70;
            } else if (percent <= 120) {
                total += 30;
            }
        }

        if (counted == 0) {
            return missing(CATEGORIES, title, WEIGHT_CATEGORIES,
                    "Fixez des limites par catégorie pour mieux maîtriser vos dépenses.");
        }

        int score = clamp(total / counted);
        String detail = within + " catégorie" + (within > 1 ? "s" : "") + " sur " + counted
                + " dans la limite ce mois-ci.";
        String tip = score < TIP_BELOW
                ? "Surveillez les catégories proches de leur limite ou qui la dépassent."
                : null;

        return new HealthComponentResponse(CATEGORIES, title, score, WEIGHT_CATEGORIES, detail, tip);
    }

    private static HealthComponentResponse goals(Input input) {
        String title = "Objectifs d'épargne";
        List<String> statuses = input.goalStatuses();

        double total = 0;
        int counted = 0;
        int fine = 0;
        int late = 0;
        int notStarted = 0;

        if (statuses != null) {
            for (String status : statuses) {
                Integer points = goalPoints(status);

                if (points == null) {
                    continue;
                }

                counted++;
                total += points;

                if ("completed".equals(status) || "onTrack".equals(status)) {
                    fine++;
                } else if ("behind".equals(status) || "overdue".equals(status)) {
                    late++;
                } else if ("notStarted".equals(status)) {
                    notStarted++;
                }
            }
        }

        if (counted == 0) {
            return missing(GOALS, title, WEIGHT_GOALS,
                    "Créez un objectif d'épargne pour suivre vos projets.");
        }

        int score = clamp(total / counted);
        String detail = counted + " objectif" + (counted > 1 ? "s" : "") + " : "
                + fine + " atteint" + (fine > 1 ? "s" : "") + " ou sur la bonne voie.";
        String tip = null;

        if (score < TIP_BELOW) {
            if (late > 0) {
                tip = "Un objectif prend du retard : ajoutez une contribution ou repoussez l'échéance.";
            } else if (notStarted > 0) {
                tip = "Commencez à alimenter vos objectifs, même avec un petit montant.";
            } else {
                tip = "Ajoutez une échéance à vos objectifs pour connaître le rythme à tenir.";
            }
        }

        return new HealthComponentResponse(GOALS, title, score, WEIGHT_GOALS, detail, tip);
    }

    private static HealthComponentResponse recurring(Input input) {
        String title = "Paiements réguliers";
        BigDecimal income = input.averageIncome();
        BigDecimal recurring = input.recurringMonthly();

        if (income == null || income.signum() <= 0) {
            return missing(RECURRING, title, WEIGHT_RECURRING,
                    "Ajoutez vos revenus pour mesurer le poids de vos paiements réguliers.");
        }

        if (recurring == null || recurring.signum() <= 0) {
            return missing(RECURRING, title, WEIGHT_RECURRING,
                    "Aucun paiement régulier détecté pour l'instant.");
        }

        double share = recurring.multiply(BigDecimal.valueOf(100))
                .divide(income, 4, RoundingMode.HALF_UP)
                .doubleValue();

        int score;

        if (share <= LIGHT_RECURRING_SHARE) {
            score = 100;
        } else if (share >= HEAVY_RECURRING_SHARE) {
            score = 0;
        } else {
            score = clamp(100 - (share - LIGHT_RECURRING_SHARE) * 2.5);
        }

        String detail = "Vos paiements réguliers représentent " + oneDecimal(share) + " % de vos revenus.";
        String tip = score < TIP_BELOW
                ? "Vos paiements réguliers pèsent lourd : revoyez vos abonnements."
                : null;

        return new HealthComponentResponse(RECURRING, title, score, WEIGHT_RECURRING, detail, tip);
    }

    private static Integer goalPoints(String status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case "completed":
            case "onTrack":
                return 100;
            case "noDeadline":
                return 70;
            case "notStarted":
                return 50;
            case "behind":
                return 40;
            case "overdue":
                return 0;
            default:
                return null;
        }
    }

    private static List<String> tips(List<HealthComponentResponse> components) {
        List<HealthComponentResponse> weak = new ArrayList<>();

        for (HealthComponentResponse component : components) {
            if (component.score() != null && component.tip() != null) {
                weak.add(component);
            }
        }

        weak.sort(Comparator
                .comparing(HealthComponentResponse::score)
                .thenComparing(Comparator.comparingInt(HealthComponentResponse::weight).reversed()));

        List<String> result = new ArrayList<>();

        for (HealthComponentResponse component : weak) {
            if (result.size() >= MAX_TIPS) {
                break;
            }

            result.add(component.tip());
        }

        return result;
    }

    private static HealthComponentResponse missing(String key, String title, int weight, String hint) {
        return new HealthComponentResponse(key, title, null, weight, hint, null);
    }

    private static int clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }

        long rounded = Math.round(value);

        if (rounded < 0) {
            return 0;
        }

        if (rounded > 100) {
            return 100;
        }

        return (int) rounded;
    }

    private static String months(int count) {
        return count + " mois";
    }

    private static String oneDecimal(double value) {
        double clean = Math.abs(value) < 0.05 ? 0.0 : value;
        return String.format(Locale.FRANCE, "%.1f", clean);
    }
}
