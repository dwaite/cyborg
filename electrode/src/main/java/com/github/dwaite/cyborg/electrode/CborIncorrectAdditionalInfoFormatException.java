package com.github.dwaite.cyborg.electrode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.github.dwaite.cyborg.electrode.InitialByte.InfoFormat;

public class CborIncorrectAdditionalInfoFormatException extends CborException {
	private static final long serialVersionUID = 1L;

	private final List<InfoFormat> expected;
	private final InitialByte header;

	public CborIncorrectAdditionalInfoFormatException(InitialByte header, InfoFormat expected) {
		super("Received header " + header + ", expected additional info format " + expected);
		this.header = header;
		this.expected = Collections.singletonList(expected);
	}

	public CborIncorrectAdditionalInfoFormatException(InitialByte header, InfoFormat... expected) {
		super("Received header " + header + ", expected one of additional info formats " + Arrays.toString(expected));
		this.header = header;
		this.expected = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(expected)));
	}

	public List<InfoFormat> getExpected() {
		return expected;
	}

	public InfoFormat getActual() {
		return header.getAdditionalInfoFormat();
	}
	
	public InitialByte getHeader() {
		return header;
	}

}
