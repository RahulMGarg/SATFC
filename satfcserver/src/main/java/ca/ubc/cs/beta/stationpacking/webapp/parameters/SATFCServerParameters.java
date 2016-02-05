package ca.ubc.cs.beta.stationpacking.webapp.parameters;

import java.io.File;

import lombok.Getter;
import lombok.ToString;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;

/**
* Created by newmanne on 30/06/15.
*/
@ToString
@Parameters(separators = "=")
@UsageTextField(title="SATFCServer Parameters",description="Parameters needed to build SATFCServer")
public class SATFCServerParameters extends AbstractOptions {

    @Parameter(names = "--redis.host", description = "host for redis")
    @Getter
    private String redisURL =  "localhost";

    @Parameter(names = "--redis.port", description = "port for redis")
    @Getter
    private int redisPort = 6379;

    @Parameter(names = "--constraint.folder", description = "Folder containing all of the station configuration folders", required = true)
    @Getter
    private String constraintFolder;

    @Parameter(names = "--seed", description = "Random seed")
    @Getter
    private long seed = 1;

    @Parameter(names = "--cache.permutations", description = "The number of permutations for the containment cache to use. Higher numbers yield better performance with large caches, but are more memory expensive")
    @Getter
    private int numPermutations = 1;

    @Parameter(names = "--accept.regex", description = "Only accept cache entries that match this regex", hidden = true)
    @Getter
    private String acceptRegex = null;

    @Parameter(names = "--cache.size.limit", description = "Only use the first limit entries from the cache", hidden = true)
    @Getter
    private long cacheSizeLimit = Long.MAX_VALUE;

    @Parameter(names = "--skipSAT", description = "Do not load SAT entries from redis")
    @Getter
    private boolean skipSAT = false;

    @Parameter(names = "--skipUNSAT", description = "Do not load UNSAT entries from redis")
    @Getter
    private boolean skipUNSAT = false;

    @Parameter(names = "--excludeSameAuction", description = "Do not count a solution if it is derived from the same auction as the problem", hidden = true)
    @Getter
    private boolean excludeSameAuction = false;

    @Parameter(names = "--cache.screener", description = "Determine what goes into the cache", hidden = true)
    @Getter
    private CACHE_SCREENER_CHOICE cacheScreenerChoice = CACHE_SCREENER_CHOICE.NEW_INFO;

    public enum CACHE_SCREENER_CHOICE {
        NEW_INFO, ADD_EVERYTHING, ADD_NOTHING
    }

    public void validate() {
        Preconditions.checkArgument(new File(constraintFolder).isDirectory(), "Provided constraint folder is not a directory", constraintFolder);
    }

}
