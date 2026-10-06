package io.casehub.connectors.spring;

import io.casehub.connectors.travel.spi.TravelPlatform;
import io.casehub.connectors.travel.spi.TravelPlatformService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(TravelPlatformService.class)
public class TravelRefManualConfig {

    @Bean
    @ConditionalOnMissingBean
    TravelPlatformService travelPlatformService(ObjectProvider<TravelPlatform> platforms) {
        return new TravelPlatformService(platforms.orderedStream().toList());
    }
}
