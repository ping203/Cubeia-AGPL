package com.cubeia.games.poker.handler;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import se.jadestone.dicearena.game.poker.network.protocol.BuyInResponse;
import se.jadestone.dicearena.game.poker.network.protocol.Enums;
import se.jadestone.dicearena.game.poker.network.protocol.ProtocolObjectFactory;

import com.cubeia.backend.cashgame.PlayerSessionId;
import com.cubeia.backend.cashgame.PlayerSessionIdImpl;
import com.cubeia.backend.cashgame.TableId;
import com.cubeia.backend.cashgame.dto.AnnounceTableResponse;
import com.cubeia.backend.cashgame.dto.BalanceUpdate;
import com.cubeia.backend.cashgame.dto.OpenSessionResponse;
import com.cubeia.backend.cashgame.dto.ReserveResponse;
import com.cubeia.backend.firebase.CashGamesBackendContract;
import com.cubeia.backend.firebase.FirebaseCallbackFactory;
import com.cubeia.firebase.api.action.GameDataAction;
import com.cubeia.firebase.api.game.GameNotifier;
import com.cubeia.firebase.api.game.lobby.LobbyTableAttributeAccessor;
import com.cubeia.firebase.api.game.table.Table;
import com.cubeia.firebase.io.StyxSerializer;
import com.cubeia.games.poker.model.PokerPlayerImpl;
import com.cubeia.poker.PokerState;

public class BackendCallHandlerTest {

    @Mock private PokerState state;
    @Mock private Table table;
    @Mock private GameNotifier notifier;
    @Mock private PokerPlayerImpl pokerPlayer;
    @Mock private CashGamesBackendContract backend;
    @Mock private FirebaseCallbackFactory callbackFactory;
    private BackendCallHandler callHandler;
    private int playerId = 1337;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        callHandler = new BackendCallHandler(state, table);
        when(table.getNotifier()).thenReturn(notifier);
        when(state.getPokerPlayer(playerId)).thenReturn(pokerPlayer);
        when(backend.getCallbackFactory()).thenReturn(callbackFactory);
    }
    
    
    PlayerSessionId playerSessionId;
    ReserveResponse reserveResponse;
    int amount;
    BuyInResponse buyInRespPacket;
    
    private void setupForHandleReserveSuccessfulResponse(boolean isSitInAfterBuyIn) throws IOException{
    	amount = 500;
        playerSessionId = new PlayerSessionIdImpl(playerId);
        int balanceOnRemoteWallet = 10000;
        BalanceUpdate balanceUpdate = new BalanceUpdate(playerSessionId , balanceOnRemoteWallet, -1);
        reserveResponse = new ReserveResponse(balanceUpdate , amount);
        
		when(pokerPlayer.getPendingBalance()).thenReturn((long)amount);
        when(pokerPlayer.isSitInAfterSuccessfulBuyIn()).thenReturn(isSitInAfterBuyIn);
        callHandler.handleReserveSuccessfulResponse(reserveResponse);
        verify(pokerPlayer).addPendingAmount(amount);
        
        ArgumentCaptor<GameDataAction> buyInResponseCaptor = ArgumentCaptor.forClass(GameDataAction.class);
        verify(notifier).notifyPlayer(Mockito.eq(playerId), buyInResponseCaptor.capture());
        GameDataAction buyInDataAction = buyInResponseCaptor.getValue();
        buyInRespPacket = (BuyInResponse) new StyxSerializer(new ProtocolObjectFactory()).unpack(buyInDataAction.getData());
        
        assertThat(buyInRespPacket.balance, is(0));
        assertThat(buyInRespPacket.pendingBalance, is(amount));
        assertThat(buyInRespPacket.resultCode, is(Enums.BuyInResultCode.OK));
        verify(state).notifyPlayerBalance(playerId);
    }
    

    
    @Test
    public void testHandleReserveSuccessfulResponse() throws IOException {
    	setupForHandleReserveSuccessfulResponse(false);
        verify(state, never()).playerIsSittingIn(playerId);
    }
    
    @Test
    public void testHandleReserveSuccessfulResponseSitInIfSuccessful() throws IOException {
    	setupForHandleReserveSuccessfulResponse(true);
        verify(state).playerIsSittingIn(playerId);
    }

    @Test
    public void testHandleOpenSessionSuccessfulResponse() {
        PlayerSessionId playerSessionId = new PlayerSessionIdImpl(playerId);
        OpenSessionResponse openSessionResponse = new OpenSessionResponse(playerSessionId, Collections.<String, String>emptyMap());
        callHandler.handleOpenSessionSuccessfulResponse(openSessionResponse);
        verify(pokerPlayer).setPlayerSessionId(playerSessionId);
    }
    
    @SuppressWarnings({ "serial", "unchecked" })
    @Test
    public void testHandleAnnounceTableSuccessfulResponse() {
        Map<String, Serializable> extProps = Mockito.mock(Map.class);
        when(state.getExternalTableProperties()).thenReturn(extProps);
        LobbyTableAttributeAccessor attributeAccessor = mock(LobbyTableAttributeAccessor.class);
        when(table.getAttributeAccessor()).thenReturn(attributeAccessor );
        
        TableId tableId = new TableId() {};
        AnnounceTableResponse announceTableResponse = new AnnounceTableResponse(tableId);
        announceTableResponse.setProperty("test", "klyka");
        callHandler.handleAnnounceTableSuccessfulResponse(announceTableResponse);
        verify(extProps).put("tableId", tableId);
        verify(extProps).putAll(announceTableResponse.tableProperties);
        verify(attributeAccessor).setIntAttribute("VISIBLE_IN_LOBBY", 1);
    }
    
}