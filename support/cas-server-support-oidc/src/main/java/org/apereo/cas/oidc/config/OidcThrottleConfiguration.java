package org.apereo.cas.oidc.config;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.support.CasFeatureModule;
import org.apereo.cas.oidc.OidcConstants;
import org.apereo.cas.oidc.issuer.OidcIssuerService;
import org.apereo.cas.throttle.AuthenticationThrottlingExecutionPlan;
import org.apereo.cas.throttle.AuthenticationThrottlingExecutionPlanConfigurer;
import org.apereo.cas.throttle.ThrottledRequestFilter;
import org.apereo.cas.util.spring.RefreshableHandlerInterceptor;
import org.apereo.cas.util.spring.boot.ConditionalOnFeature;

import lombok.val;
import org.pac4j.core.context.JEEContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * This is {@link OidcThrottleConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@Configuration(value = "OidcThrottleConfiguration", proxyBeanMethods = false)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnBean(name = AuthenticationThrottlingExecutionPlan.BEAN_NAME)
@ConditionalOnFeature(feature = CasFeatureModule.FeatureCatalog.OpenIDConnect)
public class OidcThrottleConfiguration {

    @Configuration(value = "OidcThrottleWebMvcConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class OidcThrottleWebMvcConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "oidcThrottleWebMvcConfigurer")
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public WebMvcConfigurer oidcThrottleWebMvcConfigurer(
            @Qualifier(AuthenticationThrottlingExecutionPlan.BEAN_NAME)
            final ObjectProvider<AuthenticationThrottlingExecutionPlan> authenticationThrottlingExecutionPlan) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(final InterceptorRegistry registry) {
                    val handler = new RefreshableHandlerInterceptor(
                        () -> authenticationThrottlingExecutionPlan.getObject().getAuthenticationThrottleInterceptors());
                    registry.addInterceptor(handler)
                        .order(0)
                        .addPathPatterns('/' + OidcConstants.BASE_OIDC_URL + "/**");
                }
            };
        }

    }

    @Configuration(value = "OidcThrottleExecutionPlanConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class OidcThrottleExecutionPlanConfiguration {
        @ConditionalOnMissingBean(name = "oidcAuthenticationThrottlingExecutionPlanConfigurer")
        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public AuthenticationThrottlingExecutionPlanConfigurer oidcAuthenticationThrottlingExecutionPlanConfigurer(
            @Qualifier("oidcThrottledRequestFilter")
            final ThrottledRequestFilter oidcThrottledRequestFilter) {
            return plan -> plan.registerAuthenticationThrottleFilter(oidcThrottledRequestFilter);
        }

    }

    @Configuration(value = "OidcThrottleFilterConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class OidcThrottleFilterConfiguration {
        private static final List<String> THROTTLED_ENDPOINTS = List.of(
            OidcConstants.ACCESS_TOKEN_URL,
            OidcConstants.AUTHORIZE_URL,
            OidcConstants.TOKEN_URL,
            OidcConstants.PROFILE_URL,
            OidcConstants.JWKS_URL,
            OidcConstants.CLIENT_CONFIGURATION_URL,
            OidcConstants.REVOCATION_URL,
            OidcConstants.INTROSPECTION_URL);

        @Bean
        @ConditionalOnMissingBean(name = "oidcThrottledRequestFilter")
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public ThrottledRequestFilter oidcThrottledRequestFilter(
            @Qualifier(OidcIssuerService.BEAN_NAME)
            final OidcIssuerService oidcIssuerService) {
            return (request, response) -> {
                val webContext = new JEEContext(request, response);
                return THROTTLED_ENDPOINTS
                    .stream()
                    .anyMatch(endpoint -> oidcIssuerService.validateIssuer(webContext, endpoint));
            };
        }
    }
}
