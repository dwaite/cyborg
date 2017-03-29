package us.alksol.cyborg.implant;

import java.io.IOException;

public interface CborOutput {
	CborOutput writeBoolean(boolean v) throws IOException;
	CborOutput writeNull() throws IOException;
	CborOutput writeUndefined() throws IOException;
	CborOutput writeSimpleValue(int value) throws IOException;
	
	CborOutput writeInteger(int v) throws IOException;
	CborOutput writeLong(long v) throws IOException;

	CborOutput writeBytes(byte[] b) throws IOException;
	CborOutput writeText(String s) throws IOException;
	CborOutput writeBytes(Iterable<byte[]> s) throws IOException;
	CborOutput writeTextStream(Iterable<String> r) throws IOException;
	
	CborOutput writeFloat(float v) throws IOException;
	CborOutput writeDouble(double v) throws IOException;
	CborOutput writeHalfFloat(int v) throws IOException;
	
	CborOutput writeTag(long tag) throws IOException;

	CborOutput writeStartArray(int count) throws IOException;
	CborOutput writeStartIndefiniteArray() throws IOException;
	
	CborOutput writeStartMap(int count) throws IOException;
	CborOutput writeStartIndefiniteMap() throws IOException;
	
	CborOutput writeBreak() throws IOException;
}
