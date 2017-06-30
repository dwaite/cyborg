package com.github.dwaite.cyborg.electrode;

import java.io.IOException;

import com.github.dwaite.bytestring.Bytes;

public interface CborGenerator {

	CborGenerator next(CborEvent event) throws IOException;
	
	default CborGenerator writeBoolean(boolean v) throws IOException {
		return next(DataEvent.ofBoolean(v));
	}
	
	default CborGenerator writeNull() throws IOException {
		return next(DataEvent.ofNull());
	}
	
	default CborGenerator writeUndefined() throws IOException {
		return next(DataEvent.ofUndefined());
	}

	default CborGenerator writeSimpleValue(int value) throws IOException {
		return next(DataEvent.ofSimpleValue(value));
	}
	
	default CborGenerator writeInteger(int v) throws IOException {
		return next(DataEvent.ofLong(v));
	}
	
	default CborGenerator writeLong(long v) throws IOException {
		return next(DataEvent.ofLong(v));
	}

	default CborGenerator writeUnsignedLong(long value) throws IOException {
		return next(DataEvent.ofUnsignedLong(value));
	}
	
	default CborGenerator writeNegativeUnsignedLong(long value) throws IOException {
		return next(DataEvent.ofNegativeUnsignedLong(value));		
	}

	default CborGenerator writeBytes(byte[] b) throws IOException {
		return next(DataEvent.ofBytes(b));
	}
	
	default CborGenerator writeBytes(Bytes b) throws IOException {
		return next(DataEvent.ofBytes(b));
	}

	default CborGenerator writeBytes(Iterable<Bytes> s) throws IOException {
		next(DataEvent.startIndefiniteByteArray());
		for (Bytes chunk : s) {
			next(DataEvent.ofBytes(chunk));
		}
		return next(DataEvent.ofBreak());
	}
	default CborGenerator writeText(String s) throws IOException {
		return next(DataEvent.ofText(s));
	}
	
	default CborGenerator writeTextStream(Iterable<String> r) throws IOException {
		next(DataEvent.startIndefiniteTextArray());
		for (String chunk : r) {
			next(DataEvent.ofText(chunk));
		}
		return next(DataEvent.ofBreak());
	}
	
	default CborGenerator writeFloat(float v) throws IOException {
		return next(DataEvent.ofFloat(v));
	}
	default CborGenerator writeDouble(double v) throws IOException {
		return next(DataEvent.ofDouble(v));

	}
	default CborGenerator writeHalfFloat(short v) throws IOException {
		return next(DataEvent.ofHalfFloat(v));

	}
	
	default CborGenerator writeStartTag(long tag) throws IOException {
		return next(DataEvent.ofTag(tag));
	}

	default CborGenerator writeStartArray(int count) throws IOException {
		return next(DataEvent.startArray(count));
	}
	
	default CborGenerator writeStartIndefiniteArray() throws IOException {
		return next(DataEvent.startIndefiniteArray());
	}
	
	default CborGenerator writeStartMap(int keyValuePairs) throws IOException {
		return next(DataEvent.startMap(keyValuePairs));
	}
	
	default CborGenerator writeStartIndefiniteMap() throws IOException {
		return next(DataEvent.startIndefiniteMap());

	}
	
	default CborGenerator writeBreak() throws IOException {
		return next(DataEvent.ofBreak());
	}
	
}