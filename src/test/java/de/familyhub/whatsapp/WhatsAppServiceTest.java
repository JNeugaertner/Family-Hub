package de.familyhub.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.familyhub.inbox.Channel;
import de.familyhub.inbox.InboundMessage;
import de.familyhub.inbox.Inbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Zerlegen eines WhatsApp-Payloads in Einzelnachrichten für den gemeinsamen Eingang
@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private Inbox inbox;

    private final ObjectMapper mapper = new ObjectMapper();

    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppService(inbox, Clock.systemUTC());
    }

    private JsonNode parse(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void storesEachMessageRawWithExtractedFields() throws Exception {
        when(inbox.store(any())).thenReturn(true);

        List<InboundMessage> stored = service.storeIncoming(parse("""
                {"object":"whatsapp_business_account","entry":[{"id":"E","changes":[{"field":"messages","value":{
                "messaging_product":"whatsapp",
                "messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"text","text":{"body":"Hallo"}},
                  {"from":"491572","id":"wamid.B","timestamp":"1700000001","type":"text","text":{"body":"Tschüss"}}
                ]}
                }] }]}"""));

        assertThat(stored).extracting(InboundMessage::id).containsExactly("whatsapp:wamid.A", "whatsapp:wamid.B");
        InboundMessage first = stored.get(0);
        assertThat(first.channel()).isEqualTo(Channel.WHATSAPP);
        assertThat(first.sender()).isEqualTo("491571");
        assertThat(first.senderKey()).isEqualTo("whatsapp:491571");
        assertThat(first.text()).isEqualTo("Hallo");
        assertThat(first.sentAt()).isEqualTo(Instant.ofEpochSecond(1700000000));
        assertThat(first.receivedAt()).isNotNull();
        assertThat(first.raw()).contains("wamid.A").contains("Hallo");
    }

    @Test
    void skipsMessagesThatAreAlreadyKnown() throws Exception {
        when(inbox.store(any())).thenReturn(false);

        List<InboundMessage> stored = service.storeIncoming(parse("""
                {"entry":[{"changes":[{"value":{"messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"text","text":{"body":"Hallo"}}
                ]}}]}] }"""));

        assertThat(stored).isEmpty();
    }

    @Test
    void messageWithoutTextStillStoresRawPayload() throws Exception {
        when(inbox.store(any())).thenReturn(true);

        List<InboundMessage> stored = service.storeIncoming(parse("""
                {"entry":[{"changes":[{"value":{"messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"image","image":{"id":"IMG1"}}
                ]}}]}] }"""));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).text()).isNull();
        assertThat(stored.get(0).raw()).contains("wamid.A");
    }

    @Test
    void payloadWithoutEntriesStoresNothing() throws Exception {
        List<InboundMessage> stored = service.storeIncoming(parse(
                "{\"object\":\"whatsapp_business_account\",\"entry\":[]}"));

        assertThat(stored).isEmpty();
        verify(inbox, never()).store(any());
    }

    @Test
    void nullPayloadIsRejected() {
        assertThatThrownBy(() -> service.storeIncoming(null)).isInstanceOf(WhatsAppException.class);
    }

    @Test
    void rawPayloadKeepsUnparsedStructure() throws Exception {
        when(inbox.store(any())).thenReturn(true);

        service.storeIncoming(parse("""
                {"entry":[{"changes":[{"value":{"messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"text",
                   "text":{"body":"Hallo","mentioned_jid":[]},"customer_id":"C1"}
                ]}}]}] }"""));

        ArgumentCaptor<InboundMessage> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(inbox).store(captor.capture());
        assertThat(captor.getValue().raw()).contains("customer_id").contains("mentioned_jid");
    }
}
