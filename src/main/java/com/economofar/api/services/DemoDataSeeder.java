package com.economofar.api.services;

import com.economofar.api.models.CategoryBudget;
import com.economofar.api.models.Income;
import com.economofar.api.models.ReceiptUsageType;
import com.economofar.api.models.Role;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.TicketItem;
import com.economofar.api.models.User;
import com.economofar.api.models.UserBudget;
import com.economofar.api.repositories.CategoryBudgetRepository;
import com.economofar.api.repositories.IncomeRepository;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.repositories.UserBudgetRepository;
import com.economofar.api.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fills the database with a demo account when the application starts with the "demo" profile
 * (SPRING_PROFILES_ACTIVE=demo). Nothing happens with any other profile.
 *
 * The account has fourteen months of saved receipts, a monthly budget, budgets by category and
 * some incomes, so that most screens of the app have something to show. The data comes from
 * DemoDataFactory, with the dates counted back from the day of the first start.
 *
 * Everything is saved in one transaction: if something fails, nothing is kept and the next start
 * tries again from the beginning. Starting again does not duplicate anything: an account that
 * already has receipts is left as it is (to get fresh dates, delete the data and start again).
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final DateTimeFormatter TICKET_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final UserBudgetRepository userBudgetRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public DemoDataSeeder(
            UserRepository userRepository,
            TicketRepository ticketRepository,
            UserBudgetRepository userBudgetRepository,
            IncomeRepository incomeRepository,
            CategoryBudgetRepository categoryBudgetRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.userBudgetRepository = userBudgetRepository;
        this.incomeRepository = incomeRepository;
        this.categoryBudgetRepository = categoryBudgetRepository;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(String... args) {
        transaction.executeWithoutResult(status -> seed(LocalDate.now()));
        log.info("Demo data committed.");
    }

    void seed(LocalDate today) {
        User user = userRepository.findByEmail(DemoDataFactory.EMAIL).orElse(null);

        if (user != null && ticketRepository.countByUserId(user.getId()) > 0) {
            log.info("Demo account {} is already filled, nothing to do.", DemoDataFactory.EMAIL);
            return;
        }

        if (user == null) {
            user = userRepository.save(User.builder()
                    .firstName(DemoDataFactory.FIRST_NAME)
                    .lastName(DemoDataFactory.LAST_NAME)
                    .email(DemoDataFactory.EMAIL)
                    .password(passwordEncoder.encode(DemoDataFactory.PASSWORD))
                    .role(Role.ROLE_USER)
                    .preferredCurrency(DemoDataFactory.CURRENCY)
                    .language("fr")
                    .build());
        }

        if (userBudgetRepository.findByUser(user).isEmpty()) {
            userBudgetRepository.save(new UserBudget(user, DemoDataFactory.MONTHLY_BUDGET.doubleValue(), DemoDataFactory.CURRENCY));
        }

        if (incomeRepository.countByUser(user) == 0) {
            for (DemoDataFactory.IncomeSpec income : DemoDataFactory.incomes(today)) {
                incomeRepository.save(new Income(
                        user, income.name(), income.amount(), income.startDate(), income.recurring(), income.endDate()
                ));
            }
        }

        if (categoryBudgetRepository.findByUserOrderByCategoryAsc(user).isEmpty()) {
            for (DemoDataFactory.BudgetSpec budget : DemoDataFactory.categoryBudgets()) {
                categoryBudgetRepository.save(new CategoryBudget(user, budget.category(), budget.monthlyAmount()));
            }
        }

        List<Ticket> tickets = new ArrayList<>();

        for (DemoDataFactory.ReceiptSpec receipt : DemoDataFactory.receipts(today)) {
            tickets.add(toTicket(receipt, user));
        }

        ticketRepository.saveAll(tickets);

        log.info("Demo account ready: {} with the password {} ({} receipts).", DemoDataFactory.EMAIL, DemoDataFactory.PASSWORD, tickets.size());
    }

    static Ticket toTicket(DemoDataFactory.ReceiptSpec receipt, User user) {
        LocalDateTime at = receipt.purchaseDate();
        ReceiptUsageType usage = receipt.business() ? ReceiptUsageType.BUSINESS : ReceiptUsageType.PERSONAL;

        Ticket ticket = Ticket.builder()
                .merchantName(receipt.merchant())
                .totalAmount(receipt.total())
                .purchaseDate(at)
                .category(receipt.category())
                .paymentMethod(receipt.paymentMethod())
                .rawOcrText(rawText(receipt))
                .note(receipt.note())
                .warrantyDate(receipt.warrantyDate())
                .reimbursable(receipt.reimbursable())
                .usageType(usage)
                .vatAmount(receipt.vat())
                .deductible(receipt.deductible())
                .projectName(receipt.project())
                .clientName(receipt.client())
                .expenseReportReference(receipt.expenseReport())
                .scanStatus(ScanStatus.SAVED)
                .scanStartedAt(at)
                .ocrCompletedAt(at)
                .reviewedAt(at)
                .savedAt(at)
                .user(user)
                .build();

        int position = 0;

        for (DemoDataFactory.LineSpec line : receipt.lines()) {
            LocalDateTime created = at.plusSeconds(position);

            ticket.addItem(TicketItem.builder()
                    .name(line.name())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .totalPrice(line.total())
                    .category(line.category())
                    .usageType(usage)
                    .createdAt(created)
                    .updatedAt(created)
                    .build());

            position++;
        }

        return ticket;
    }

    static String rawText(DemoDataFactory.ReceiptSpec receipt) {
        StringBuilder text = new StringBuilder();

        text.append(receipt.merchant().toUpperCase(Locale.ROOT)).append('\n');
        text.append(TICKET_DATE.format(receipt.purchaseDate())).append('\n');

        for (DemoDataFactory.LineSpec line : receipt.lines()) {
            text.append(line.quantity()).append(" x ").append(line.name()).append("  ").append(line.total().toPlainString()).append('\n');
        }

        text.append("TOTAL ").append(receipt.total().toPlainString()).append(' ').append(DemoDataFactory.CURRENCY).append('\n');

        return text.toString();
    }
}
