package com.firebirdcss.watchman;

/**
 * This class acts as the repository of all of the application's properties
 * and constants which may be used throughout the application.
 * 
 * @author Scott Griffis
 *
 */
public class InternalProperties {
	/* ********************* *
	 * APPLICATION CONSTANTS *
	 * ********************* */
	public static final String APPLICATION_BASE_PATH = "/opt/watchman/";
	public static final String APPLICATION_DATA_PATH = APPLICATION_BASE_PATH + "data/";
	
	/* ******************** *
	 * APPLICATION DEFAULTS *
	 * ******************** */
	public static final long DEFAULT_SCAN_NEXT_CYCLE_SLEEP_MILLIS = -1L;
	public static final String DEFAULT_SCAN_ROOT_PATH = APPLICATION_BASE_PATH + "demo/";
	public static final String DEFAULT_LOG_PATH = null;
	public static final String DEFAULT_PERSIST_PATH = APPLICATION_DATA_PATH + "previous-scan-data";
	
	/* ********************** *
	 * APPLICATION PROPERTIES *
	 * ********************** */
	public static long scanNextCycleSleepMillis = DEFAULT_SCAN_NEXT_CYCLE_SLEEP_MILLIS;
	public static String scanRootPath = DEFAULT_SCAN_ROOT_PATH;
	public static String logPath = DEFAULT_LOG_PATH;
	public static String persistPath = DEFAULT_PERSIST_PATH;
}
