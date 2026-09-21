package io.casehub.connectors.graphql;

import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.spi.EventTiming;
import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.platform.simulation.Simulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamCollaborationSimulationTest {

    private Simulation sim;

    @BeforeEach
    void setUp() {
        var calendarInfo = new CalendarInfo("cal-work-001", "Work Calendar",
                "Team meetings", true);
        var calendarEvent = new CalendarEvent("evt-standup-001", "cal-work-001",
                "Daily Standup", "Team sync", "Room 3A",
                new EventTiming.Timed(
                        Instant.parse("2026-09-20T09:00:00Z"),
                        Instant.parse("2026-09-20T09:15:00Z"),
                        ZoneId.of("Europe/London")),
                List.of("alice@example.com", "bob@example.com"),
                "recur-standup");
        var channel = new Channel(new ChatChannelRef("ch-general"),
                "#general", "General", "General channel", false, 12);
        var member = new Member(new MemberRef("user-001"), "alice");
        var sendResult = SendResult.success(
                new ChatMessageRef(new ChatChannelRef("ch-general"), "msg-sim-001"),
                Instant.parse("2026-09-20T10:00:00Z"));

        sim = Simulation.forTest("household")
                .seed("calendar-platform.listCalendars", null,
                        List.of(calendarInfo))
                .seed("calendar-platform.listEvents", null,
                        List.of(calendarEvent))
                .stub("calendar-platform.getEvent", "evt-standup-001",
                        calendarEvent)
                .seed("chat-platform.discovery.listChannels", null,
                        List.of(channel))
                .seed("chat-platform.members.list", null,
                        List.of(member))
                .seed("chat-platform.messaging.send", null, sendResult)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void flatSpi_calendarListCalendars_resolvesFromCorpus() {
        List<CalendarInfo> calendars = sim.resolve(
                "calendar-platform.listCalendars", null);
        assertThat(calendars).hasSize(1);
        assertThat(calendars.get(0).id()).isEqualTo("cal-work-001");
        assertThat(calendars.get(0).summary()).isEqualTo("Work Calendar");
        assertThat(calendars.get(0).primary()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void flatSpi_calendarListEvents_resolvesFromCorpus() {
        List<CalendarEvent> events = sim.resolve(
                "calendar-platform.listEvents", null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).id()).isEqualTo("evt-standup-001");
        assertThat(events.get(0).summary()).isEqualTo("Daily Standup");
        assertThat(events.get(0).timing()).isInstanceOf(EventTiming.Timed.class);
    }

    @Test
    void flatSpi_calendarGetEvent_resolvesViaKeyLookup() {
        CalendarEvent event = sim.resolve(
                "calendar-platform.getEvent", "evt-standup-001");
        assertThat(event.id()).isEqualTo("evt-standup-001");
        assertThat(event.calendarId()).isEqualTo("cal-work-001");
        assertThat(event.attendees()).containsExactly(
                "alice@example.com", "bob@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void capabilitySpi_chatDiscovery_resolvesFromCorpus() {
        List<Channel> channels = sim.resolve(
                "chat-platform.discovery.listChannels", null);
        assertThat(channels).hasSize(1);
        assertThat(channels.get(0).ref().id()).isEqualTo("ch-general");
        assertThat(channels.get(0).name()).isEqualTo("#general");
    }

    @Test
    @SuppressWarnings("unchecked")
    void capabilitySpi_chatMembers_resolvesFromCorpus() {
        List<Member> members = sim.resolve(
                "chat-platform.members.list", null);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).ref().id()).isEqualTo("user-001");
        assertThat(members.get(0).displayName()).isEqualTo("alice");
    }

    @Test
    void capabilitySpi_chatMessaging_resolvesFromCorpus() {
        SendResult result = sim.resolve(
                "chat-platform.messaging.send", null);
        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef().messageId()).isEqualTo("msg-sim-001");
    }

    @Test
    void journalRecords_flatAndDottedQualifiedNames() {
        var overlay = sim.overlay();

        sim.resolve("calendar-platform.listCalendars", null);
        sim.resolve("chat-platform.messaging.send", null);
        sim.resolve("chat-platform.discovery.listChannels", null);

        var verifier = sim.verifier();
        verifier.method("calendar-platform.listCalendars").wasCalled(1);
        verifier.method("chat-platform.messaging.send").wasCalled(1);
        verifier.method("chat-platform.discovery.listChannels").wasCalled(1);
        verifier.inOrder(
                "calendar-platform.listCalendars",
                "chat-platform.messaging.send",
                "chat-platform.discovery.listChannels");

        sim.popOverlay(overlay);
    }
}
