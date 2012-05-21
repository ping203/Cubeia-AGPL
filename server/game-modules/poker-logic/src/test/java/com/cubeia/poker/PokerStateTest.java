package com.cubeia.poker;

import com.cubeia.poker.action.ActionRequest;
import com.cubeia.poker.adapter.HandEndStatus;
import com.cubeia.poker.adapter.ServerAdapter;
import com.cubeia.poker.model.RatedPlayerHand;
import com.cubeia.poker.player.DefaultPokerPlayer;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.player.SitOutStatus;
import com.cubeia.poker.pot.Pot;
import com.cubeia.poker.pot.PotHolder;
import com.cubeia.poker.pot.PotTransition;
import com.cubeia.poker.rake.RakeInfoContainer;
import com.cubeia.poker.result.HandResult;
import com.cubeia.poker.result.Result;
import com.cubeia.poker.states.ServerAdapterHolder;
import com.cubeia.poker.states.StateChanger;
import com.cubeia.poker.states.WaitingToStartSTM;
import com.cubeia.poker.timing.Periods;
import com.cubeia.poker.timing.TimingFactory;
import com.cubeia.poker.timing.TimingProfile;
import com.cubeia.poker.variant.telesina.Telesina;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.*;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItem;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class PokerStateTest {

    PokerState state;

    @Mock
    PokerSettings settings;

    @Mock
    GameType gameType;

    @Mock
    ServerAdapterHolder serverAdapterHolder;

    @Mock
    PokerContext context;

    @Mock
    StateChanger stateChanger;

    int anteLevel;

    @Before
    public void setup() {
        initMocks(this);
        state = new PokerState();
        anteLevel = 100;
        when(settings.getRakeSettings()).thenReturn(TestUtils.createOnePercentRakeSettings());
        when(settings.getAnteLevel()).thenReturn(anteLevel);
        when(settings.getTiming()).thenReturn(TimingFactory.getRegistry().getDefaultTimingProfile());
        when(gameType.canPlayerAffordEntryBet(Mockito.any(PokerPlayer.class), Mockito.any(PokerSettings.class), Mockito.eq(false))).thenReturn(true);
        state.serverAdapter = mock(ServerAdapter.class);
        state.init(gameType, settings);
        state.pokerContext.settings = settings;
    }

    @Test
    public void testNotifyHandFinishedPendingBalanceTooHigh() {
        TimingProfile timingProfile = mock(TimingProfile.class);
        when(settings.getTiming()).thenReturn(timingProfile);
        when(settings.getMaxBuyIn()).thenReturn(100);

        DefaultPokerPlayer player1 = new DefaultPokerPlayer(1);
        player1.setBalance(40L);
        player1.addNotInHandAmount(90L);

        DefaultPokerPlayer player2 = new DefaultPokerPlayer(2);
        player2.setBalance(220L);
        player2.addNotInHandAmount(120L);

        state.pokerContext.playerMap.put(player1.getId(), player1);
        state.pokerContext.playerMap.put(player2.getId(), player2);

        state.commitPendingBalances();

        assertThat(player1.getBalance(), is(100L));
        assertThat(player1.getBalanceNotInHand(), is(30L));

        assertThat(player2.getBalance(), is(220L));
        assertThat(player2.getBalanceNotInHand(), is(120L));

    }

    @Test
    public void testCommitPendingBalances() {
        PokerState state = new PokerState();
        PokerSettings settings = mock(PokerSettings.class);
        when(settings.getMaxBuyIn()).thenReturn(10000);

        state.init(gameType, settings);

        PokerPlayer player1 = Mockito.mock(PokerPlayer.class);
        PokerPlayer player2 = Mockito.mock(PokerPlayer.class);
        Map<Integer, PokerPlayer> playerMap = new HashMap<Integer, PokerPlayer>();
        playerMap.put(0, player1);
        playerMap.put(1, player2);
        state.pokerContext.playerMap = playerMap;

        state.commitPendingBalances();

        // Verify interaction and max buyin level
        verify(player1).commitBalanceNotInHand(10000);
        verify(player2).commitBalanceNotInHand(10000);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNotifyPotUpdated() {
        PokerState state = new PokerState();

        state.pokerContext.currentHandPlayerMap = new HashMap<Integer, PokerPlayer>();
        PokerPlayer player0 = mock(PokerPlayer.class);
        when(player0.getId()).thenReturn(1337);
        PokerPlayer player1 = mock(PokerPlayer.class);
        when(player1.getId()).thenReturn(1338);
        PokerPlayer player2 = mock(PokerPlayer.class);
        when(player2.getId()).thenReturn(1339);

        state.pokerContext.getCurrentHandPlayerMap().put(player0.getId(), player0);
        state.pokerContext.getCurrentHandPlayerMap().put(player1.getId(), player1);
        state.pokerContext.getCurrentHandPlayerMap().put(player2.getId(), player2);

        state.pokerContext.potHolder = mock(PotHolder.class);
        state.serverAdapter = mock(ServerAdapter.class);

        Collection<Pot> pots = new ArrayList<Pot>();
        when(state.pokerContext.getPotHolder().getPots()).thenReturn(pots);
        long totalPot = 3434L;
        when(state.pokerContext.getPotHolder().getTotalPotSize()).thenReturn(totalPot);
        BigDecimal totalRake = new BigDecimal("4444");

        when(state.pokerContext.getPotHolder().calculateRake()).thenReturn(new RakeInfoContainer((int) totalPot, totalRake.intValue(), null));
        RakeInfoContainer rakeInfoContainer = mock(RakeInfoContainer.class);

        when(state.pokerContext.getPotHolder().calculateRakeIncludingBetStacks(Mockito.anyCollection())).thenReturn(rakeInfoContainer);

        Collection<PotTransition> potTransitions = new ArrayList<PotTransition>();
        state.notifyPotAndRakeUpdates(potTransitions);

        verify(state.serverAdapter).notifyPotUpdates(pots, potTransitions);
        verify(state.serverAdapter).notifyPlayerBalance(player0);
        verify(state.serverAdapter).notifyPlayerBalance(player1);
        verify(state.serverAdapter).notifyPlayerBalance(player2);

        ArgumentCaptor<RakeInfoContainer> rakeInfoCaptor = ArgumentCaptor.forClass(RakeInfoContainer.class);
        verify(state.serverAdapter).notifyRakeInfo(rakeInfoCaptor.capture());
        RakeInfoContainer rakeInfoContainer1 = rakeInfoCaptor.getValue();
        assertThat(rakeInfoContainer1, is(rakeInfoContainer));

    }

    @Test
    public void testGetTotalPotSize() {
        PokerState state = new PokerState();
        state.pokerContext.potHolder = mock(PotHolder.class);

        PokerPlayer player0 = mock(PokerPlayer.class);
        Integer player0id = 13371;
        when(player0.getId()).thenReturn(player0id);
        when(player0.getBetStack()).thenReturn(10L); // Bet

        PokerPlayer player1 = mock(PokerPlayer.class);
        Integer player1id = 13372;
        when(player1.getId()).thenReturn(player1id);
        when(player1.getBetStack()).thenReturn(10L); // Raise

        PokerPlayer player2 = mock(PokerPlayer.class);
        Integer player2id = 13373;
        when(player2.getId()).thenReturn(player2id);
        when(player2.getBetStack()).thenReturn(0L); // Nothing yet

        Collection<Pot> pots = new ArrayList<Pot>();
        when(state.pokerContext.getPotHolder().getPots()).thenReturn(pots);
        long totalPot = 500L; // already betted in earlier betting rounds
        when(state.pokerContext.getPotHolder().getTotalPotSize()).thenReturn(totalPot);

        Map<Integer, PokerPlayer> playerMap = new HashMap<Integer, PokerPlayer>();
        playerMap.put(player0.getId(), player0);
        playerMap.put(player1.getId(), player1);
        playerMap.put(player2.getId(), player2);

        state.pokerContext.currentHandPlayerMap = playerMap;

        assertThat(state.getTotalPotSize(), is(520L));

    }

    @Test
    public void testPotsClearedAtStartOfHand() {
        state.serverAdapter = mock(ServerAdapter.class);
        state.pokerContext.playerMap = new HashMap<Integer, PokerPlayer>();
        RakeSettings rakeSettings = TestUtils.createOnePercentRakeSettings();
        PokerSettings settings = new PokerSettings(0, 0, 0, 0, null, 4, null, rakeSettings, null);
        state.pokerContext.settings = settings;
        PokerPlayer player1 = mock(PokerPlayer.class);
        PokerPlayer player2 = mock(PokerPlayer.class);
        state.pokerContext.playerMap.put(1, player1);
        state.pokerContext.playerMap.put(2, player2);
        when(player1.isSittingOut()).thenReturn(false);
        when(player2.isSittingOut()).thenReturn(false);

        assertThat(state.pokerContext.getPotHolder(), nullValue());
        state.startHand();
        assertThat(state.pokerContext.getPotHolder(), notNullValue());
    }

    @Test
    public void testResetValuesAtStartOfHand() {
        PokerState state = new PokerState();
        PotHolder oldPotHolder = new PotHolder(null);
        state.pokerContext.potHolder = oldPotHolder;
        RakeSettings rakeSettings = TestUtils.createOnePercentRakeSettings();
        PokerSettings settings = new PokerSettings(0, 0, 0, 0, null, 4, null, rakeSettings, null);
        state.pokerContext.settings = settings;

        state.pokerContext.playerMap = new HashMap<Integer, PokerPlayer>();
        PokerPlayer player1 = mock(PokerPlayer.class);
        PokerPlayer player2 = mock(PokerPlayer.class);
        state.pokerContext.playerMap.put(1, player1);
        state.pokerContext.playerMap.put(2, player2);

        state.resetValuesAtStartOfHand();

        verify(player1).resetBeforeNewHand();
        verify(player2).resetBeforeNewHand();
        assertThat(state.pokerContext.getPotHolder(), not(sameInstance(oldPotHolder)));
        verify(gameType).prepareNewHand();
    }

    @Test
    public void testNotifyBalancesAsStartOfHand() {
        PotHolder oldPotHolder = new PotHolder(null);
        state.pokerContext.potHolder = oldPotHolder;
        RakeSettings rakeSettings = TestUtils.createOnePercentRakeSettings();
        PokerSettings settings = new PokerSettings(0, 0, 0, 0, null, 4, null, rakeSettings, null);
        state.pokerContext.settings = settings;

        ServerAdapter serverAdapter = mock(ServerAdapter.class);
        state.serverAdapter = serverAdapter;

        state.pokerContext.playerMap = new HashMap<Integer, PokerPlayer>();
        PokerPlayer player1 = mock(PokerPlayer.class);
        PokerPlayer player2 = mock(PokerPlayer.class);

        int player1Id = 1337;
        int player2Id = 666;

        when(player1.getBalanceNotInHand()).thenReturn(100L);
        when(player2.getBalanceNotInHand()).thenReturn(100L);

        when(player1.getBalance()).thenReturn(10L);
        when(player2.getBalance()).thenReturn(10L);

        when(player1.getId()).thenReturn(player1Id);
        when(player2.getId()).thenReturn(player2Id);

        when(player1.isSittingOut()).thenReturn(false);
        when(player2.isSittingOut()).thenReturn(false);

        state.pokerContext.playerMap.put(player1Id, player1);
        state.pokerContext.playerMap.put(player2Id, player2);

        state.pokerContext.seatingMap.put(0, player1);
        state.pokerContext.seatingMap.put(1, player2);

        state.startHand();

        verify(state.serverAdapter).notifyPlayerBalance(player1);
        verify(state.serverAdapter).notifyPlayerBalance(player2);


    }

    @Test
    public void testNotifyStatusesAsStartOfHand() {
        PotHolder oldPotHolder = new PotHolder(null);
        state.pokerContext.potHolder = oldPotHolder;
        RakeSettings rakeSettings = TestUtils.createOnePercentRakeSettings();
        PokerSettings settings = new PokerSettings(0, 0, 0, 0, null, 4, null, rakeSettings, null);
        state.pokerContext.settings = settings;

        ServerAdapter serverAdapter = mock(ServerAdapter.class);
        state.serverAdapter = serverAdapter;

        state.pokerContext.playerMap = new HashMap<Integer, PokerPlayer>();
        PokerPlayer player1 = mock(PokerPlayer.class);
        PokerPlayer player2 = mock(PokerPlayer.class);

        int player1Id = 1337;
        int player2Id = 666;

        when(player1.getBalanceNotInHand()).thenReturn(100L);
        when(player2.getBalanceNotInHand()).thenReturn(100L);

        when(player1.getBalance()).thenReturn(10L);
        when(player2.getBalance()).thenReturn(10L);

        when(player1.getId()).thenReturn(player1Id);
        when(player2.getId()).thenReturn(player2Id);

        when(player1.isSittingOut()).thenReturn(false);
        when(player2.isSittingOut()).thenReturn(false);

        state.pokerContext.playerMap.put(player1Id, player1);
        state.pokerContext.playerMap.put(player2Id, player2);

        state.pokerContext.seatingMap.put(0, player1);
        state.pokerContext.seatingMap.put(1, player2);

        state.startHand();
    }

    // TODO FIXTESTS
//    @Test
//    public void testBuyInInfoNotSentOnJoinIfPlayerCanBuyin() {
//        PokerState state = new PokerState();
//        state.init(gameType, settings);
//        state.serverAdapter = mock(ServerAdapter.class);
//        state.gameType = mock(Telesina.class);
//
//        PokerPlayer player = mock(PokerPlayer.class);
//        int playerId = 1337;
//        when(player.getId()).thenReturn(playerId);
//
//        when(state.gameType.canPlayerAffordEntryBet(player, settings, true)).thenReturn(true);
//
//        state.addPlayer(player);
//
//        Mockito.verify(state.serverAdapter, never()).notifyBuyInInfo(1337, false);
//    }
//
//    @Test
//    public void shutdown() {
//        PokerState state = new PokerState();
//        state.shutdown();
//        assertThat(state.getCurrentState(), is(PokerState.SHUTDOWN));
//    }
//
//    @Test(expected = UnsupportedOperationException.class)
//    public void illegalToMoveFromShutdownState() {
//        PokerState state = new PokerState();
//        state.setCurrentState(PokerState.SHUTDOWN);
//        state.setCurrentState(PokerState.PLAYING);
//    }
//
//    @SuppressWarnings("unchecked")
//    @Test
//    public void testHandleBuyInRequestWhileGamePlaying() {
//        PokerPlayer player = mock(PokerPlayer.class);
//        state.setCurrentState(PokerState.PLAYING);
//        int amount = 1234;
//
//        state.handleBuyInRequest(player, amount);
//
//        verify(player).addRequestedBuyInAmount(amount);
//        verify(state.serverAdapter, never()).performPendingBuyIns(Mockito.anyCollection());
//    }
//
//    @SuppressWarnings({"unchecked", "rawtypes"})
//    @Test
//    public void testHandleBuyInRequestWhenWaitingToStart() {
//        PokerPlayer player = mock(PokerPlayer.class);
//        state.setCurrentState(PokerState.WAITING_TO_START);
//        int amount = 1234;
//
//        state.handleBuyInRequest(player, amount);
//
//        verify(player).addRequestedBuyInAmount(amount);
//        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
//        verify(state.serverAdapter).performPendingBuyIns(captor.capture());
//        Collection<PokerPlayer> players = captor.getValue();
//        assertThat(players.size(), is(1));
//        assertThat(players, hasItem(player));
//    }
//
//    @Test
//    public void testSetPlayersWithoutMoneyAsSittingOut() {
//        int player1id = 1001;
//        int player2id = 1002;
//        int player3id = 1003;
//        PokerPlayer player1 = mock(PokerPlayer.class);
//        PokerPlayer player2 = mock(PokerPlayer.class);
//        PokerPlayer player3 = mock(PokerPlayer.class);
//        when(player1.getId()).thenReturn(player1id);
//        when(player2.getId()).thenReturn(player2id);
//        when(player3.getId()).thenReturn(player3id);
//
//        when(player1.isSittingOut()).thenReturn(false);
//        when(player2.isSittingOut()).thenReturn(false);
//        when(player3.isSittingOut()).thenReturn(false);
//
//        state.pokerContext.seatingMap = new TreeMap<Integer, PokerPlayer>();
//        state.pokerContext.seatingMap.put(0, player1);
//        state.pokerContext.seatingMap.put(1, player2);
//        state.pokerContext.seatingMap.put(2, player3);
//
//        state.pokerContext.playerMap = new TreeMap<Integer, PokerPlayer>();
//        state.pokerContext.playerMap.put(player1id, player1);
//        state.pokerContext.playerMap.put(player2id, player2);
//        state.pokerContext.playerMap.put(player3id, player3);
//
//        GameType telesina = mock(Telesina.class);
//        state.gameType = telesina;
//
//
//        when(player1.getBalance()).thenReturn(10L);
//        when(player1.getPendingBalanceSum()).thenReturn(10L);
//        when(settings.getAnteLevel()).thenReturn(20);
//
//        when(player3.isBuyInRequestActive()).thenReturn(true);
//
//        when(telesina.canPlayerAffordEntryBet(player1, settings, true)).thenReturn(true);
//        when(telesina.canPlayerAffordEntryBet(player2, settings, true)).thenReturn(false);
//        when(telesina.canPlayerAffordEntryBet(player3, settings, true)).thenReturn(false);
//
//        state.setPlayersWithoutMoneyAsSittingOut();
//
//        verify(player1, never()).setSitOutStatus(Mockito.any(SitOutStatus.class));
//        verify(player2).setSitOutStatus(SitOutStatus.SITTING_OUT);
//        verify(player3).setSitOutStatus(SitOutStatus.SITTING_OUT);
//
//        verify(state.serverAdapter, never()).notifyBuyInInfo(player1id, true);
//        verify(state.serverAdapter, never()).notifyBuyInInfo(player2id, true);
//        verify(state.serverAdapter, never()).notifyBuyInInfo(player3id, true);
//    }
//
//    @Test
//    public void testSendBuyInInfoToPlayersWithoutMoney() {
//        int player1id = 1001;
//        int player2id = 1002;
//        int player3id = 1003;
//        PokerPlayer player1 = mock(PokerPlayer.class);
//        PokerPlayer player2 = mock(PokerPlayer.class);
//        PokerPlayer player3 = mock(PokerPlayer.class);
//        when(player1.getId()).thenReturn(player1id);
//        when(player2.getId()).thenReturn(player2id);
//        when(player3.getId()).thenReturn(player3id);
//
//        when(player1.isSittingOut()).thenReturn(false);
//        when(player2.isSittingOut()).thenReturn(false);
//        when(player3.isSittingOut()).thenReturn(false);
//
//        state.pokerContext.seatingMap = new TreeMap<Integer, PokerPlayer>();
//        state.pokerContext.seatingMap.put(0, player1);
//        state.pokerContext.seatingMap.put(1, player2);
//        state.pokerContext.seatingMap.put(2, player3);
//
//        state.pokerContext.playerMap = new TreeMap<Integer, PokerPlayer>();
//        state.pokerContext.playerMap.put(player1id, player1);
//        state.pokerContext.playerMap.put(player2id, player2);
//        state.pokerContext.playerMap.put(player3id, player3);
//
//        GameType telesina = mock(Telesina.class);
//        state.gameType = telesina;
//
//
//        when(player1.getBalance()).thenReturn(10L);
//        when(player1.getPendingBalanceSum()).thenReturn(10L);
//        when(settings.getAnteLevel()).thenReturn(20);
//
//        when(player3.isBuyInRequestActive()).thenReturn(true);
//
//        when(telesina.canPlayerAffordEntryBet(player1, settings, true)).thenReturn(true);
//        when(telesina.canPlayerAffordEntryBet(player2, settings, true)).thenReturn(false);
//        when(telesina.canPlayerAffordEntryBet(player3, settings, true)).thenReturn(false);
//
//        state.sendBuyinInfoToPlayersWithoutMoney();
//
//        verify(state.serverAdapter, never()).notifyBuyInInfo(player1id, true); // player affords buyin
//        verify(state.serverAdapter).notifyBuyInInfo(player2id, true);
//        verify(state.serverAdapter, never()).notifyBuyInInfo(player3id, true); // player has pending buyin that will cover it
//    }

}
