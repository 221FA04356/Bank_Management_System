package com.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.session-minutes:20}") int sessionMinutes;

    @Bean public LocaleResolver localeResolver() {
        SessionLocaleResolver r = new SessionLocaleResolver();
        r.setDefaultLocale(Locale.ENGLISH);
        return r;
    }

    @Bean public BCryptPasswordEncoder encoder() { return new BCryptPasswordEncoder(); }

    @Override public void addInterceptors(InterceptorRegistry reg) {
        LocaleChangeInterceptor lang = new LocaleChangeInterceptor();
        lang.setParamName("lang");
        reg.addInterceptor(lang);
        reg.addInterceptor(new AuthInterceptor(sessionMinutes)).excludePathPatterns(
                "/", "/login", "/logout", "/signup/**", "/unlock/**", "/unlock", "/css/**", "/error", "/favicon.ico");
    }
}
