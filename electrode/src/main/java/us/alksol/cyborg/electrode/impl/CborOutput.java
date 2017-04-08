package us.alksol.cyborg.electrode.impl;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;

import us.alksol.cyborg.electrode.CborEvent;
import us.alksol.cyborg.electrode.DataEvent;

public class CborOutput implements Runnable {
	private Iterator<CborEvent> source;
	private DataOutput target;
	
	public CborOutput(Iterator<CborEvent> source, DataOutput target) {
		this.source = source;
		this.target = target;
	}
	
	public void run() {
		source.forEachRemaining((event) -> {
			try {
				DataEvent.write(event, target);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}
}
