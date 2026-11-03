package com.economofar.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the data of the demo account: about fourteen months of the receipts of a person who lives
 * in France, with the habits that the analyses look for (a weekly shop, a baker every three or four
 * days, fuel, a transport pass, subscriptions, a few big purchases with a warranty and business
 * receipts of two clients).
 *
 * Nothing is read from the database and nothing is written: the same day always gives the same data
 * (the random numbers come from a fixed seed), so the numbers of the demo can be checked in tests. The
 * amounts are in euros to the cent, the total of a receipt is the sum of the totals of its lines, and
 * no receipt is dated after yesterday. This class has no dependency on Spring.
 */
public final class DemoDataFactory {

    public static final String EMAIL = "demo@economofar.app";
    public static final String PASSWORD = "Demo2026Pass";
    public static final String FIRST_NAME = "Camille";
    public static final String LAST_NAME = "Martin";
    public static final String CURRENCY = "EUR";
    public static final int MONTHS = 14;
    public static final long SEED = 20260101L;
    public static final BigDecimal MONTHLY_BUDGET = new BigDecimal("1150.00");

    public static final String GROCERY = "Alimentation";
    public static final String RESTAURANT = "Restaurant";
    public static final String TRANSPORT = "Transport";
    public static final String HEALTH = "Sant\u00e9";
    public static final String SHOPPING = "Shopping";
    public static final String HOME = "Maison";
    public static final String LEISURE = "Loisirs";

    private static final String CARD = "Carte";
    private static final String CASH = "Esp\u00e8ces";
    private static final String MOBILE = "Apple Pay";

    private static final int[] SHIFTS = {-1, 0, 0, 0, 0, 1};

    /** One line of a receipt. */
    public record LineSpec(String name, String category, int quantity, BigDecimal unitPrice) {

        public BigDecimal total() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** One receipt, as it would be saved after a scan that the person has checked. */
    public record ReceiptSpec(
            String merchant,
            String category,
            String paymentMethod,
            LocalDateTime purchaseDate,
            boolean business,
            String project,
            String client,
            String expenseReport,
            boolean reimbursable,
            boolean deductible,
            LocalDate warrantyDate,
            String note,
            List<LineSpec> lines
    ) {

        public BigDecimal total() {
            BigDecimal sum = BigDecimal.ZERO;

            for (LineSpec line : lines) {
                sum = sum.add(line.total());
            }

            return sum.setScale(2, RoundingMode.HALF_UP);
        }

        /** The VAT included in a business receipt at 20 percent, null for a personal receipt. */
        public BigDecimal vat() {
            if (business == false) {
                return null;
            }

            return total().multiply(BigDecimal.valueOf(20)).divide(BigDecimal.valueOf(120), 2, RoundingMode.HALF_UP);
        }
    }

    /** A source of income. */
    public record IncomeSpec(String name, BigDecimal amount, LocalDate startDate, boolean recurring, LocalDate endDate) {
    }

    /** The monthly budget of one category. */
    public record BudgetSpec(String category, BigDecimal monthlyAmount) {
    }

    private record Staple(String name, String category, int every, int quantity, double price, double drift) {
    }

    private record Filler(String name, String category, double price) {
    }

    private static final List<Staple> STAPLES = List.of(
            new Staple("Lait demi-\u00e9cr\u00e9m\u00e9 1L", GROCERY, 1, 6, 1.05, 0.07),
            new Staple("Bananes 1kg", GROCERY, 1, 1, 1.89, 0.03),
            new Staple("Yaourts nature x8", GROCERY, 1, 1, 2.45, 0.04),
            new Staple("Eau min\u00e9rale 6x1,5L", GROCERY, 1, 2, 2.60, 0.03),
            new Staple("Pain de mie complet", GROCERY, 1, 1, 1.89, 0.04),
            new Staple("Tomates grappe 500g", GROCERY, 1, 1, 2.29, 0.02),
            new Staple("\u0152ufs bio x12", GROCERY, 2, 1, 3.89, 0.08),
            new Staple("P\u00e2tes spaghetti 500g", GROCERY, 2, 3, 0.89, 0.05),
            new Staple("Jus d'orange 1L", GROCERY, 2, 2, 1.65, 0.03),
            new Staple("Poulet fermier", GROCERY, 2, 1, 8.90, 0.04),
            new Staple("Beurre doux 250g", GROCERY, 3, 1, 2.35, 0.06),
            new Staple("Fromage r\u00e2p\u00e9 200g", GROCERY, 3, 1, 1.99, 0.03),
            new Staple("Caf\u00e9 en grains 1kg", GROCERY, 4, 1, 11.90, 0.05),
            new Staple("Riz basmati 1kg", GROCERY, 4, 1, 2.79, 0.03),
            new Staple("Papier toilette x12", HOME, 5, 1, 6.49, 0.02),
            new Staple("Lessive liquide 1,5L", HOME, 8, 1, 7.99, 0.02)
    );

    private static final List<Filler> FILLERS = List.of(
            new Filler("Pommes Gala 1kg", GROCERY, 2.69),
            new Filler("Carottes 1kg", GROCERY, 1.29),
            new Filler("Courgettes 500g", GROCERY, 1.99),
            new Filler("Salade batavia", GROCERY, 1.09),
            new Filler("Pommes de terre 2kg", GROCERY, 2.89),
            new Filler("Oignons jaunes 1kg", GROCERY, 1.39),
            new Filler("Champignons de Paris", GROCERY, 1.89),
            new Filler("Saumon fum\u00e9 4 tranches", GROCERY, 4.99),
            new Filler("Jambon blanc x4", GROCERY, 3.19),
            new Filler("Steak hach\u00e9 x2", GROCERY, 4.49),
            new Filler("Filet de cabillaud", GROCERY, 6.90),
            new Filler("Mozzarella", GROCERY, 1.15),
            new Filler("Comt\u00e9 200g", GROCERY, 3.95),
            new Filler("Cr\u00e8me fra\u00eeche 20cl", GROCERY, 1.35),
            new Filler("Farine T55 1kg", GROCERY, 1.05),
            new Filler("Sucre en poudre 1kg", GROCERY, 1.39),
            new Filler("Huile d'olive 75cl", GROCERY, 6.49),
            new Filler("Sauce tomate 400g", GROCERY, 1.25),
            new Filler("Thon au naturel x3", GROCERY, 3.45),
            new Filler("Lentilles vertes 500g", GROCERY, 1.89),
            new Filler("Cornflakes 500g", GROCERY, 2.55),
            new Filler("Confiture de fraises", GROCERY, 2.35),
            new Filler("Miel de fleurs 250g", GROCERY, 4.80),
            new Filler("Chocolat noir 70%", GROCERY, 1.79),
            new Filler("Biscuits sabl\u00e9s", GROCERY, 2.15),
            new Filler("Eau gazeuse x6", GROCERY, 3.30),
            new Filler("Th\u00e9 vert x20", GROCERY, 2.69),
            new Filler("Glaces vanille", GROCERY, 3.49),
            new Filler("Pizza surgel\u00e9e", GROCERY, 3.25),
            new Filler("Gel douche", HOME, 2.49),
            new Filler("Dentifrice", HOME, 2.19),
            new Filler("Liquide vaisselle", HOME, 1.89),
            new Filler("Sacs poubelle x30", HOME, 3.19),
            new Filler("\u00c9ponges x3", HOME, 2.29),
            new Filler("Shampoing", HOME, 3.59)
    );

    private DemoDataFactory() {
    }

    private interface Visit {
        void at(LocalDate date);
    }

    private static final class Draft {
        private final String merchant;
        private final String category;
        private final String payment;
        private final LocalDateTime when;
        private final List<LineSpec> lines = new ArrayList<>();
        private boolean business;
        private String project;
        private String client;
        private String expenseReport;
        private boolean reimbursable;
        private boolean deductible;
        private LocalDate warrantyDate;
        private String note;

        private Draft(String merchant, String category, String payment, LocalDateTime when) {
            this.merchant = merchant;
            this.category = category;
            this.payment = payment;
            this.when = when;
        }

        private Draft add(String name, String category, int quantity, BigDecimal unitPrice) {
            lines.add(new LineSpec(name, category, quantity, unitPrice));
            return this;
        }

        private ReceiptSpec build() {
            return new ReceiptSpec(
                    merchant, category, payment, when, business, project, client, expenseReport,
                    reimbursable, deductible, warrantyDate, note, List.copyOf(lines)
            );
        }
    }

    private static final class Plan {
        private final Random random = new Random(SEED);
        private final LocalDate today;
        private final LocalDate start;
        private final LocalDate last;
        private final long span;
        private final Map<String, Draft> drafts = new LinkedHashMap<>();

        private Plan(LocalDate today) {
            this.today = today;
            this.last = today.minusDays(1);
            this.start = today.minusMonths(MONTHS);
            this.span = ChronoUnit.DAYS.between(start, last);
        }

        private boolean inRange(LocalDate date) {
            return date.isBefore(start) == false && date.isAfter(last) == false;
        }

        private double progress(LocalDate date) {
            return (double) ChronoUnit.DAYS.between(start, date) / span;
        }

        private boolean chance(int percent) {
            return random.nextInt(100) < percent;
        }

        private int between(int low, int high) {
            return low + random.nextInt(high - low + 1);
        }

        private Draft draft(LocalDate date, String merchant, String category, String payment, int hour, int minute) {
            String key = date + "|" + merchant;
            Draft draft = drafts.get(key);

            if (draft == null) {
                draft = new Draft(merchant, category, payment, date.atTime(hour, minute));
                drafts.put(key, draft);
            }

            return draft;
        }

        private Draft draftAt(LocalDate date, String merchant, String category, String payment, int hour) {
            return draft(date, merchant, category, payment, hour, random.nextInt(60));
        }
    }

    /** The receipts of the demo account for the given day, oldest first. */
    public static List<ReceiptSpec> receipts(LocalDate today) {
        Plan plan = new Plan(today);

        trips(plan);
        bakery(plan);
        coffee(plan);
        lunches(plan);
        pizzas(plan);
        fuel(plan);
        pharmacy(plan);
        shopping(plan);
        home(plan);
        monthly(plan);
        bigPurchases(plan);
        business(plan);

        List<ReceiptSpec> result = new ArrayList<>();

        for (Draft draft : plan.drafts.values()) {
            result.add(draft.build());
        }

        result.sort(Comparator.comparing(ReceiptSpec::purchaseDate).thenComparing(ReceiptSpec::merchant));
        addImperfect(plan, result);
        result.sort(Comparator.comparing(ReceiptSpec::purchaseDate).thenComparing(ReceiptSpec::merchant));

        return List.copyOf(result);
    }

    /** The incomes of the demo account for the given day: a salary and a few one-off incomes. */
    public static List<IncomeSpec> incomes(LocalDate today) {
        LocalDate first = today.minusMonths(MONTHS).withDayOfMonth(1);

        return List.of(
                new IncomeSpec("Salaire", new BigDecimal("1850.00"), first, true, null),
                new IncomeSpec("Prime annuelle", new BigDecimal("600.00"), today.minusMonths(7).withDayOfMonth(20), false, null),
                new IncomeSpec("Mission freelance", new BigDecimal("450.00"), today.minusMonths(4).withDayOfMonth(15), false, null),
                new IncomeSpec("Remboursement mutuelle", new BigDecimal("64.50"), today.minusMonths(2).withDayOfMonth(10), false, null)
        );
    }

    /** The monthly budget of each category of the demo account. */
    public static List<BudgetSpec> categoryBudgets() {
        return List.of(
                new BudgetSpec(GROCERY, new BigDecimal("420.00")),
                new BudgetSpec(RESTAURANT, new BigDecimal("140.00")),
                new BudgetSpec(TRANSPORT, new BigDecimal("260.00")),
                new BudgetSpec(HEALTH, new BigDecimal("60.00")),
                new BudgetSpec(SHOPPING, new BigDecimal("120.00")),
                new BudgetSpec(LEISURE, new BigDecimal("50.00")),
                new BudgetSpec(HOME, new BigDecimal("70.00"))
        );
    }

    private static void addImperfect(Plan plan, List<ReceiptSpec> result) {
        LocalDate unknownDay = plan.today.minusDays(40);
        LocalDate otherDay = plan.today.minusDays(18);

        result.add(new ReceiptSpec(
                "Commer\u00e7ant inconnu", GROCERY, null, unknownDay.atTime(13, 12), false, null, null, null,
                false, false, null, null,
                List.of(new LineSpec("Article", GROCERY, 1, exact("12.40")))
        ));
        result.add(new ReceiptSpec(
                "Quincaillerie du Coin", "Autre", CASH, otherDay.atTime(10, 25), false, null, null, null,
                false, false, null, null,
                List.of(new LineSpec("Cl\u00e9 de serrure", "Autre", 1, exact("8.90")))
        ));

        ReceiptSpec scanned = null;

        for (ReceiptSpec receipt : result) {
            if (receipt.merchant().equals("Pizzeria Napoli") && receipt.purchaseDate().toLocalDate().isAfter(plan.today.minusDays(120))) {
                scanned = receipt;
                break;
            }
        }

        if (scanned != null) {
            result.add(new ReceiptSpec(
                    scanned.merchant(), scanned.category(), scanned.paymentMethod(), scanned.purchaseDate().plusMinutes(1),
                    scanned.business(), scanned.project(), scanned.client(), scanned.expenseReport(),
                    scanned.reimbursable(), scanned.deductible(), scanned.warrantyDate(), scanned.note(), scanned.lines()
            ));
        }
    }

    private static BigDecimal price(double base, double drift, double progress) {
        return BigDecimal.valueOf(Math.round(base * (1.0 + drift * progress) * 100.0), 2);
    }

    private static BigDecimal exact(String value) {
        return new BigDecimal(value);
    }

    private static void every(Plan plan, int minGap, int maxGap, Visit visit) {
        LocalDate date = plan.start.plusDays(plan.random.nextInt(maxGap));

        while (date.isAfter(plan.last) == false) {
            visit.at(date);
            date = date.plusDays(plan.between(minGap, maxGap));
        }
    }

    private static LocalDate weekday(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }

        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }

        return date;
    }

    private static void trips(Plan plan) {
        LocalDate saturday = plan.start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        int index = 0;

        for (LocalDate week = saturday; week.isAfter(plan.last) == false; week = week.plusDays(7)) {
            index++;

            if (plan.chance(4)) {
                continue;
            }

            LocalDate date = week.plusDays(SHIFTS[plan.random.nextInt(SHIFTS.length)]);

            if (plan.inRange(date) == false) {
                continue;
            }

            int roll = plan.random.nextInt(100);
            String merchant = roll < 50 ? "Carrefour Market" : roll < 85 ? "Lidl" : "Monoprix";
            double factor = roll < 50 ? 1.00 : roll < 85 ? 0.88 : 1.12;
            String payment = index % 11 == 3 ? null : plan.chance(18) ? MOBILE : CARD;
            Draft draft = plan.draftAt(date, merchant, GROCERY, payment, plan.between(9, 18));
            double progress = plan.progress(date);

            for (Staple staple : STAPLES) {
                int phase = Math.floorMod(staple.name().hashCode(), staple.every());

                if ((index + phase) % staple.every() == 0 && plan.chance(94)) {
                    draft.add(staple.name(), staple.category(), staple.quantity(),
                            price(staple.price() * factor, staple.drift(), progress));
                }
            }

            List<Filler> pool = new ArrayList<>(FILLERS);
            Collections.shuffle(pool, plan.random);
            int count = plan.between(9, 14);

            for (int i = 0; i < count; i++) {
                Filler filler = pool.get(i);
                double noise = 0.93 + plan.random.nextInt(15) / 100.0;
                int quantity = plan.chance(20) ? 2 : 1;
                draft.add(filler.name(), filler.category(), quantity, price(filler.price() * factor * noise, 0.02, progress));
            }
        }
    }

    private static void bakery(Plan plan) {
        LocalDate date = plan.start.plusDays(plan.random.nextInt(3));

        while (date.isAfter(plan.last) == false) {
            String payment = plan.chance(60) ? CASH : CARD;
            Draft draft = plan.draftAt(date, "Boulangerie Le Fournil", GROCERY, payment, plan.between(7, 9));
            double progress = plan.progress(date);

            draft.add("Baguette tradition", GROCERY, plan.between(1, 2), price(1.15, 0.09, progress));

            if (plan.chance(30)) {
                draft.add("Croissant au beurre", GROCERY, plan.between(1, 3), price(1.20, 0.08, progress));
            }

            if (plan.chance(15)) {
                draft.add("Pain aux c\u00e9r\u00e9ales", GROCERY, 1, price(2.60, 0.06, progress));
            }

            date = date.plusDays(plan.between(3, 4));
        }
    }

    private static void coffee(Plan plan) {
        for (LocalDate date = plan.start; date.isAfter(plan.last) == false; date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            if (plan.chance(40) == false) {
                continue;
            }

            String payment = plan.chance(50) ? CASH : CARD;
            Draft draft = plan.draftAt(date, "Caf\u00e9 de la Gare", RESTAURANT, payment, plan.between(8, 9));
            double progress = plan.progress(date);

            draft.add("Caf\u00e9 expresso", RESTAURANT, 1, price(1.70, 0.06, progress));

            if (plan.chance(35)) {
                draft.add("Croissant", RESTAURANT, 1, price(1.30, 0.05, progress));
            }
        }
    }

    private static void lunches(Plan plan) {
        every(plan, 11, 15, date -> {
            LocalDate day = weekday(date);

            if (plan.inRange(day) == false) {
                return;
            }

            Draft draft = plan.draftAt(day, "Brasserie du Port", RESTAURANT, plan.chance(70) ? CARD : MOBILE, 12);
            double progress = plan.progress(day);

            draft.add("Menu du jour", RESTAURANT, 1, price(15.90, 0.05, progress));
            draft.add("Boisson", RESTAURANT, 1, price(3.50, 0.04, progress));

            if (plan.chance(40)) {
                draft.add("Caf\u00e9", RESTAURANT, 1, price(1.80, 0.05, progress));
            }
        });
    }

    private static void pizzas(Plan plan) {
        every(plan, 18, 24, date -> {
            LocalDate day = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

            if (plan.inRange(day) == false) {
                return;
            }

            Draft draft = plan.draftAt(day, "Pizzeria Napoli", RESTAURANT, CARD, plan.between(19, 20));
            double progress = plan.progress(day);

            draft.add("Pizza Reine", RESTAURANT, 2, price(12.50, 0.06, progress));
            draft.add("Boisson", RESTAURANT, 2, price(3.80, 0.04, progress));

            if (plan.chance(50)) {
                draft.add("Tiramisu", RESTAURANT, 1, price(5.50, 0.05, progress));
            }
        });
    }

    private static void fuel(Plan plan) {
        every(plan, 14, 18, date -> {
            Draft draft = plan.draftAt(date, "TotalEnergies", TRANSPORT, CARD, plan.between(17, 19));
            double noise = plan.random.nextInt(9) / 100.0 - 0.04;

            draft.add("Carburant SP95-E10", TRANSPORT, plan.between(30, 38), price(1.74 + noise, 0.04, plan.progress(date)));
        });
    }

    private static void pharmacy(Plan plan) {
        String[] names = {"Doliprane 1000mg", "Sirop contre la toux", "Vitamine C", "Pansements", "Spray nasal", "Cr\u00e8me hydratante"};
        double[] prices = {2.18, 7.90, 6.50, 3.20, 5.60, 9.90};

        every(plan, 35, 50, date -> {
            Draft draft = plan.draftAt(date, "Pharmacie Centrale", HEALTH, CARD, plan.between(10, 18));
            List<Integer> picks = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
            Collections.shuffle(picks, plan.random);
            int count = plan.between(2, 3);

            for (int i = 0; i < count; i++) {
                draft.add(names[picks.get(i)], HEALTH, 1, price(prices[picks.get(i)], 0.03, plan.progress(date)));
            }
        });
    }

    private static void shopping(Plan plan) {
        String[] merchants = {"Zara", "Decathlon", "H&M", "Fnac"};
        String[][] names = {
                {"T-shirt coton", "Jean slim", "Pull col rond"},
                {"Chaussures de running", "Short de sport", "Gourde 750ml"},
                {"Chemise lin", "Chaussettes x5", "Ceinture cuir"},
                {"Roman poche", "C\u00e2ble USB-C", "Carte m\u00e9moire 128Go"}
        };
        double[][] prices = {
                {19.95, 39.95, 29.95},
                {59.99, 12.99, 6.99},
                {24.99, 9.99, 14.99},
                {8.90, 14.99, 17.99}
        };
        int[] counter = {0};

        every(plan, 28, 45, date -> {
            int shop = counter[0] % merchants.length;
            counter[0]++;
            Draft draft = plan.draftAt(date, merchants[shop], SHOPPING, plan.chance(75) ? CARD : MOBILE, plan.between(11, 18));
            int count = plan.between(1, 3);

            for (int i = 0; i < count; i++) {
                draft.add(names[shop][i], SHOPPING, 1, exact(String.format(java.util.Locale.ROOT, "%.2f", prices[shop][i])));
            }
        });
    }

    private static void home(Plan plan) {
        String[] merchants = {"IKEA", "Leroy Merlin"};
        String[] names = {"Ampoules LED x4", "\u00c9tag\u00e8re murale", "Plante verte", "Bo\u00eetes de rangement x3", "Peinture blanche 2,5L"};
        double[] prices = {7.90, 24.99, 12.50, 14.99, 29.90};
        int[] counter = {0};

        every(plan, 50, 70, date -> {
            Draft draft = plan.draftAt(date, merchants[counter[0] % merchants.length], HOME, CARD, plan.between(10, 17));
            counter[0]++;
            List<Integer> picks = new ArrayList<>(List.of(0, 1, 2, 3, 4));
            Collections.shuffle(picks, plan.random);
            int count = plan.between(1, 3);

            for (int i = 0; i < count; i++) {
                draft.add(names[picks.get(i)], HOME, 1, exact(String.format(java.util.Locale.ROOT, "%.2f", prices[picks.get(i)])));
            }
        });
    }

    private static void monthly(Plan plan) {
        YearMonth month = YearMonth.from(plan.start);
        YearMonth end = YearMonth.from(plan.last);

        while (month.isAfter(end) == false) {
            LocalDate pass = month.atDay(2);

            if (plan.inRange(pass)) {
                plan.draft(pass, "Navigo", TRANSPORT, CARD, 7, 45)
                        .add("Abonnement Navigo mensuel", TRANSPORT, 1, exact("86.40"));
            }

            LocalDate music = month.atDay(5);

            if (plan.inRange(music)) {
                plan.draft(music, "Spotify", LEISURE, CARD, 3, 5)
                        .add("Spotify Premium", LEISURE, 1, exact("10.99"));
            }

            LocalDate gym = month.atDay(8);

            if (plan.inRange(gym)) {
                plan.draft(gym, "FitnessPark", HEALTH, CARD, 3, 10)
                        .add("Abonnement salle de sport", HEALTH, 1, exact("29.90"));
            }

            LocalDate video = month.atDay(12);

            if (plan.inRange(video)) {
                String amount = video.isBefore(plan.today.minusMonths(4)) ? "13.49" : "15.49";

                plan.draft(video, "Netflix", LEISURE, CARD, 3, 15)
                        .add("Abonnement Netflix", LEISURE, 1, exact(amount));
            }

            month = month.plusMonths(1);
        }
    }

    private static void bigPurchases(Plan plan) {
        LocalDate laptop = plan.today.minusDays(150);
        Draft draft = plan.draft(laptop, "Apple Store", SHOPPING, CARD, 14, 30)
                .add("MacBook Air 13 pouces", SHOPPING, 1, exact("1299.00"));
        draft.warrantyDate = laptop.plusYears(2);
        draft.note = "Garantie 2 ans, facture \u00e0 conserver";

        LocalDate headphones = plan.today.minusDays(275);
        draft = plan.draft(headphones, "Fnac", SHOPPING, MOBILE, 16, 10)
                .add("Casque audio sans fil", SHOPPING, 1, exact("149.99"));
        draft.warrantyDate = headphones.plusYears(2);
        draft.note = "Garantie 2 ans";

        LocalDate vacuum = plan.today.minusDays(345);
        draft = plan.draft(vacuum, "Boulanger", HOME, CARD, 15, 20)
                .add("Aspirateur robot", HOME, 1, exact("249.00"));
        draft.warrantyDate = vacuum.plusYears(1);
        draft.note = "Garantie 1 an";

        LocalDate toaster = plan.today.minusDays(380);
        draft = plan.draft(toaster, "Boulanger", HOME, CARD, 11, 40)
                .add("Grille-pain", HOME, 1, exact("39.90"));
        draft.warrantyDate = toaster.plusYears(1);
        draft.note = "Garantie 1 an";

        plan.draft(plan.today.minusDays(95), "Garage Auto Plus", TRANSPORT, CARD, 17, 15)
                .add("R\u00e9vision compl\u00e8te", TRANSPORT, 1, exact("189.00"))
                .add("Plaquettes de frein avant", TRANSPORT, 1, exact("112.00"))
                .add("Main d'\u0153uvre", TRANSPORT, 3, exact("38.00"));

        draft = plan.draft(plan.today.minusDays(60), "Restaurant Le Jardin", RESTAURANT, CARD, 20, 5)
                .add("Menu d\u00e9gustation", RESTAURANT, 2, exact("49.00"))
                .add("Vin rouge", RESTAURANT, 1, exact("32.00"));
        draft.note = "Anniversaire";
    }

    private static void business(Plan plan) {
        String[] clients = {"Atelier Verde", "Studio Nova"};
        String[] projects = {"Refonte du site", "Application mobile"};

        for (int i = 0; i < 12; i++) {
            LocalDate month = plan.today.minusMonths(i + 1);
            LocalDate date = weekday(month.withDayOfMonth(plan.between(3, 24)));

            if (plan.inRange(date) == false) {
                continue;
            }

            int kind = i % 4;
            int who = i % 2;
            Draft draft;

            if (kind == 0) {
                draft = plan.draftAt(date, "SNCF", TRANSPORT, CARD, plan.between(7, 9))
                        .add("Billet TGV Paris-Lyon", TRANSPORT, 1, exact("78.00"));
            } else if (kind == 1) {
                draft = plan.draftAt(date, "Staples", SHOPPING, CARD, plan.between(10, 17))
                        .add("Ramettes papier A4 x5", SHOPPING, 1, exact("24.90"))
                        .add("Stylos x10", SHOPPING, 1, exact("6.50"));
            } else if (kind == 2) {
                draft = plan.draftAt(date, "Restaurant L'Atelier", RESTAURANT, CARD, 12)
                        .add("D\u00e9jeuner client", RESTAURANT, 2, exact("21.50"));
            } else {
                draft = plan.draftAt(date, "Taxi G7", TRANSPORT, MOBILE, plan.between(8, 19))
                        .add("Course taxi", TRANSPORT, 1, exact("23.40"));
            }

            markBusiness(draft, projects[who], clients[who], date, kind != 3);
        }

        LocalDate train = plan.today.minusDays(2);
        Draft trip = plan.draft(train, "SNCF", TRANSPORT, CARD, 8, 12)
                .add("Billet TGV Paris-Lyon", TRANSPORT, 1, exact("78.00"));
        markBusiness(trip, projects[0], clients[0], train, true);

        LocalDate lunchDay = plan.today.minusDays(6);
        Draft lunch = plan.draft(lunchDay, "Restaurant L'Atelier", RESTAURANT, CARD, 12, 40)
                .add("D\u00e9jeuner client", RESTAURANT, 2, exact("21.50"));
        markBusiness(lunch, projects[1], clients[1], lunchDay, true);
    }

    private static void markBusiness(Draft draft, String project, String client, LocalDate date, boolean reimbursable) {
        draft.business = true;
        draft.project = project;
        draft.client = client;
        draft.expenseReport = "EXP-" + date.getYear() + "-" + String.format(java.util.Locale.ROOT, "%02d", date.getMonthValue());
        draft.reimbursable = reimbursable;
        draft.deductible = true;
    }
}
