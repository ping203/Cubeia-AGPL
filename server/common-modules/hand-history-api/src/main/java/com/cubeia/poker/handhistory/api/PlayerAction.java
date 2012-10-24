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

package com.cubeia.poker.handhistory.api;

public class PlayerAction extends HandHistoryEvent {

    private static final long serialVersionUID = 2633660241901849321L;

	public enum Type {
        SMALL_BLIND, BIG_BLIND, CALL, CHECK, BET, RAISE, FOLD, DECLINE_ENTRY_BET, ANTE, BIG_BLIND_PLUS_DEAD_SMALL_BLIND, DEAD_SMALL_BLIND;
    }

    private Type action;
    private Amount amount;
    private boolean timeout;

    private int playerId;

    public PlayerAction() {
    }

    public PlayerAction(int playerId) {
        this.playerId = playerId;
    }

    public PlayerAction(int playerId, Type action) {
        this.playerId = playerId;
        this.action = action;
    }

    public PlayerAction(int playerId, Type action, Amount amount) {
        this.playerId = playerId;
        this.action = action;
        this.amount = amount;
    }

    public Type getAction() {
        return action;
    }

    public void setAction(Type action) {
        this.action = action;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public Amount getAmount() {
        return amount;
    }

    public boolean isTimout() {
        return timeout;
    }

    public void setTimout(boolean timout) {
        this.timeout = timout;
    }

    public int getPlayerId() {
        return playerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        PlayerAction that = (PlayerAction) o;

        if (playerId != that.playerId) {
            return false;
        }
        if (timeout != that.timeout) {
            return false;
        }
        if (action != that.action) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (action != null ? action.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (timeout ? 1 : 0);
        result = 31 * result + playerId;
        return result;
    }

    @Override
    public String toString() {
        return "PlayerAction [action=" + action + ", amount=" + amount + ", timeout=" + timeout + ", playerId=" + playerId + "]";
    }
}
