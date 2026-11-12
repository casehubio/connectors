package io.casehub.connectors.mobile.spi;

import java.util.Set;

record BuiltMobilePlatform(
        String id,
        Set<Class<?>> supported,
        MobilePlatform.Sms sms,
        MobilePlatform.VoiceCall voiceCall,
        MobilePlatform.VoiceCallControl voiceCallControl,
        MobilePlatform.Voicemail voicemail,
        MobilePlatform.Messaging messaging,
        MobilePlatform.NumberInfo numberInfo) implements MobilePlatform {

    @Override
    public boolean supports(Class<?> capability) {
        return supported.contains(capability);
    }
}
