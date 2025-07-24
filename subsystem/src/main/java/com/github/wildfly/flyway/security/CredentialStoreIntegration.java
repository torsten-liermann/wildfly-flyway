package com.github.wildfly.flyway.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.Logger;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * Integration with WildFly Elytron credential stores for secure password handling.
 * This class provides secure storage and retrieval of database credentials.
 */
public class CredentialStoreIntegration {
    
    private static final Logger log = Logger.getLogger(CredentialStoreIntegration.class);
    
    private static final String CREDENTIAL_STORE_CAPABILITY = "org.wildfly.security.credential-store";
    private static final String DEFAULT_ALIAS_PREFIX = "flyway.datasource.";
    
    private final Map<String, CredentialStore> credentialStores = new HashMap<>();
    
    /**
     * Retrieves a password from the specified credential store.
     *
     * @param context The operation context
     * @param credentialStoreName The name of the credential store
     * @param alias The alias for the credential
     * @return The password as a char array, or null if not found
     * @throws OperationFailedException if credential retrieval fails
     */
    public char[] getPassword(OperationContext context, String credentialStoreName, String alias) 
            throws OperationFailedException {
        
        if (credentialStoreName == null || alias == null) {
            return null;
        }
        
        try {
            CredentialStore store = getCredentialStore(context, credentialStoreName);
            if (store == null) {
                log.debugf("Credential store '%s' not found", credentialStoreName);
                return null;
            }
            
            if (!store.exists(alias, PasswordCredential.class)) {
                log.debugf("Alias '%s' not found in credential store '%s'", alias, credentialStoreName);
                return null;
            }
            
            PasswordCredential credential = store.retrieve(alias, PasswordCredential.class);
            if (credential != null) {
                ClearPassword password = credential.getPassword(ClearPassword.class);
                if (password != null) {
                    return password.getPassword();
                }
            }
            
            return null;
            
        } catch (CredentialStoreException e) {
            throw new OperationFailedException("Failed to retrieve credential", e);
        }
    }
    
    /**
     * Stores a password in the specified credential store.
     *
     * @param context The operation context
     * @param credentialStoreName The name of the credential store
     * @param alias The alias for the credential
     * @param password The password to store
     * @throws OperationFailedException if credential storage fails
     */
    public void storePassword(OperationContext context, String credentialStoreName, 
                              String alias, char[] password) throws OperationFailedException {
        
        if (credentialStoreName == null || alias == null || password == null) {
            throw new IllegalArgumentException("Credential store name, alias, and password must not be null");
        }
        
        try {
            CredentialStore store = getCredentialStore(context, credentialStoreName);
            if (store == null) {
                throw new OperationFailedException("Credential store '" + credentialStoreName + "' not found");
            }
            
            ClearPassword clearPassword = ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, password);
            PasswordCredential credential = new PasswordCredential(clearPassword);
            
            store.store(alias, credential);
            store.flush();
            
            log.infof("Stored credential for alias '%s' in credential store '%s'", alias, credentialStoreName);
            
        } catch (CredentialStoreException e) {
            throw new OperationFailedException("Failed to store credential", e);
        }
    }
    
    /**
     * Removes a password from the specified credential store.
     *
     * @param context The operation context
     * @param credentialStoreName The name of the credential store
     * @param alias The alias for the credential
     * @throws OperationFailedException if credential removal fails
     */
    public void removePassword(OperationContext context, String credentialStoreName, String alias) 
            throws OperationFailedException {
        
        if (credentialStoreName == null || alias == null) {
            return;
        }
        
        try {
            CredentialStore store = getCredentialStore(context, credentialStoreName);
            if (store == null) {
                return;
            }
            
            if (store.exists(alias, PasswordCredential.class)) {
                store.remove(alias, PasswordCredential.class);
                store.flush();
                log.infof("Removed credential for alias '%s' from credential store '%s'", alias, credentialStoreName);
            }
            
        } catch (CredentialStoreException e) {
            throw new OperationFailedException("Failed to remove credential", e);
        }
    }
    
    /**
     * Creates a CallbackHandler for database authentication using credentials from the store.
     *
     * @param context The operation context
     * @param credentialStoreName The name of the credential store
     * @param userAlias The alias for the username
     * @param passwordAlias The alias for the password
     * @return A CallbackHandler for authentication
     * @throws OperationFailedException if handler creation fails
     */
    public CallbackHandler createCallbackHandler(OperationContext context, String credentialStoreName,
                                                 String userAlias, String passwordAlias) 
            throws OperationFailedException {
        
        final String username = getUsernameFromStore(context, credentialStoreName, userAlias);
        final char[] password = getPassword(context, credentialStoreName, passwordAlias);
        
        return new CallbackHandler() {
            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback && username != null) {
                        ((NameCallback) callback).setName(username);
                    } else if (callback instanceof PasswordCallback && password != null) {
                        ((PasswordCallback) callback).setPassword(password);
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            }
        };
    }
    
    /**
     * Generates a secure alias for storing datasource credentials.
     *
     * @param datasourceName The name of the datasource
     * @param credentialType The type of credential (username/password)
     * @return The generated alias
     */
    public String generateAlias(String datasourceName, String credentialType) {
        return DEFAULT_ALIAS_PREFIX + datasourceName + "." + credentialType;
    }
    
    /**
     * Validates that required credentials exist in the store.
     *
     * @param context The operation context
     * @param credentialStoreName The name of the credential store
     * @param aliases The aliases to check
     * @return true if all credentials exist, false otherwise
     */
    public boolean validateCredentialsExist(OperationContext context, String credentialStoreName, 
                                            String... aliases) {
        try {
            CredentialStore store = getCredentialStore(context, credentialStoreName);
            if (store == null) {
                return false;
            }
            
            for (String alias : aliases) {
                if (!store.exists(alias, PasswordCredential.class)) {
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.debugf("Error validating credentials: %s", e.getMessage());
            return false;
        }
    }
    
    private CredentialStore getCredentialStore(OperationContext context, String storeName) 
            throws OperationFailedException {
        
        CredentialStore store = credentialStores.get(storeName);
        if (store != null) {
            return store;
        }
        
        // Look up the credential store from the capability registry
        try {
            // This would normally use the capability registry in WildFly
            // For now, return null to indicate store not found
            log.debugf("Looking up credential store: %s", storeName);
            return null;
            
        } catch (Exception e) {
            throw new OperationFailedException("Failed to access credential store", e);
        }
    }
    
    private String getUsernameFromStore(OperationContext context, String credentialStoreName, 
                                        String alias) throws OperationFailedException {
        // For usernames, we might store them as a different type of credential
        // or as a plain attribute. For now, retrieve as a password and convert.
        char[] chars = getPassword(context, credentialStoreName, alias);
        return chars != null ? new String(chars) : null;
    }
    
    /**
     * Clears any cached credential stores.
     */
    public void clearCache() {
        credentialStores.clear();
    }
}
