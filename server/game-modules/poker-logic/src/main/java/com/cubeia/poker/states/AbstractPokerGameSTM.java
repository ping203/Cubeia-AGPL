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

package com.cubeia.poker.states;

import com.cubeia.poker.GameType;
import com.cubeia.poker.PokerContext;
import com.cubeia.poker.action.PokerAction;
import com.cubeia.poker.adapter.ServerAdapter;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.player.PokerPlayerStatus;
import com.cubeia.poker.player.SitOutStatus;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static java.util.Arrays.asList;

public abstract class AbstractPokerGameSTM implements PokerGameSTM {

    private static final long serialVersionUID = 1L;

    @VisibleForTesting
    StateChanger stateChanger;

    protected GameType gameType;

    protected PokerContext context;

    private ServerAdapterHolder serverAdapter;

    private static final Logger log = LoggerFactory.getLogger(AbstractPokerGameSTM.class);

    public AbstractPokerGameSTM(GameType gameType, PokerContext context, ServerAdapterHolder serverAdapter, StateChanger stateChanger) {
        this.gameType = gameType;
        this.stateChanger = stateChanger;
        this.context = context;
        this.serverAdapter = serverAdapter;
    }

    protected AbstractPokerGameSTM() {

    }

    @Override
    public void enterState() {
    }

    @Override
    public void timeout() {
        throw new IllegalStateException(this + " is wrong state. Context: " + context);
    }

    @Override
    public void act(PokerAction action) {
        throw new IllegalStateException("PokerState: " + context + " Action: " + action);
    }

    public String getStateDescription() {
        return getClass().getName();
    }

    @Override
    public boolean isCurrentlyWaitingForPlayer(int playerId) {
        return false;
    }

    @Override
    public void playerJoined(PokerPlayer player) {
    }

    @Override
    public boolean isPlayerInHand(int playerId) {
        return false;
    }

    @Override
    public void startHand() {
        log.warn("Won't start hand. Current state = " + this);
    }

    @Override
    public void playerSitsOut(int playerId, SitOutStatus status) {
        context.setSitOutStatus(playerId, status);
        notifyPlayerSittingOut(playerId);
    }

    @Override
    public void playerSitsIn(int playerId) {
        log.debug("player {} is sitting in", playerId);

        PokerPlayer player = context.getPokerPlayer(playerId);
        if (player == null) {
            log.warn("player {} not at table but tried to sit in. Ignoring.", playerId);
            return;
        }

        if (!player.isSittingOut()) {
            log.debug("sit in status has not changed");
            return;
        }

        if (gameType.canPlayerAffordEntryBet(player, context.getSettings(), true)) {
            log.debug("Player {} can afford ante. Sit in", player);

            player.sitIn();
            player.setSitOutNextRound(false);
            player.setSitInAfterSuccessfulBuyIn(false);
            notifyPlayerSittingIn(playerId);

            // This might start the game.
            playerJoined(player);
        } else {
            log.debug("player {} is out of cash, must bring more before joining", player);

            if (!player.isBuyInRequestActive() && player.getRequestedBuyInAmount() == 0L) {
                log.debug("player {} does not have buy in request active so notify buy in info", player);
                notifyBuyinInfo(playerId, true);
            }
        }
    }

    public void notifyPlayerSittingIn(int playerId) {
        log.debug("notifyPlayerSittingIn() id: " + playerId + " status:" + PokerPlayerStatus.SITIN.name());
        boolean isInCurrentHand = context.isPlayerInHand(playerId);
        getServerAdapter().notifyPlayerStatusChanged(playerId, PokerPlayerStatus.SITIN, isInCurrentHand);
    }

    private void notifyBuyinInfo(int playerId, boolean mandatoryBuyin) {
        getServerAdapter().notifyBuyInInfo(playerId, mandatoryBuyin);
    }

    @Override
    public void performPendingBuyIns(Set<PokerPlayer> singleton) {
        log.debug("Not performing pending buy-ins as the current state does not think that's appropriate: " + this);
    }

    protected void doPerformPendingBuyIns(Set<PokerPlayer> players) {
        getServerAdapter().performPendingBuyIns(players);
    }

    private void notifyPlayerSittingOut(int playerId) {
        log.debug("playerSitsOut() id: " + playerId + " status:" + PokerPlayerStatus.SITOUT.name());
        boolean isInCurrentHand = context.isPlayerInHand(playerId);
        getServerAdapter().notifyPlayerStatusChanged(playerId, PokerPlayerStatus.SITOUT, isInCurrentHand);
    }

    protected void changeState(AbstractPokerGameSTM newState) {
        newState.context = context;
        newState.gameType = gameType;
        newState.serverAdapter = serverAdapter;
        newState.stateChanger = stateChanger;
        stateChanger.changeState(newState);
    }

    protected ServerAdapter getServerAdapter() {
        return serverAdapter.get();
    }
}
