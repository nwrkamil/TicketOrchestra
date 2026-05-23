package com.ticketorchestra.common.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.jakarta.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "com.amazonaws.xray.enabled", havingValue = "true", matchIfMissing = true)
public class XRayConfig {

    @PostConstruct
    public void init() {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        builder.withSamplingStrategy(new NoSamplingStrategy());
        AWSXRay.setGlobalRecorder(builder.build());
    }

    @Bean
    public Filter tracingFilter() {
        return new AWSXRayServletFilter("TicketOrchestra");
    }
}
