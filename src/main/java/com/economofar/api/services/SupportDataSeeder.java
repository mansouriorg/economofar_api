package com.economofar.api.services;

import com.economofar.api.models.FaqItem;
import com.economofar.api.repositories.FaqItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SupportDataSeeder implements CommandLineRunner {

    private final FaqItemRepository faqItemRepository;

    public SupportDataSeeder(FaqItemRepository faqItemRepository) {
        this.faqItemRepository = faqItemRepository;
    }

    @Override
    public void run(String... args) {
        if (faqItemRepository.count() > 0) {
            return;
        }

        faqItemRepository.save(new FaqItem(
                "Compte",
                "Comment modifier mes informations de compte ?",
                "Vous pouvez modifier vos informations depuis l’espace Paramètres. Certaines informations sensibles peuvent nécessiter une vérification.",
                1
        ));

        faqItemRepository.save(new FaqItem(
                "Tickets",
                "Comment scanner un ticket ?",
                "Depuis le tableau de bord, appuyez sur le bouton de scan, prenez une photo claire du ticket, puis validez les informations détectées.",
                2
        ));

        faqItemRepository.save(new FaqItem(
                "Tickets",
                "Pourquoi le montant détecté est incorrect ?",
                "L’analyse OCR peut varier selon la qualité de l’image, la lumière ou le format du ticket. Vous pouvez corriger les informations avant l’enregistrement.",
                3
        ));

        faqItemRepository.save(new FaqItem(
                "Budget",
                "Comment fonctionne l’alerte de budget dépassé ?",
                "Lorsque le total de vos tickets du mois dépasse le budget défini, Economofar crée automatiquement une notification.",
                4
        ));

        faqItemRepository.save(new FaqItem(
                "Notifications",
                "Puis-je désactiver certaines notifications ?",
                "Oui. Depuis les paramètres, vous pouvez activer ou désactiver chaque type de notification séparément.",
                5
        ));
    }
}