package com.shelflife.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentMerchantResolver currentMerchantResolver;

    public WebConfig(CurrentMerchantResolver currentMerchantResolver) {
        this.currentMerchantResolver = currentMerchantResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMerchantResolver);
    }
}
