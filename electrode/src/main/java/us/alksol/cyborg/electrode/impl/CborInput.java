package us.alksol.cyborg.electrode.impl;

import java.io.DataInput;
import java.util.Iterator;
import java.util.NoSuchElementException;

import us.alksol.cyborg.electrode.CborEvent;
import us.alksol.cyborg.electrode.CborParser;
import us.alksol.cyborg.electrode.DataEvent;

public class CborInput implements CborParser {
	
	private DataInput source;
	private CborEvent next;
	
	public CborInput(DataInput input) {
		source = input;
		next = null;
	}
	
	@Override
	public boolean hasNext() {
		try {
			if (next == null) {
				next = DataEvent.fromDataInput(source);
			}
			return next != null;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public CborEvent next() {
		CborEvent event = next;
		if (event == null) {
			throw new NoSuchElementException();
		}
		next = null;
		return event;
	}

	@Override
	public void close() throws Exception {
	}
}
