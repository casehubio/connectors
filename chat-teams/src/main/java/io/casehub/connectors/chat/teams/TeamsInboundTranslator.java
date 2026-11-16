package io.casehub.connectors.chat.teams;

import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.InboundTranslator;

public class TeamsInboundTranslator implements InboundTranslator {

    @Override
    public String connectorType() {
        return InboundConnectorTypes.TEAMS;
    }

    @Override
    public ReceivedMessage translate(InboundMessage msg) {
        String activityType = msg.metadata().get("teams-activity-type");
        if (!"message".equals(activityType)) {
            throw new IllegalArgumentException(
                    "Non-message activity type: " + activityType
                    + " — not translatable to ReceivedMessage");
        }

        var    channel    = new ChatChannelRef(msg.externalChannelRef());
        String activityId = msg.metadata().get("teams-activity-id");
        var    messageRef = new ChatMessageRef(channel, activityId);

        String replyToId = msg.metadata().get("teams-reply-to-id");
        ChatMessageRef parentRef = replyToId != null
                                   ? new ChatMessageRef(channel, replyToId) : null;

        String      raw = msg.content();
        ChatContent content;
        if (containsHtml(raw)) {
            content = new ChatContent(htmlToPlainText(raw), htmlToMarkdown(raw),
                                      java.util.List.of(), java.util.List.of());
        } else {
            content = new ChatContent(raw);
        }

        return new ReceivedMessage(
                TeamsChatPlatform.PLATFORM_ID,
                channel,
                messageRef,
                parentRef,
                new MemberRef(msg.externalSenderId()),
                content,
                msg.receivedAt());
    }

    private static final java.util.regex.Pattern TAG_PATTERN =
            java.util.regex.Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");

    static String htmlToMarkdown(String html) {
        if (html == null || html.isEmpty()) {return html;}
        String s = html;
        s = s.replaceAll("<at[^>]*>.*?</at>", "");
        s = s.replaceAll("(?i)<b>([^<]*)</b>", "**$1**");
        s = s.replaceAll("(?i)<strong>([^<]*)</strong>", "**$1**");
        s = s.replaceAll("(?i)<i>([^<]*)</i>", "*$1*");
        s = s.replaceAll("(?i)<em>([^<]*)</em>", "*$1*");
        s = s.replaceAll("(?i)<strike>([^<]*)</strike>", "~~$1~~");
        s = s.replaceAll("(?i)<s>([^<]*)</s>", "~~$1~~");
        s = s.replaceAll("(?i)<del>([^<]*)</del>", "~~$1~~");
        s = s.replaceAll("(?i)<code>([^<]*)</code>", "`$1`");
        s = s.replaceAll("(?i)<pre>([^<]*)</pre>", "```\n$1\n```");
        s = s.replaceAll("(?i)<a\\s+href=\"([^\"]*)\"[^>]*>([^<]*)</a>", "[$2]($1)");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)<li>([^<]*)</li>", "- $1\n");
        s = s.replaceAll("(?i)</?[uo]l>", "");
        s = s.replaceAll("(?i)</?p>", "\n");
        s = s.replaceAll("(?i)</?div>", "\n");
        s = s.replaceAll("(?i)<h([1-6])>([^<]*)</h\\1>", "\n$2\n");
        s = TAG_PATTERN.matcher(s).replaceAll("");
        s = s.replaceAll("&", "&");
        s = s.replaceAll("<", "<");
        s = s.replaceAll(">", ">");
        s = s.replaceAll("&quot;", "\"");
        s = s.replaceAll("&#39;", "'");
        s = s.replaceAll("&nbsp;", " ");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.strip();
    }

    static String htmlToPlainText(String html) {
        if (html == null || html.isEmpty()) {return html;}
        String s = html;
        s = s.replaceAll("<at[^>]*>.*?</at>", "");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</?p>", "\n");
        s = s.replaceAll("(?i)</?div>", "\n");
        s = TAG_PATTERN.matcher(s).replaceAll("");
        s = s.replaceAll("&", "&");
        s = s.replaceAll("<", "<");
        s = s.replaceAll(">", ">");
        s = s.replaceAll("&quot;", "\"");
        s = s.replaceAll("&#39;", "'");
        s = s.replaceAll("&nbsp;", " ");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.strip();
    }

    static boolean containsHtml(String text) {
        return text != null && TAG_PATTERN.matcher(text).find();
    }
}
