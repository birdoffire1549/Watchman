package com.firebirdcss.watchman;

import java.util.Arrays;
import java.util.Iterator;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * This is the main class of the application and contains the main method which
 * is the start of execution for this application.
 * 
 * @author Scott Griffis
 *
 */
public class ApplicationMain {
	private static final Logger log = LogManager.getLogger(ApplicationMain.class);
	
	private static ScannerProcess scannerProcess = null;
	
	private static volatile boolean running = false;
	
	/**
	 * The application's main entry-point.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Thread.currentThread().setName("watchman");
		
		/* Initialize the application */
		init(args);
		log.info("Application initialized.");
		
		/* The main application loop */
		while(running) {
			/* Maintain/tend the process */
			if (scannerProcess == null || !scannerProcess.isAlive()) {
				log.info("Starting Scanner Process...");
				scannerProcess = new ScannerProcess();
				scannerProcess.start();
				log.info("The scanner process started.");
			}
			
			/* Take a nap */
			try {
				scannerProcess.join(); 
			} catch (InterruptedException probablyShutdown) {
				// Do nothing... 
			}
			
			if (InternalProperties.scanNextCycleSleepMillis == -1) { // Ensures the process isn't restarted...
				running = false;
			}
		}
		
		/* Application is ready to be shutdown */
		scannerProcess.shutdown();
		
		while (scannerProcess.isAlive()) {
			try {
				scannerProcess.join();
			} catch (InterruptedException dontCare) {
				// Don't care, I am gonna wait for this thread forever if need be.
			}
		}
		
		log.info("Shutdown complete.");
	}
	
	/**
	 * PRIVATE METHOD: This method initializes the application.
	 * 
	 */
	private static void init(String[] args) {
		running = true;
		
		/* Process the application's arguments */
		if (args == null || args.length == 0) {
			displayUsage();
		} else {
			Iterator<String> it = Arrays.asList(args).iterator();
			try {
				long setSwitches = 0L;
				while (it.hasNext()) {
					String arg = it.next();
					switch (arg) {
						case "-s":
						case "--scan-path": // Specify scan root path
							InternalProperties.scanRootPath = it.next();
							setSwitches |= 1;
							break;
						case "-i":
						case "--interval": // Specifies the scan interval in millis 
							InternalProperties.scanNextCycleSleepMillis = Long.parseLong(it.next());
							setSwitches |= 1 << 1;
							break;
						case "-l":
						case "--log-path":
							InternalProperties.logPath = it.next();
							System.out.println("The feature for argument '" + arg + "' is not implemented at this time. Output will appear on the console."); // TODO: Implement!!!
							setSwitches |= 1 << 2;
							break;
						case "-p":
						case "--persist-path": 
							InternalProperties.persistPath = it.next();
							setSwitches |= 1 << 3;
							break;
						default:
							displayUsage();
							break;
					}
				}
				
				if ((setSwitches & 1) != 1) { // Yah, it's overly complicated maybe I'll change later.
					displayUsage();
				}
			} catch (Exception e) {
				displayUsage();
			}
		}
		Runtime.getRuntime().addShutdownHook(new ShutdownHook(Thread.currentThread()));
	}
	
	/**
	 * PRIVATE METHOD: This method will display the program's usage syntax to the console
	 * when called and then exits the application.
	 * 
	 */
	private static void displayUsage() {
		String message = ""
				+ "Program usage: watchman [options] ((-s | --scan-path) scanpath)\n"
				+ "    (-s | --scan-path) scanpath  - The path to the file or directory to scan.\n"
				+ "\n"
				+ "    options:\n"
				+ "    (-i | --interval) millis     - Specifies the time to wait between scans in milliseconds,\n"
				+ "                                   if not specified the scan will only run once then stop.\n"
				+ "    (-l | --log-path) path       - Secifies the path to which to log the output of the application,\n"
				+ "                                   if not specified then application's output is displayed to console.\n"
				+ "    (-p | --persist-path) path   - Specifies the path to the file in which to persist scan data. If not\n"
				+ "                                   specified then it is created in the location '" + InternalProperties.DEFAULT_PERSIST_PATH + "'.\n"
				+ "\n"
				+ "                                   NOTE: The persist-path is also where previous scan results are loaded\n"
				+ "                                   from on startup of the application. If location doesn't exist then the\n"
				+ "                                   application must perfrom a new baseline scan and then creates the file\n"
				+ "                                   in the location specified.\n";
		
		System.out.println(message);
		System.exit(0);
	}
	
	/**
	 * PRIVATE CLASS: Just a shutdown-hook for the application.
	 * 
	 * @author Scott Griffis
	 *
	 */
	private static class ShutdownHook extends Thread {
		private final Thread masterThread;
		
		/**
		 * CONSTRUCTOR: This method is used to pass in the master thread
		 * and to initialize the class.
		 * 
		 * @param thread - The master or main thread as {@link Thread}
		 */
		public ShutdownHook(Thread thread) {
			this.setName("ShutdownHook");
			masterThread = thread;
		}
		
		/*
		 * (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			running = false;
			masterThread.interrupt();
			while (masterThread.isAlive()) {
				try {
					masterThread.join();
				} catch (InterruptedException dontCare) {
					// don't care...
				}
			}
		}
	}
}
