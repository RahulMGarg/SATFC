package ca.ubc.cs.beta.stationpacking.execution.daemon;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.options.UsageSection;
import ca.ubc.cs.beta.aclib.options.ConfigToLaTeX;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.stationpacking.daemon.datamanager.solver.SolverManager;
import ca.ubc.cs.beta.stationpacking.daemon.server.threadedserver.listener.ServerListener;
import ca.ubc.cs.beta.stationpacking.daemon.server.threadedserver.responder.ServerResponder;
import ca.ubc.cs.beta.stationpacking.daemon.server.threadedserver.responder.ServerResponse;
import ca.ubc.cs.beta.stationpacking.daemon.server.threadedserver.solver.ServerSolver;
import ca.ubc.cs.beta.stationpacking.daemon.server.threadedserver.solver.ServerSolverInterrupter;
import ca.ubc.cs.beta.stationpacking.daemon.server.threadedserver.solver.SolvingJob;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.daemon.ThreadedSolverServerParameters;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class ThreadedSolverServerExecutor {

	private static Logger log = LoggerFactory.getLogger(ThreadedSolverServerExecutor.class);
	
	
	private final static AtomicInteger TERMINATION_STATUS = new AtomicInteger(0);
	
	private final static UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER;
		
	static
	{
		/*
		 * Statically define the uncaught exception handler.
		 */

		//Any uncaught exception should terminate current process.
		UNCAUGHT_EXCEPTION_HANDLER = new UncaughtExceptionHandler() 
		{
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				
				e.printStackTrace();
				
				log.error("Thread {} died with an exception ({}).",t.getName(),e.getMessage());
				
				log.error("Stopping service :( .");
				EXECUTOR_SERVICE.shutdownNow();
				
				TERMINATION_STATUS.set(1);
				
			}
		};
	}
	
	private final static ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
	
	public static void main(String[] args) {
		//Parse the command line arguments in a parameter object.
		ThreadedSolverServerParameters aParameters = new ThreadedSolverServerParameters();
		JCommander aParameterParser = JCommanderHelper.getJCommander(aParameters, TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators());
		try
		{
			aParameterParser.parse(args);
		}
		catch (ParameterException aParameterException)
		{
			List<UsageSection> sections = ConfigToLaTeX.getParameters(aParameters, TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators());
			
			boolean showHiddenParameters = false;
			
			//A much nicer usage screen than JCommander's 
			ConfigToLaTeX.usage(sections, showHiddenParameters);
			
			log.error(aParameterException.getMessage());
			return;
		}
		
		//Setup the solver manager.
		SolverManager aSolverManager = aParameters.getSolverManager();
		
		//Setup server socket.
		int aServerPort = aParameters.Port;
		
		DatagramSocket aServerSocket;
		try {
			aServerSocket = new DatagramSocket(aServerPort, InetAddress.getByName("localhost"));
		} catch (SocketException e) {
			throw new IllegalArgumentException("Could not create server socket ("+e.getMessage()+").");
		} catch (UnknownHostException e1) {
			throw new IllegalStateException("Cannot create socket at local host address ("+e1.getMessage()+").");
		}
		
		//Setup queues and solver state.
		BlockingQueue<SolvingJob> aSolvingJobQueue = new LinkedBlockingQueue<SolvingJob>();
		ServerSolverInterrupter aSolverState = new ServerSolverInterrupter();
		
		BlockingQueue<ServerResponse> aServerResponseQueue = new LinkedBlockingQueue<ServerResponse>();
		
				
		//Setup server runnables.
		ServerListener aServerListener = new ServerListener(aSolvingJobQueue, aSolverState, aServerResponseQueue, aServerSocket);
		ServerResponder aServerResponder = new ServerResponder(aServerResponseQueue, aServerSocket);
		ServerSolver aServerSolver = new ServerSolver(aSolverManager, aSolverState, aSolvingJobQueue, aServerResponseQueue);
		
		
		
		//Submit and start producers and consumers.
		
		submitRunnable(aServerListener);
		submitRunnable(aServerResponder);
		submitRunnable(aServerSolver);
		
		try {
			EXECUTOR_SERVICE.awaitTermination(365*10, TimeUnit.DAYS);
		} catch (InterruptedException e1) {
			log.error("We are really amazed that we're seeing this right now",e1);
			return;
		}
	
		System.exit(TERMINATION_STATUS.get());
		
	}
	
	/**
	 * Wrapper method around runnables to make executor service catch uncaught exceptions.
	 * @param r - a runnable.
	 */
	private static void submitRunnable(final Runnable r)
	{
		EXECUTOR_SERVICE.submit(
				new Runnable() {
					@Override
					public void run() {
						// TODO Auto-generated method stub
						try {
							r.run();
						}  catch(Throwable t)
						{
							UNCAUGHT_EXCEPTION_HANDLER.uncaughtException(Thread.currentThread(), t);
						}
					}
							
				});
	}
}