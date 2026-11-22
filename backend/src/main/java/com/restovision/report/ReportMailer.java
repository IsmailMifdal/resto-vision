package com.restovision.report;

import com.restovision.api.Dtos.ReportDto;
import com.restovision.domain.AppUser;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Role;
import com.restovision.repo.AppUserRepository;
import com.restovision.repo.RestaurantRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Envoi du rapport du jour par e-mail aux gérants du restaurant : chaque matin pour la veille, ou sur demande. */
@Service
public class ReportMailer {
    private static final Logger log = LoggerFactory.getLogger(ReportMailer.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private final ObjectProvider<JavaMailSender> senders;
    private final DailyReportService reports;
    private final RestaurantRepository restaurants;
    private final AppUserRepository users;
    private final String host;
    private final String from;
    private final ZoneId timezone;

    public ReportMailer(ObjectProvider<JavaMailSender> senders, DailyReportService reports,
                        RestaurantRepository restaurants, AppUserRepository users,
                        @Value("${spring.mail.host:}") String host,
                        @Value("${restovision.mail.from:RestoVision <rapport@restovision.local>}") String from,
                        @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.senders = senders;
        this.reports = reports;
        this.restaurants = restaurants;
        this.users = users;
        this.host = host;
        this.from = from;
        this.timezone = ZoneId.of(timezone);
    }

    public boolean isConfigured() {
        return !host.isBlank() && senders.getIfAvailable() != null;
    }

    /** Chaque matin : le rapport de la veille, à chaque restaurant qui a un gérant. */
    @Scheduled(cron = "${restovision.mail.report-cron:0 0 8 * * *}", zone = "${restovision.timezone:Europe/Paris}")
    @Transactional
    public void sendYesterday() {
        if (!isConfigured()) {
            return;
        }
        LocalDate yesterday = LocalDate.now(timezone).minusDays(1);
        for (Restaurant restaurant : restaurants.findAll()) {
            try {
                send(restaurant, yesterday);
            } catch (RuntimeException e) {
                log.warn("Rapport du {} non envoyé à {} : {}", yesterday, restaurant.getCode(), e.getMessage());
            }
        }
    }

    /** Envoie le rapport d'un jour terminé. Renvoie les adresses des destinataires. */
    @Transactional
    public List<String> send(String restaurantCode, LocalDate day) {
        Restaurant restaurant = restaurants.findByCode(restaurantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Aucun serveur d'envoi d'e-mails n'est configuré (SPRING_MAIL_HOST).");
        }
        if (day == null || !day.isBefore(LocalDate.now(timezone))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seul le rapport d'une journée terminée s'envoie.");
        }
        try {
            return send(restaurant, day);
        } catch (MailException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Le serveur d'e-mails a refusé l'envoi.");
        }
    }

    private List<String> send(Restaurant restaurant, LocalDate day) {
        List<String> to = users.findByRestaurantAndRoleIn(restaurant, List.of(Role.MANAGER, Role.ADMIN)).stream()
                .map(AppUser::getEmail).toList();
        if (to.isEmpty()) {
            return to;
        }
        ReportDto report = reports.report(restaurant, day, false);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.toArray(String[]::new));
        message.setSubject(subject(restaurant, day));
        message.setText(body(restaurant, report));
        senders.getObject().send(message);
        log.info("Rapport du {} envoyé à {} gérant(s) de {}", day, to.size(), restaurant.getCode());
        return to;
    }

    static String subject(Restaurant restaurant, LocalDate day) {
        return restaurant.getName() + " · rapport du " + day.format(DATE);
    }

    static String body(Restaurant restaurant, ReportDto report) {
        String origin = report.source() == DailyReport.Source.AI
                ? "Rapport rédigé par IA à partir des chiffres mesurés dans la salle."
                : "Rapport établi à partir des chiffres mesurés dans la salle.";
        return "Bonjour,\n\n" + report.text() + "\n\n" + origin
                + "\nLe détail, table par table, est dans l'écran Service de RestoVision.\n\n— RestoVision, "
                + restaurant.getName() + "\n";
    }
}
