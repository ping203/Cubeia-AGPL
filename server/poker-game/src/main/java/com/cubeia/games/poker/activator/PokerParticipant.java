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

import static com.cubeia.poker.variant.telesina.TelesinaDeckUtil.createDeckCards;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cubeia.backend.cashgame.dto.AnnounceTableRequest;
import com.cubeia.backend.firebase.CashGamesBackendContract;
import com.cubeia.backend.firebase.FirebaseCallbackFactory;
import com.cubeia.firebase.api.game.GameDefinition;
import com.cubeia.firebase.api.game.activator.DefaultCreationParticipant;
import com.cubeia.firebase.api.game.lobby.LobbyTableAttributeAccessor;
import com.cubeia.firebase.api.game.table.Table;
import com.cubeia.firebase.api.lobby.LobbyPath;
import com.cubeia.games.poker.FirebaseState;
import com.cubeia.games.poker.lobby.PokerLobbyAttributes;
import com.cubeia.poker.PokerSettings;
import com.cubeia.poker.PokerState;
import com.cubeia.poker.RakeSettings;
import com.cubeia.poker.rng.RNGProvider;
import com.cubeia.poker.rounds.betting.BetStrategyName;
import com.cubeia.poker.timing.TimingFactory;
import com.cubeia.poker.timing.TimingProfile;
import com.cubeia.poker.timing.Timings;
import com.cubeia.poker.variant.PokerVariant;
import com.google.inject.Injector;


/**
 * Table Creator.
 * 
 *
 * @author Fredrik Johansson, Cubeia Ltd
 */
public class PokerParticipant extends DefaultCreationParticipant {
    
	@SuppressWarnings("unused")
	private static Logger log = LoggerFactory.getLogger(PokerParticipant.class);

	public static final int GAME_ID = 4718;
	
	// the minimum buy in as a multiple of ante.
	private static final int MIN_BUY_IN_ANTE_MULTIPLIER = 10;
	
	// max buy in as a multiple of ante
	private static final int MAX_BUY_IN_ANTE_MULTIPLIER = 100;

	/** Number of seats at the table */
	private int seats = 10;

	/** Dummy domain for testing lobby tree */
	private String domain = "A";

	private TimingProfile timingProfile = TimingFactory.getRegistry().getDefaultTimingProfile();

	private Timings timing = Timings.DEFAULT;

	private final int anteLevel;
	
	private final RNGProvider rngProvider;

	// FIXME: This is not good IoC practice *cough*
	private Injector injector;

	private final PokerVariant variant;

	// TODO: get rake settings from property/db
    public static final BigDecimal RAKE_FRACTION = new BigDecimal("0.01");
    public static final long RAKE_LIMIT = 500;
    public static final long RAKE_LIMIT_HEADS_UP = 150;

    private final CashGamesBackendContract cashGameBackendService;

    
	public PokerParticipant(int seats, String domain, int anteLevel, Timings timing, PokerVariant variant, 
	    RNGProvider rngProvider, CashGamesBackendContract cashGameBackendService) {
	    
		super();
		this.seats = seats;
		this.domain = domain;
		this.timing = timing;
		this.anteLevel = anteLevel;
		this.variant = variant;
        this.cashGameBackendService = cashGameBackendService;
		this.timingProfile  = TimingFactory.getRegistry().getTimingProfile(timing);
		this.rngProvider = rngProvider;
	}


	public LobbyPath getLobbyPath() {
		LobbyPath path = new LobbyPath(GAME_ID, domain + "/" + variant.name());
		return path;
	}

	public void setInjector(Injector injector) {
		this.injector = injector;
	}

	/* (non-Javadoc)
	 * @see com.cubeia.firebase.api.game.activator.DefaultCreationParticipant#getLobbyPathForTable(com.cubeia.firebase.api.game.table.Table)
	 */
	@Override 
	public LobbyPath getLobbyPathForTable(Table table) {
		LobbyPath path = getLobbyPath();
		path.setObjectId(table.getId());
		return path;
	}

	@Override
	public void tableCreated(Table table, LobbyTableAttributeAccessor acc) {
		super.tableCreated(table, acc);
		PokerState pokerState = injector.getInstance(PokerState.class);

        PokerSettings settings = new PokerSettings(anteLevel, anteLevel * MIN_BUY_IN_ANTE_MULTIPLIER, anteLevel * MAX_BUY_IN_ANTE_MULTIPLIER, timingProfile, variant, 
		    table.getPlayerSet().getSeatingMap().getNumberOfSeats(), BetStrategyName.NO_LIMIT, 
		    new RakeSettings(RAKE_FRACTION, RAKE_LIMIT, RAKE_LIMIT_HEADS_UP), "MOCK::" + table.getId());
		pokerState.init(rngProvider, settings);
		pokerState.setAdapterState(new FirebaseState());
		pokerState.setId(table.getId());
		table.getGameState().setState(pokerState);

		// FIXME: Refactor these attributes to the PokerLobbyAttributes enum instead
		acc.setIntAttribute("VISIBLE_IN_LOBBY", 0);
		acc.setStringAttribute("SPEED", timing.name());
		// acc.setIntAttribute("ANTE", anteLevel);
		acc.setIntAttribute("BETTING_GAME_ANTE", anteLevel);
		acc.setStringAttribute("BETTING_GAME_BETTING_MODEL", "NO_LIMIT");
		acc.setStringAttribute("MONETARY_TYPE", "REAL_MONEY");
		acc.setIntAttribute(PokerLobbyAttributes.VISIBLE_IN_LOBBY.name(), 1);
		acc.setStringAttribute("VARIANT", variant.name());
		acc.setIntAttribute("MIN_BUY_IN", pokerState.getMinBuyIn());
		acc.setIntAttribute("MAX_BUY_IN", pokerState.getMaxBuyIn());
		int deckSize = createDeckCards(pokerState.getTableSize()).size();
		acc.setIntAttribute("DECK_SIZE", deckSize);
		
		FirebaseCallbackFactory callbackFactory = cashGameBackendService.getCallbackFactory();
		AnnounceTableRequest announceRequest = new AnnounceTableRequest(table.getId());   // TODO: this should be the id from the table record
        cashGameBackendService.announceTable(announceRequest, callbackFactory.createAnnounceTableCallback(table));
	}

	@Override
	public String getTableName(GameDefinition def, Table t) {
		return variant.name() + "<"+t.getId()+">";
	}

	public int getSeats() {
		return seats;
	}

	public void setSeats(int seats) {
		this.seats = seats;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	@Override
	public String toString() {
		return "PokerParticipant [seats=" + seats + ", domain=" + domain
				+ ", timingProfile=" + timingProfile + ", timing=" + timing
				+ ", anteLevel=" + anteLevel + ", variant=" + variant + "]";
	}
}