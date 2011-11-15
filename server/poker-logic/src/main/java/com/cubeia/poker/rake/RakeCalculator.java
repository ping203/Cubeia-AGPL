package com.cubeia.poker.rake;

import java.io.Serializable;
import java.util.Collection;

import com.cubeia.poker.pot.Pot;

public interface RakeCalculator extends Serializable {
    
    /**
     * Calculates the rakes for the given pots.
     * @param pots Pots to calculate rake for. This should be all pots in the current hand.
     * @param firstCallHasBeenMade set to true if the hand has seen a call, false if no one has called yet
     * @return the calculated rakes per pot, total rake and total bets (pot sizes)
     */
    RakeInfoContainer calculateRakes(Collection<Pot> pots, boolean firstCallHasBeenMade);
    
    
}