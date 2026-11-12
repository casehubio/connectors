package io.casehub.connectors.mobile.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.CallOptions;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.MessagingChannel;
import io.casehub.connectors.mobile.model.NumberCapabilities;
import io.casehub.connectors.mobile.model.PhoneNumber;
import io.casehub.connectors.mobile.model.RichMessage;
import io.casehub.connectors.mobile.model.SendResult;
import io.casehub.connectors.mobile.model.SmsMessage;
import io.casehub.connectors.mobile.model.TranscriptionResult;
import io.casehub.connectors.mobile.model.VoicemailMessage;

import java.util.List;

public interface MobileBackend {

    SendResult sendSms(String from, String to, String body);

    Page<SmsMessage> listSmsMessages(PageRequest page);

    Call initiateCall(String from, String to, String instruction, CallOptions options);

    Call initiateCall(String from, String to, ConversationHandler handler, CallOptions options);

    Call getCall(String callId);

    Page<Call> listCalls(PageRequest page);

    void hangUp(String callId);

    void transfer(String callId, String transferTo);

    Page<VoicemailMessage> listVoicemails(PageRequest page);

    VoicemailMessage getVoicemail(String id);

    void deleteVoicemail(String id);

    TranscriptionResult transcribeVoicemail(String id);

    SendResult sendMessage(String from, String to, String body, MessagingChannel channel);

    Page<RichMessage> listRichMessages(PageRequest page);

    List<PhoneNumber> listNumbers();

    NumberCapabilities lookupNumber(String number);
}
