/**
 * Copyright (C) 2010 Cubeia Ltd <info@cubeia.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.cubeia.games.poker.activator;

import com.cubeia.backend.firebase.CashGamesBackendContract;
import com.cubeia.firebase.guice.inject.Service;
import com.cubeia.games.poker.entity.TableConfigTemplate;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ParticipantFactoryImpl implements ParticipantFactory {

    @Inject
    private PokerStateCreator stateCreator;

    @Service
    private CashGamesBackendContract backend;

    @Inject
    private LobbyDomainSelector domainSelector;

    @Inject
    private TableNameManager tableNamer;

    @Override
    public PokerParticipant createParticipantFor(TableConfigTemplate template) {
        return new PokerParticipant(
                template,
                template == null ? null : domainSelector.selectLobbyDomainFor(template),
                stateCreator,
                backend,
                tableNamer);
    }
}