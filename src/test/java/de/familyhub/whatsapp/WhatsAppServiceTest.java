package de.familyhub.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Der Service zerlegt eingehende Payloads und legt jede Nachricht roh ab; bereits bekannte IDs werden
// übersprungen, Payloads ohne Einträge bleiben leer.
@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private WhatsAppMessageRepository repository;

    private final ObjectMapper mapper = new ObjectMapper();

    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppService(repository);
    }

    private WhatsAppMessage saved(WhatsAppMessage in) {
        return new WhatsAppMessage(in.id(), in.from(), in.text(), in.timestamp(), in.receivedAt(), in.raw());
    }

    private JsonNode parse(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void storesEachMessageRawWithExtractedFields() throws Exception {
        when(repository.existsById("wamid.A")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> saved(inv.getArgument(0, WhatsAppMessage.class)));

        List<WhatsAppMessage> stored = service.storeIncoming(parse("""
                {"object":"whatsapp_business_account","entry":[{"id":"E","changes":[{"field":"messages","value":{
                "messaging_product":"whatsapp",
                "messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"text","text":{"body":"Hallo"}},
                  {"from":"491572","id":"wamid.B","timestamp":"1700000001","type":"text","text":{"body":"Tschüss"}}
                ]}
                }] }]}"""));

        assertThat(stored).extracting(WhatsAppMessage::id).containsExactly("wamid.A", "wamid.B");
        assertThat(stored.get(0).from()).isEqualTo("491571");
        assertThat(stored.get(0).text()).isEqualTo("Hallo");
        assertThat(stored.get(0).timestamp()).isNotNull();
        assertThat(stored.get(0).raw()).contains("wamid.A").contains("Hallo");
    }

    @Test
    void skipsMessagesThatAreAlreadyKnown() throws Exception {
        when(repository.existsById("wamid.A")).thenReturn(true);

        List<WhatsAppMessage> stored = service.storeIncoming(parse("""
                {"entry":[{"changes":[{"value":{"messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"text","text":{"body":"Hallo"}}
                ]}}]}] }"""));

        assertThat(stored).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void messageWithoutTextStillStoresRawPayload() throws Exception {
        when(repository.existsById("wamid.A")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> saved(inv.getArgument(0, WhatsAppMessage.class)));

        List<WhatsAppMessage> stored = service.storeIncoming(parse("""
                {"entry":[{"changes":[{"value":{"messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"image","image":{"id":"IMG1"}}
                ]}}]}] }"""));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).text()).isNull();
        assertThat(stored.get(0).raw()).contains("wamid.A");
    }

    @Test
    void payloadWithoutEntriesStoresNothing() throws Exception {
        List<WhatsAppMessage> stored = service.storeIncoming(parse(
                "{\"object\":\"whatsapp_business_account\",\"entry\":[]}"));

        assertThat(stored).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void nullPayloadIsRejected() {
        assertThatThrownBy(() -> service.storeIncoming(null)).isInstanceOf(WhatsAppException.class);
    }

    @Test
    void rawPayloadKeepsUnparsedStructure() throws Exception {
        when(repository.existsById("wamid.A")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> saved(inv.getArgument(0, WhatsAppMessage.class)));

        service.storeIncoming(parse("""
                {"entry":[{"changes":[{"value":{"messages":[
                  {"from":"491571","id":"wamid.A","timestamp":"1700000000","type":"text",
                   "text":{"body":"Hallo","mentioned_jid":[]},"customer_id":"C1"}
                ]}}]}] }"""));

        ArgumentCaptor<WhatsAppMessage> captor = ArgumentCaptor.forClass(WhatsAppMessage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().raw()).contains("customer_id").contains("mentioned_jid");
    }
}
