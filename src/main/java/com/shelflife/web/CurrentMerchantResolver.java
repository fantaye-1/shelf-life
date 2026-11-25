package com.shelflife.web;

import com.shelflife.store.MerchantRepository;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentMerchant} parameters. Temporary: trusts a request header until app login (M8),
 * so the API must not be exposed publicly before then.
 */
@Component
public class CurrentMerchantResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Merchant-Id";

    private final MerchantRepository merchants;

    public CurrentMerchantResolver(MerchantRepository merchants) {
        this.merchants = merchants;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMerchant.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String raw = webRequest.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            throw new UnknownMerchantException("Missing " + HEADER + " header");
        }
        UUID id;
        try {
            id = UUID.fromString(raw.strip());
        } catch (IllegalArgumentException e) {
            throw new UnknownMerchantException(HEADER + " is not a valid id");
        }
        if (!merchants.existsById(id)) {
            throw new UnknownMerchantException("Unknown merchant");
        }
        return id;
    }
}
