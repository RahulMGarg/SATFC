package ca.ubc.cs.beta.stationpacking.legacy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.stationpacking.data.Station;
import ca.ubc.cs.beta.stationpacking.experiment.instance.IInstance;
import ca.ubc.cs.beta.stationpacking.experiment.instanceencoder.cnflookup.ICNFLookup;
import ca.ubc.cs.beta.stationpacking.experiment.solver.result.SATResult;

/**
 * CNF lookup that searches a particular directory for CNFs.
 * @author afrechet
 *
 */
public class DirCNFLookup implements ICNFLookup{

	private static Logger log = LoggerFactory.getLogger(InstanceGeneration.class);
	
	private String fCNFDirectory;

	public DirCNFLookup(String aCNFDirectory)
	{
		fCNFDirectory = aCNFDirectory;
	}
	
	private String hashforFilename(String aString)
	{
		MessageDigest aDigest = DigestUtils.getSha1Digest();
		try {
			byte[] aResult = aDigest.digest(aString.getBytes("UTF-8"));
		    String aResultString = new String(Hex.encodeHex(aResult));	
		    return aResultString;
		}
		catch (UnsupportedEncodingException e) {
		    throw new IllegalStateException("Could not encode filename", e);
		}
	}
	
	public boolean hasCNFfor(IInstance aInstance) {
		String aCNFFilename = fCNFDirectory+File.separatorChar+getCNFNamefor(aInstance);
		File aCNFFile = new File(aCNFFilename);
		return aCNFFile.exists();
	}

	public String getCNFfor(IInstance aInstance) {
		String aCNFName = getCNFNamefor(aInstance);
		String aCNFFilename = fCNFDirectory+File.separatorChar+getCNFNamefor(aInstance);
		File aCNFFile = new File(aCNFFilename);
		if(!aCNFFile.exists()) {
			try{
				throw new IllegalStateException("Tried to lookup a CNF for a set of stations that is unavailable.");
			} catch(Exception e){
				e.printStackTrace();
			}
		}
		return aCNFName;
	}

	//NA - modified to include channel range
	private String getCNFNamefor(IInstance aInstance) {
		return hashforFilename(Station.hashStationSet(aInstance.getStations()))+aInstance.getChannelRange().hashCode()+".cnf";
	}
	
	
	public String addCNFfor(IInstance aInstance){
		log.warn("Not assigning station set to any CNF - CNF must be put with the right name in the right directory.");
		return getCNFNamefor(aInstance);
	}

	@Override
	public boolean hasSATResult(IInstance aInstance) {
		try{
			throw new UnsupportedOperationException("Method hasSATResult is not implemented in class DirCNFLookup.");
		} catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public SATResult getSATResult(IInstance aInstance) {
		try{
			throw new UnsupportedOperationException("Method getSATResult is not implemented in class DirCNFLookup.");
		} catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean putSATResult(IInstance aInstance, SATResult aResult) {
		try{
			throw new UnsupportedOperationException("Method putSATResult is not implemented in class DirCNFLookup.");
		} catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public String getNameFor(IInstance aInstance) {
		try{
			throw new UnsupportedOperationException("Method getNameFor is not implemented in class DirCNFLookup.");
		} catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void writeToFile() throws IOException {
		try{
			throw new UnsupportedOperationException("Method writeToFile is not implemented in class DirCNFLookup.");
		} catch(Exception e){
			e.printStackTrace();
		}		
	}

}