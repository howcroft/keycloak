/*
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.keycloak.services.managers;

import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserFederationProvider;
import org.keycloak.models.UserFederationProviderFactory;
import org.keycloak.models.UserFederationProviderModel;
import org.keycloak.models.UserFederationSyncResult;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.ServicesLogger;
import org.keycloak.timer.TimerProvider;


import java.util.List;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class UsersSyncManager {

    protected static final ServicesLogger logger = ServicesLogger.ROOT_LOGGER;

    /**
     * Check federationProviderModel of all realms and possibly start periodic sync for them
     *
     * @param sessionFactory
     * @param timer
     */
    public void bootstrapPeriodic(final KeycloakSessionFactory sessionFactory, final TimerProvider timer) {
        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                List<RealmModel> realms = session.realms().getRealms();
                for (final RealmModel realm : realms) {
                    List<UserFederationProviderModel> federationProviders = realm.getUserFederationProviders();
                    for (final UserFederationProviderModel fedProvider : federationProviders) {
                        refreshPeriodicSyncForProvider(sessionFactory, timer, fedProvider, realm.getId());
                    }
                }
            }
        });
    }

    public UserFederationSyncResult syncAllUsers(final KeycloakSessionFactory sessionFactory, String realmId, final UserFederationProviderModel fedProvider) {
        final UserFederationProviderFactory fedProviderFactory = (UserFederationProviderFactory) sessionFactory.getProviderFactory(UserFederationProvider.class, fedProvider.getProviderName());
        updateLastSyncInterval(sessionFactory, fedProvider, realmId);
        return fedProviderFactory.syncAllUsers(sessionFactory, realmId, fedProvider);
    }

    public UserFederationSyncResult syncChangedUsers(final KeycloakSessionFactory sessionFactory, String realmId, final UserFederationProviderModel fedProvider) {
        final UserFederationProviderFactory fedProviderFactory = (UserFederationProviderFactory) sessionFactory.getProviderFactory(UserFederationProvider.class, fedProvider.getProviderName());

        // See when we did last sync.
        int oldLastSync = fedProvider.getLastSync();
        updateLastSyncInterval(sessionFactory, fedProvider, realmId);
        return fedProviderFactory.syncChangedUsers(sessionFactory, realmId, fedProvider, Time.toDate(oldLastSync));
    }

    public void refreshPeriodicSyncForProvider(final KeycloakSessionFactory sessionFactory, TimerProvider timer, final UserFederationProviderModel fedProvider, final String realmId) {
        if (fedProvider.getFullSyncPeriod() > 0) {
            // We want periodic full sync for this provider
            timer.schedule(new Runnable() {

                @Override
                public void run() {
                    try {
                        syncAllUsers(sessionFactory, realmId, fedProvider);
                    } catch (Throwable t) {
                        logger.errorDuringFullUserSync(t);
                    }
                }

            }, fedProvider.getFullSyncPeriod() * 1000, fedProvider.getId() + "-FULL");
        } else {
            timer.cancelTask(fedProvider.getId() + "-FULL");
        }

        if (fedProvider.getChangedSyncPeriod() > 0) {
            // We want periodic sync of just changed users for this provider
            timer.schedule(new Runnable() {

                @Override
                public void run() {
                    try {
                        syncChangedUsers(sessionFactory, realmId, fedProvider);
                    } catch (Throwable t) {
                        logger.errorDuringChangedUserSync(t);
                    }
                }

            }, fedProvider.getChangedSyncPeriod() * 1000, fedProvider.getId() + "-CHANGED");

        } else {
            timer.cancelTask(fedProvider.getId() + "-CHANGED");
        }
    }

    public void removePeriodicSyncForProvider(TimerProvider timer, final UserFederationProviderModel fedProvider) {
        timer.cancelTask(fedProvider.getId() + "-FULL");
        timer.cancelTask(fedProvider.getId() + "-CHANGED");
    }

    // Update interval of last sync for given UserFederationProviderModel. Do it in separate transaction
    private void updateLastSyncInterval(final KeycloakSessionFactory sessionFactory, final UserFederationProviderModel fedProvider, final String realmId) {
        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                RealmModel persistentRealm = session.realms().getRealm(realmId);
                List<UserFederationProviderModel> persistentFedProviders = persistentRealm.getUserFederationProviders();
                for (UserFederationProviderModel persistentFedProvider : persistentFedProviders) {
                    if (fedProvider.getId().equals(persistentFedProvider.getId())) {
                        // Update persistent provider in DB
                        int lastSync = Time.currentTime();
                        persistentFedProvider.setLastSync(lastSync);
                        persistentRealm.updateUserFederationProvider(persistentFedProvider);

                        // Update "cached" reference
                        fedProvider.setLastSync(lastSync);
                    }
                }
            }

        });
    }

}
