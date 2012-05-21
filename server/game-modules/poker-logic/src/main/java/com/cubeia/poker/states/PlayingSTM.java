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

import com.cubeia.poker.action.PokerAction;
import com.cubeia.poker.adapter.HandEndStatus;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.result.HandResult;
import com.cubeia.poker.result.Result;
import com.cubeia.poker.sitout.SitoutCalculator;
import com.cubeia.poker.timing.Periods;
import com.cubeia.poker.timing.TimingProfile;
import com.cubeia.poker.tournament.RoundReport;
import com.cubeia.poker.variant.HandFinishedListener;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PlayingSTM extends AbstractPokerGameSTM implements HandFinishedListener {

    private static final long serialVersionUID = 7076228045164551068L;

    private static final Logger log = LoggerFactory.getLogger(PlayingSTM.class);

    public String toString() {
        return "PlayingState";
    }

    @Override
    public void enterState() {
        gameType.addHandFinishedListener(this);
    }

    @Override
    public void act(PokerAction action) {
        gameType.act(action);
    }

    @Override
    public void timeout() {
        gameType.timeout();
    }

    @Override
    public boolean isCurrentlyWaitingForPlayer(int playerId) {
        return gameType.isCurrentlyWaitingForPlayer(playerId);
    }

    @Override
    public void handFinished(HandResult result, HandEndStatus status) {
        context.setHandFinished(true);

        awardWinners(result.getResults());

        if (context.isTournamentTable()) {
            // Report round to tournament coordinator and wait for notification
            sendTournamentRoundReport();
        } else {
            getServerAdapter().notifyHandEnd(result, status);

            for (PokerPlayer player : context.getPlayerMap().values()) {
                getServerAdapter().notifyPlayerBalance(player);
            }

            getServerAdapter().performPendingBuyIns(context.getPlayerMap().values());

            // clean up players here and make leaving players leave and so on also update the lobby
            getServerAdapter().cleanupPlayers(new SitoutCalculator());

            //setPlayersWithoutMoneyAsSittingOut();
            sendBuyinInfoToPlayersWithoutMoney();

            TimingProfile timing = context.getSettings().getTiming();
            log.debug("Schedule hand over timeout in: {}", timing != null ? timing.getTime(Periods.START_NEW_HAND) : 0);
            getServerAdapter().scheduleTimeout(timing.getTime(Periods.START_NEW_HAND));
        }

        changeState(new WaitingToStartSTM());
    }

    @Override
    public boolean isPlayerInHand(int playerId) {
        return context.isPlayerInHand(playerId);
    }

    /**
     * Send buy-in question to all players in the current hand that do not have enough money to pay ante.
     */
    @VisibleForTesting
    protected void sendBuyinInfoToPlayersWithoutMoney() {
        for (PokerPlayer player : context.getPlayerMap().values()) {

            boolean canPlayerAffordEntryBet = gameType.canPlayerAffordEntryBet(player, context.getSettings(), true);
            if (!canPlayerAffordEntryBet) {
                if (!player.isBuyInRequestActive()) {
                    getServerAdapter().notifyBuyInInfo(player.getId(), true);
                }
            }
        }
    }

    void sendTournamentRoundReport() {
        RoundReport report = new RoundReport();
        for (PokerPlayer player : context.getPlayerMap().values()) {
            report.setSetBalance(player.getId(), player.getBalance());
        }
        log.debug("Sending tournament round report: " + report);
        getServerAdapter().reportTournamentRound(report);
    }

    private void awardWinners(Map<PokerPlayer, Result> results) {
        for (Map.Entry<PokerPlayer, Result> entry : results.entrySet()) {
            PokerPlayer player = entry.getKey();
            player.addChips(entry.getValue().getWinningsIncludingOwnBets());
        }
    }


}
