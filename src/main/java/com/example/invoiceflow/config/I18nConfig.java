package com.example.invoiceflow.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class I18nConfig {

    public static final Locale DEFAULT_LOCALE = Locale.FRENCH;
    private static final List<Locale> SUPPORTED = List.of(Locale.FRENCH, Locale.ENGLISH);

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(DEFAULT_LOCALE);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    public static Locale toLocale(String code) {
        if (code == null) return DEFAULT_LOCALE;
        return switch (code.toUpperCase()) {
            case "EN" -> Locale.ENGLISH;
            default -> Locale.FRENCH;
        };
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED);
        return resolver;
    }
}
