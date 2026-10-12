/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.infrastructure.ldap;

import de.uni_passau.fim.se2.domain.exceptions.DirectoryUnavailableException;
import de.uni_passau.fim.se2.domain.interfaces.FimAccountDirectory;
import de.uni_passau.fim.se2.domain.model.FimAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Checks FIM-Account credentials by binding to the FIM LDAP server as the user
 * (bind authentication): the server verifies the password, CodeOwl never
 * compares or stores it.
 */
@Slf4j
@Component
public class LdapFimAccountDirectory implements FimAccountDirectory {

    private final BindAuthenticator authenticator;

    public LdapFimAccountDirectory(@Value("${spring.ldap.urls}") String ldapUrl,
                                   @Value("${spring.ldap.base}") String ldapBaseDn) {
        // The context source gets no base DN: BindAuthenticator prepends it to the
        // DN pattern, and the pattern below already carries the full DN.
        DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(ldapUrl);
        // Without timeouts an unresponsive server would hold the request thread indefinitely.
        contextSource.setBaseEnvironmentProperties(Map.of(
                "com.sun.jndi.ldap.connect.timeout", "5000",
                "com.sun.jndi.ldap.read.timeout", "10000"
        ));
        contextSource.afterPropertiesSet();

        authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserDnPatterns(new String[] { "uid={0}," + ldapBaseDn });
    }

    @Override
    public Optional<FimAccount> authenticate(String username, String password) {
        String uid = FimAccount.normalizeUid(username);
        DirContextOperations entry;
        try {
            entry = authenticator.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(uid, password));
        } catch (BadCredentialsException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error("FIM LDAP login failed for uid {}: {}", uid, e.getMessage(), e);
            throw new DirectoryUnavailableException("FIM LDAP server not available", e);
        }

        return Optional.of(new FimAccount(
                uid,
                entry.getStringAttribute("mail"),
                entry.getStringAttribute("givenName"),
                entry.getStringAttribute("sn")
        ));
    }
}
