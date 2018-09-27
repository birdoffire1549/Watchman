package com.firebirdcss.watchman;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * 
 * This class makes up the thread task which is responsible for scanning the desired
 * file or directory resource on a periodic basis.
 * 
 * @author Scott Griffis
 *
 */
public class ScannerProcess extends Thread {
	private static final Logger log = LogManager.getLogger(ScannerProcess.class);
	
	private volatile boolean running = false;
	private boolean baselineScanComplete = false;
	
	private final Map<String/*FilePath*/, byte[]/*SHA-Hash*/> scannedFiles = new HashMap<>();
	
	private final List<String/*FilePath*/> changedSinceLastScan = new ArrayList<>();
	private final List<String/*FilePath*/> newSinceLastScan = new ArrayList<>();
	private AtomicInteger totalPathsScanned = new AtomicInteger(0);
	private AtomicInteger totalFilesScanned = new AtomicInteger(0);
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		this.running = true;
		loadPersistedData();
		
		while (running) {
			/* Scan introduction */
			if (this.baselineScanComplete) {
				log.info("Running a new scan...");
			} else {
				log.info("Running a baseline scan...");
			}
			
			resetCounters();
			
			long scanStartMillis = System.currentTimeMillis();
			scanPath(InternalProperties.scanRootPath);
			long scanDurationMillis = System.currentTimeMillis() - scanStartMillis;
			
			/* Scan summary */
			log.info("Scan complete.");
			
			persistData();
			logScanResults(scanDurationMillis);
			
			this.baselineScanComplete = true;
			
			if (InternalProperties.scanNextCycleSleepMillis != -1) {
				log.info("Sleeping for '" + InternalProperties.scanNextCycleSleepMillis + "' milliseconds, until next run cycle...");
				try {
					Thread.sleep(InternalProperties.scanNextCycleSleepMillis);
				} catch (InterruptedException itsOk) {
					// Either shutting down or will just run another scan...
				}
			} else {
				running = false;
			}
		}
	}
	
	/**
	 * PRIVATE METHOD: This method handles the persisting of the scan data to
	 * the desired location on disk.
	 * 
	 */
	private void persistData() {
		try {
			File file = new File(InternalProperties.persistPath);
			File path = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(File.separator)));
			if (!path.exists()) {
				path.mkdirs();
			}
			
			try (ObjectOutputStream pOut = new ObjectOutputStream(new FileOutputStream(file))) {
				pOut.writeObject(this.scannedFiles);
			}
		} catch (Exception e) {
			log.error("Could not persist scan data!!!\n"
					+ "\tCheck to ensure the path for the specified location ('" + InternalProperties.persistPath + "')is writable.\n"
					+ "\tWill try to persist again next run.");
		}
		
	}
	
	/**
	 * PRIVATE METHOD: This method loads the persisted scan results from a previous scan run.
	 * 
	 */
	@SuppressWarnings("unchecked")
	private void loadPersistedData() {
		try {
			File file = new File(InternalProperties.persistPath);
			try (ObjectInputStream pIn = new ObjectInputStream(new FileInputStream(file))) {
				this.scannedFiles.putAll(((Map<String, byte[]>) pIn.readObject()));
			}
			this.baselineScanComplete = true;
		} catch (Exception e) {
			log.warn("Could not load the persisted scan data!!!\n"
					+ "\tCheck to ensure the path for the specified location ('" + InternalProperties.persistPath + "')is writable.\n"
					+ "\tThis scan will be treated as a baseline scan.");
			this.baselineScanComplete = false;
		}
	}
	
	/**
	 * PRIVATE METHOD: It is the job of this method to log the results of the scan.
	 * 
	 * @param scanDurationMillis - The duration in milliseconds of the scan that just ran as <code>long</code>.
	 */
	private void logScanResults(long scanDurationMillis) {
		log.info("\nScan results:\n"
				+ "\tScan duration millis: ........ '" + scanDurationMillis + "'\n"
				+ "\tDirectories scanned: ......... '" + this.totalPathsScanned.get() + "'\n"
				+ "\tFiles scanned: ............... '" + this.totalFilesScanned.get() + "'\n"
				+ "\tNew files: ................... '" + this.newSinceLastScan.size() + "'\n"
				+ "\tFiles changed since last scan: '" + this.changedSinceLastScan.size() + "'\n");
		
		if (this.baselineScanComplete) { // This information is only of value after baseline scan...
			StringBuilder message = new StringBuilder();
			if (this.newSinceLastScan.size() > 0) {
				message.append("\n\nThe following files are new:");
				for (String s : this.newSinceLastScan) {
					message.append("\n\t").append(s);
				}
				message.append('\n');
			}
			
			if (this.changedSinceLastScan.size() > 0) {
				message.append("\n\nThe following files have changed:");
				for (String s : this.changedSinceLastScan) {
					message.append("\n\t").append(s);
				}
				message.append('\n');
			}
			
			if (message.length() > 0) log.info(message);
		}
	}
	
	/**
	 * PRIVATE METHOD: This method resets the value of the variables that 
	 * are considered counters and are used for the storing of, or accumulating 
	 * values for between scans for reporting purposes.
	 * 
	 */
	private void resetCounters() {
		this.totalPathsScanned.set(0);
		this.totalFilesScanned.set(0);
		this.newSinceLastScan.clear();
		this.changedSinceLastScan.clear();
	}
	
	/**
	 * RECURSIVE PRIVATE METHOD: This method scans a path to process the path's contents in 
	 * parallel, however if the path is actually a file and not a directory then the file is 
	 * simply processed. This method uses recursion to process children items. 
	 * 
	 * @param path - The path to either a file or a directory to scan as {@link String}
	 */
	private void scanPath(String path) {
		File dir = new File(path);
		if (dir.exists()) {
			if (dir.isDirectory()) {
				/* Process directory items in parallel */
				/* ********************************************************************************
				 * NOTE:
				 * ********************************************************************************
				 * The sub files and directories are all scanned in parallel. I am not sure at this
				 * point if there is any limit to their possible parallelization or not.
				 * 
				 * If not, then this scan may be very resource intensive.
				 * 
				 * It may be at some point that it is desired to limit the number of scans that can
				 * happen in parallel but that is not my current concern.
				 */
				Arrays.asList(dir.listFiles()).stream().parallel().forEach((file) -> scanPath(file.getPath()));
				this.totalPathsScanned.incrementAndGet();
			} else if (dir.isFile()) {
				/* Process file */
				scanFile(dir.getPath());
				this.totalFilesScanned.incrementAndGet(); // Done with file...
			}
		}
	}
	
	/**
	 * PRIVATE METHOD: It is the job of this method to scan a file given its path.
	 * The file is then scanned and a hash is generated to represent
	 * the current state of the file. The path and the hash are then
	 * sent downstream to the {@link #handleResults(String, byte[])} method
	 * for further processing.
	 * 
	 * Inspiration from:
	 * https://www.mkyong.com/java/java-sha-hashing-example/
	 * 
	 * @param filePath - The path of the file to scan as {@link String}
	 */
	private void scanFile(String filePath) {
		try {
			/* Scan the given file and derive its hash */
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream dis = new DigestInputStream(new FileInputStream(filePath), digest)) {
				while (dis.read() != -1); // empty loop to clear the data
				digest = dis.getMessageDigest();
			}
			
			/* Send results downstream for processing */
			handleResults(filePath, digest.digest());
		} catch (Exception e) {
			log.error("An Exception occurred while processing the file '" + filePath + "': ", e);
		}
	}
	
	/**
	 * This method can be called externally and is used to gracefully 
	 * shutdown this process.
	 * 
	 */
	public void shutdown() {
		this.running = false;
		this.interrupt();
	}
	
	/**
	 * PRIVATE METHOD: It is the job of this method to handle the scan results it is given.
	 * These results consist of a file path and a file hash.
	 * The path and hash are stored for later reference by this method, also
	 * this method determines if the scanned file is new, changed or same from 
	 * scan to scan.
	 * 
	 * @param filePath - The file path of the file that was scanned, as {@link String}
	 * @param hash - The hash of the file as a <code>byte</code> array.
	 */
	private void handleResults(String filePath, byte[] hash) {
		byte[] prevHash = scannedFiles.get(filePath);
		if (prevHash == null) {
			this.newSinceLastScan.add(filePath);
			scannedFiles.put(filePath, hash);
		} else {
			if (!Arrays.equals(prevHash, hash)) {
				if (this.baselineScanComplete) this.changedSinceLastScan.add(filePath);
				scannedFiles.put(filePath, hash);
			}
		}
	}
}
