package se.rupy.http;

import java.io.IOException;

/**
 * The failure, if thrown, does not display the error to the user but
 * disconnects the client. Useful if you receive hack attempts or similar
 * unwanted requests. Also used internally to jump the 500 Internal Server Error
 * trap.
 */
public class Failure extends IOException {
	public Failure(String message) {
		super(message);
	}

	protected Failure(Helper helper) {
		super(helper.getRoot().getMessage());
	}

	protected static void chain(Throwable t) throws Failure {
		throw (Failure) new Failure(new Failure.Helper(t)).initCause(t);
	}

	protected static void chain(String message, Throwable t) throws Failure {
		throw (Failure) new Failure(message).initCause(t);
	}

	static class Helper {
		Throwable root;

		protected  Helper(Throwable t) {
			while (t.getCause() != null) {
				t = t.getCause();
			}

			root = t;
		}

		protected Throwable getRoot() {
			return root;
		}
	}
	
	/**
	 * To close the event without logging to error.txt.
	 */
	static class Close extends IOException {
		public Close() {}
		public Close(String message) {
			super(message);
		}
	}

	/**
	 * When a client is not purging the TCP pipeline,
     * either because it is lagging/buffering (old
     * modems or satellite f.ex.) the NIO Async.
     * pipeline can't wait because we have fever
     * shared threads to avoid context switching
     * choke. 206 f.ex. should not wait or warn
     * about this!
	 */
	static class Timeout extends IOException {
        public boolean log; // Don't log if Partial Content!
		public Timeout() {}
		public Timeout(String message, boolean log) {
			super(message); this.log = log;
		}
	}
}
