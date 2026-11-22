package com.restovision.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restovision.api.Dtos.ReportDto;
import com.restovision.domain.AppUser;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Role;
import com.restovision.repo.AppUserRepository;
import com.restovision.repo.RestaurantRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

/** Test sans contexte Spring : un second contexte recréerait la base de test partagée. */
class ReportMailerTest {
    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1);
    private final Restaurant demo = new Restaurant("demo", "Chez Démo");
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final DailyReportService reports = mock(DailyReportService.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private ReportMailer mailer;

    @SuppressWarnings("unchecked")
    private ReportMailer mailer(String host) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        return new ReportMailer(provider, reports, restaurants, users, host, "RestoVision <r@test>", "Europe/Paris");
    }

    @BeforeEach
    void setUp() {
        when(restaurants.findByCode("demo")).thenReturn(Optional.of(demo));
        when(users.findByRestaurantAndRoleIn(eq(demo), any())).thenReturn(List.of(
                new AppUser("gerant@chez-demo.fr", "x", Role.MANAGER, demo)));
        when(reports.report(eq(demo), eq(YESTERDAY), anyBoolean())).thenReturn(new ReportDto(YESTERDAY,
                "Samedi : 31 tablées accueillies.", DailyReport.Source.TEMPLATE, Instant.now(), false, false));
        mailer = mailer("smtp.test");
    }

    @Test
    void yesterdaysReportGoesToTheManagers() {
        assertThat(mailer.send("demo", YESTERDAY)).containsExactly("gerant@chez-demo.fr");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("gerant@chez-demo.fr");
        assertThat(sent.getValue().getSubject()).startsWith("Chez Démo · rapport du ");
        assertThat(sent.getValue().getText()).startsWith("Bonjour,").contains("31 tablées accueillies", "écran Service");
    }

    @Test
    void todayIsNotSentBecauseItIsNotOver() {
        assertThatThrownBy(() -> mailer.send("demo", YESTERDAY.plusDays(1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("journée terminée");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void withoutMailServerNothingIsSentAndTheRefusalIsExplained() {
        ReportMailer off = mailer("");
        assertThat(off.isConfigured()).isFalse();
        assertThatThrownBy(() -> off.send("demo", YESTERDAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("SPRING_MAIL_HOST");
        off.sendYesterday();
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void theSubjectNamesTheRestaurantAndTheDay() {
        assertThat(ReportMailer.subject(demo, LocalDate.of(2026, 11, 14))).isEqualTo("Chez Démo · rapport du samedi 14 novembre");
    }
}
