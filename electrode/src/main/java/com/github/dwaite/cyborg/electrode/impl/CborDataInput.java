package com.github.dwaite.cyborg.electrode.impl;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;

import com.github.dwaite.cyborg.electrode.CborEvent;
import com.github.dwaite.cyborg.electrode.CborParser;
import com.github.dwaite.cyborg.electrode.DataEvent;

/** Adapter of a data input source to implement CborParser */
public class CborDataInput implements CborParser {
	
	private DataInput source;
	private CborEvent next;
	
	public CborDataInput(DataInputStream input) {
		source = input;
		next = null;
	}

	public CborDataInput(DataInput input) {
		source = input;
		next = null;
	}

	
	@Override
	public boolean hasNext() {
		if (source == null) {
			return false;
		}
		if (next != null) {
			return true;
		}

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
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		CborEvent event = next;
		if (event == null) {
			throw new NoSuchElementException();
		}
		next = null;
		return event;
	}

	@Override
	public CborEvent peek() throws IOException, NoSuchElementException {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		return next;
	}
}
