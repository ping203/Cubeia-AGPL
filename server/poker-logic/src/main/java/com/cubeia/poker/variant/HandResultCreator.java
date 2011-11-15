package com.cubeia.poker.variant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.cubeia.poker.hand.Card;
import com.cubeia.poker.hand.Hand;
import com.cubeia.poker.hand.HandInfo;
import com.cubeia.poker.hand.HandTypeEvaluator;
import com.cubeia.poker.model.PlayerHand;
import com.cubeia.poker.model.RatedPlayerHand;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.pot.Pot;
import com.cubeia.poker.pot.PotHolder;
import com.cubeia.poker.pot.PotTransition;
import com.cubeia.poker.result.HandResult;
import com.cubeia.poker.result.Result;
import com.cubeia.poker.util.HandResultCalculator;
import com.google.common.annotations.VisibleForTesting;

public class HandResultCreator {

	private HandTypeEvaluator hte;

	public HandResultCreator(HandTypeEvaluator hte) {
		this.hte = hte;
	}

	public HandResult createHandResult(List<Card> communityCards, HandResultCalculator handResultCalculator, PotHolder potHolder, 
			Map<Integer, PokerPlayer> currentHandPlayerMap, List<Integer> playerRevealOrder, Set<PokerPlayer> muckingPlayers) {
		
		
		List<PlayerHand> playerHands = createHandsList(communityCards, currentHandPlayerMap.values());
		Map<PokerPlayer, Result> playerResults = handResultCalculator.getPlayerResults(playerHands, potHolder, currentHandPlayerMap);
		Collection<PotTransition> potTransitions = createPotTransitionsByResults(playerResults);
				
		playerHands = filterOutMuckedPlayerHands(playerHands, muckingPlayers);
		
		return new HandResult(playerResults, rateHands(playerHands), potTransitions, potHolder.calculateRake(), playerRevealOrder);
	}
	
	protected List<PlayerHand> filterOutMuckedPlayerHands(List<PlayerHand> playerHands, Set<PokerPlayer> muckingPlayers){
		List<PlayerHand> filteredList = new ArrayList<PlayerHand>();
		for (PlayerHand playerHand : playerHands) {
			boolean shouldShowHand = true;
			for (PokerPlayer player : muckingPlayers) {
				if(playerHand.getPlayerId() == player.getId()){
					shouldShowHand = false;
					break;
				}
			}
			if(shouldShowHand){
				filteredList.add(playerHand);
			}
		}

		return filteredList;
	}
	
	@VisibleForTesting
    protected Collection<PotTransition> createPotTransitionsByResults(Map<PokerPlayer, Result> playerResults) {
        Collection<PotTransition> potTransitions = new ArrayList<PotTransition>();
		for (Entry<PokerPlayer, Result> entry : playerResults.entrySet()) {
		    PokerPlayer player = entry.getKey();
		    for (Entry<Pot, Long> potShare :  entry.getValue().getWinningsByPot().entrySet()) {
		        potTransitions.add(new PotTransition(player, potShare.getKey(), potShare.getValue()));
		    }
		}
        return potTransitions;
    }

	private List<PlayerHand> createHandsList(List<Card> communityCards, Collection<PokerPlayer> players) {
		ArrayList<PlayerHand> playerHands = new ArrayList<PlayerHand>();

		for (PokerPlayer player : players) {
			if (!player.hasFolded()) {
				Hand h = new Hand();
				h.addCards(player.getPocketCards().getCards());
				h.addCards(communityCards);
				playerHands.add(new PlayerHand(player.getId(), h));
			}
		}

		return playerHands;
	}

	private List<RatedPlayerHand> rateHands(List<PlayerHand> hands) {
		List<RatedPlayerHand> result = new LinkedList<RatedPlayerHand>();
		
		for (PlayerHand hand : hands) {
			HandInfo bestHandInfo = hte.getBestHandInfo(hand.getHand());
			result.add(new RatedPlayerHand(hand, bestHandInfo.getHandType(), bestHandInfo.getCards()));
		}
		
		return result;
	}
}