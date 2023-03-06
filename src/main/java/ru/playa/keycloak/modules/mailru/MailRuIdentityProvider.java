package ru.playa.keycloak.modules.mailru;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import ru.playa.keycloak.modules.MessageUtils;
import ru.playa.keycloak.modules.StringUtils;

/**
 * Провайдер OAuth-авторизации через <a href="https://my.mail.ru">Мой Мир</a>.
 * <a href="https://api.mail.ru/docs/guides/oauth/">Подробнее</a>.
 *
 * @author Anatoliy Pokhresnyi
 */
public class MailRuIdentityProvider
        extends OIDCIdentityProvider
        implements SocialIdentityProvider<OIDCIdentityProviderConfig> {

    /**
     * Запрос кода подтверждения.
     */
    private static final String AUTH_URL = "https://oauth.mail.ru/login";

    /**
     * Обмен кода подтверждения на токен.
     */
    private static final String TOKEN_URL = "https://oauth.mail.ru/token";

    /**
     * Запрос информации о пользователе.
     */
    private static final String PROFILE_URL = "https://oauth.mail.ru/userinfo";

    /**
     * Права доступа к данным пользователя по умолчанию.
     */
    private static final String DEFAULT_SCOPE = "userinfo";

    /**
     * Создает объект OAuth-авторизации через
     * <a href="https://my.mail.ru">Мой Мир</a>.
     *
     * @param session Сессия Keycloak.
     * @param config  Конфигурация OAuth-авторизации.
     */
    public MailRuIdentityProvider(KeycloakSession session, MailRuIdentityProviderConfig config) {
        super(session, config);
        config.setAuthorizationUrl(AUTH_URL);
        config.setTokenUrl(TOKEN_URL);
        config.setUserInfoUrl(PROFILE_URL);
    }

    @Override
    protected boolean supportsExternalExchange() {
        return true;
    }

    @Override
    protected String getProfileEndpointForValidation(EventBuilder event) {
        return PROFILE_URL;
    }

    @Override
    protected SimpleHttp buildUserInfoRequest(String subjectToken, String userInfoUrl) {
        logger.info("subjectToken: " + subjectToken);
        logger.info("userInfoUrl: " + userInfoUrl);

        return SimpleHttp.doGet(PROFILE_URL + "?access_token=" + subjectToken, session);
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode profile) {
        logger.info("profile: " + profile.toString());

        BrokeredIdentityContext user = new BrokeredIdentityContext(getJsonProperty(profile, "email"));

        String email = getJsonProperty(profile, "email");

        if (StringUtils.isNullOrEmpty(email)) {
            throw new IllegalArgumentException(MessageUtils.email("Yandex"));
        } else {
            String domain = email.substring(email.indexOf("@") + 1);
            boolean match = Optional
                .ofNullable(((MailRuIdentityProviderConfig) getConfig()).getHostedDomain())
                .map(hd -> hd.split(","))
                .map(Arrays::asList)
                .orElse(Collections.singletonList("*"))
                .stream()
                .noneMatch(hd -> hd.equalsIgnoreCase(domain) || hd.equals("*"));

            if (match) {
                throw new IllegalArgumentException(MessageUtils.hostedDomain("Yandex", domain));
            }
        }

        user.setEmail(email);
        user.setUsername(email);
        user.setFirstName(getJsonProperty(profile, "first_name"));
        user.setLastName(getJsonProperty(profile, "last_name"));

        user.setIdpConfig(getConfig());
        user.setIdp(this);

        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, profile, getConfig().getAlias());

        return user;
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        try {
            return extractIdentityFromProfile(null,
                                              SimpleHttp.doGet(PROFILE_URL + "?access_token=" + accessToken, session)
                                                        .asJson());
        } catch (IOException e) {
            throw new IdentityBrokerException("Could not obtain user profile from MailRu: " + e.getMessage(), e);
        }
    }

    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }
}