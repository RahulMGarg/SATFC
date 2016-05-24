package ca.ubc.cs.beta.stationpacking.execution;

import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Slf4j
public class SimulatorProblemReader extends AProblemReader {

    private final Jedis jedis;
    private final String queueName;
    private SimulatorMessage activeMessage;

    public SimulatorProblemReader(Jedis jedis, String queueName) {
        this.jedis = jedis;
        this.queueName = queueName;
        log.info("Reading instances from queue {}", RedisUtils.makeKey(queueName));
    }

    @Override
    public SATFCFacadeProblem getNextProblem() {
        SATFCFacadeProblem problem = null;
        while (true) {
            String id = jedis.rpoplpush(RedisUtils.makeKey(queueName), RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE));
            if (id == null) {
                // Need to wait for a problem to appear
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            final String problemKey = queueName + ":" + id;
            final String activeProblemString = jedis.get(problemKey);
            Preconditions.checkNotNull(activeProblemString, "Could not find a problem where one should be at %s", problemKey);
            activeMessage = JSONUtils.toObject(activeProblemString, SimulatorMessage.class);

            problem = new SATFCFacadeProblem(
                    null,
                    null,
                    activeMessage.getProblemSpec().getProblem().getDomains(),
                    activeMessage.getProblemSpec().getProblem().getPreviousAssignment(),
                    activeMessage.getProblemSpec().getStationInfoFolder(),
                    Long.toString(activeMessage.getId()),
                    activeMessage.getProblemSpec().getCutoff()
            );
            break;
        }
        return problem;
    }

    @Override
    public void onPostProblem(SATFCFacadeProblem problem, SATFCResult result) {
        super.onPostProblem(problem, result);

        // I can see why this might be a bad idea, but probably it will just work...
        final long numDeleted = jedis.lrem(RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE), 1, Long.toString(activeMessage.getId()));
        if (numDeleted != 1) {
            log.error("Couldn't delete problem {} from the processing queue!", activeMessage.getId());
        }

        // Put the reply back!
        jedis.lpush(activeMessage.getReplyQueue(), JSONUtils.toString(new SATFCSimulatorReply(result, activeMessage.getId())));
    }

    // TODO: actually write proper json constructors for immutability

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SATFCSimulatorReply {

        private SATFCResult result;
        private long id;

    }


    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class SimulatorMessage {

        SATFCProblemSpecification problemSpec;
        String replyQueue;
        long id;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SATFCProblemSpecification {

        private SATFCProblem problem;
        private double cutoff;
        private String stationInfoFolder;
        private long seed;

    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SATFCProblem {

        private Map<Integer, Set<Integer>> domains;
        private Map<Integer, Integer> previousAssignment;

    }

}

