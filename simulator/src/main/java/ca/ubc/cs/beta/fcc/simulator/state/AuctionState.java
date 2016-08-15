package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.Prices;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Created by newmanne on 2016-08-02.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuctionState {

    private Prices benchmarkPrices;
    private ParticipationRecord participation;
    private int round;
    private Map<Integer, Integer> assignment;
    private IModifiableLadder ladder;

    private Double UHFToOffBenchmark;

}
