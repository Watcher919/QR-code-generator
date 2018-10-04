/* 
 * QR Code generator library (Java)
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package io.nayuki.qrcodegen;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Represents an immutable square grid of black and white cells for a QR Code symbol, and
 * provides static functions to create a QR Code from user-supplied textual or binary data.
 * <p>This class covers the QR Code Model 2 specification, supporting all versions (sizes)
 * from 1 to 40, all 4 error correction levels, and 4 character encoding modes.</p>
 */
public final class QrCode {
	
	/*---- Static factory functions (high level) ----*/
	
	/**
	 * Returns a QR Code symbol representing the specified Unicode text string at the specified error correction level.
	 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
	 * Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
	 * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
	 * ecl argument if it can be done without increasing the version.
	 * @param text the text to be encoded, which can be any Unicode string
	 * @param ecl the error correction level to use (will be boosted)
	 * @return a QR Code representing the text
	 * @throws NullPointerException if the text or error correction level is {@code null}
	 * @throws IllegalArgumentException if the text fails to fit in the
	 * largest version QR Code at the ECL, which means it is too long
	 */
	public static QrCode encodeText(String text, Ecc ecl) {
		Objects.requireNonNull(text);
		Objects.requireNonNull(ecl);
		List<QrSegment> segs = QrSegment.makeSegments(text);
		return encodeSegments(segs, ecl);
	}
	
	
	/**
	 * Returns a QR Code symbol representing the specified binary data string at the specified error correction level.
	 * This function always encodes using the binary segment mode, not any text mode. The maximum number of
	 * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
	 * The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
	 * @param data the binary data to encode
	 * @param ecl the error correction level to use (will be boosted)
	 * @return a QR Code representing the binary data
	 * @throws NullPointerException if the data or error correction level is {@code null}
	 * @throws IllegalArgumentException if the data fails to fit in the
	 * largest version QR Code at the ECL, which means it is too long
	 */
	public static QrCode encodeBinary(byte[] data, Ecc ecl) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(ecl);
		QrSegment seg = QrSegment.makeBytes(data);
		return encodeSegments(Arrays.asList(seg), ecl);
	}
	
	
	/*---- Static factory functions (mid level) ----*/
	
	/**
	 * Returns a QR Code symbol representing the specified segments at the specified error correction
	 * level. The smallest possible QR Code version is automatically chosen for the output. The ECC level
	 * of the result may be higher than the ecl argument if it can be done without increasing the version.
	 * <p>This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and binary) to encode text more efficiently.
	 * This function is considered to be lower level than simply encoding text or binary data.</p>
	 * @param segs the segments to encode
	 * @param ecl the error correction level to use (will be boosted)
	 * @return a QR Code representing the segments
	 * @throws NullPointerException if the list of segments, any segment, or the error correction level is {@code null}
	 * @throws IllegalArgumentException if the segments fail to fit in the
	 * largest version QR Code at the ECL, which means they are too long
	 */
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl) {
		return encodeSegments(segs, ecl, MIN_VERSION, MAX_VERSION, -1, true);
	}
	
	
	/**
	 * Returns a QR Code symbol representing the specified segments with the specified encoding parameters.
	 * The smallest possible QR Code version within the specified range is automatically chosen for the output.
	 * <p>This function allows the user to create a custom sequence of segments that switches
	 * between modes (such as alphanumeric and binary) to encode text more efficiently.
	 * This function is considered to be lower level than simply encoding text or binary data.</p>
	 * @param segs the segments to encode
	 * @param ecl the error correction level to use (may be boosted)
	 * @param minVersion the minimum allowed version of the QR symbol (at least 1)
	 * @param maxVersion the maximum allowed version of the QR symbol (at most 40)
	 * @param mask the mask pattern to use, which is either -1 for automatic choice or from 0 to 7 for fixed choice
	 * @param boostEcl increases the error correction level if it can be done without increasing the version number
	 * @return a QR Code representing the segments
	 * @throws NullPointerException if the list of segments, any segment, or the error correction level is {@code null}
	 * @throws IllegalArgumentException if 1 &le; minVersion &le; maxVersion &le; 40
	 * is violated, or if mask &lt; &minus;1 or mask > 7, or if the segments fail
	 * to fit in the maxVersion QR Code at the ECL, which means they are too long
	 */
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl, int minVersion, int maxVersion, int mask, boolean boostEcl) {
		Objects.requireNonNull(segs);
		Objects.requireNonNull(ecl);
		if (!(MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= MAX_VERSION) || mask < -1 || mask > 7)
			throw new IllegalArgumentException("Invalid value");
		
		// Find the minimal version number to use
		int version, dataUsedBits;
		for (version = minVersion; ; version++) {
			int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			dataUsedBits = QrSegment.getTotalBits(segs, version);
			if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
			if (version >= maxVersion)  // All versions in the range could not fit the given data
				throw new IllegalArgumentException("Data too long");
		}
		assert dataUsedBits != -1;
		
		// Increase the error correction level while the data still fits in the current version number
		for (Ecc newEcl : Ecc.values()) {  // From low to high
			if (boostEcl && dataUsedBits <= getNumDataCodewords(version, newEcl) * 8)
				ecl = newEcl;
		}
		
		// Concatenate all segments to create the data bit string
		BitBuffer bb = new BitBuffer();
		for (QrSegment seg : segs) {
			bb.appendBits(seg.mode.modeBits, 4);
			bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
			bb.appendData(seg.data);
		}
		assert bb.bitLength() == dataUsedBits;
		
		// Add terminator and pad up to a byte if applicable
		int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
		assert bb.bitLength() <= dataCapacityBits;
		bb.appendBits(0, Math.min(4, dataCapacityBits - bb.bitLength()));
		bb.appendBits(0, (8 - bb.bitLength() % 8) % 8);
		assert bb.bitLength() % 8 == 0;
		
		// Pad with alternating bytes until data capacity is reached
		for (int padByte = 0xEC; bb.bitLength() < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
			bb.appendBits(padByte, 8);
		
		// Create the QR Code symbol
		return new QrCode(version, ecl, bb.getBytes(), mask);
	}
	
	
	
	/*---- Instance fields ----*/
	
	// Public immutable scalar parameters
	
	/** This QR Code symbol's version number, which is always between 1 and 40 (inclusive). */
	public final int version;
	
	/** The width and height of this QR Code symbol, measured in modules.
	 * Always equal to version &times; 4 + 17, in the range 21 to 177 (inclusive). */
	public final int size;
	
	/** The error correction level used in this QR Code symbol. Never {@code null}. */
	public final Ecc errorCorrectionLevel;
	
	/** The mask pattern used in this QR Code symbol, in the range 0 to 7 (i.e. unsigned 3-bit integer).
	 * Note that even if a constructor was called with automatic masking requested
	 * (mask = -1), the resulting object will still have a mask value between 0 and 7. */
	public final int mask;
	
	// Private grids of modules/pixels (conceptually immutable)
	private boolean[][] modules;     // The modules of this QR Code symbol (false = white, true = black)
	private boolean[][] isFunction;  // Indicates function modules that are not subjected to masking
	
	
	
	/*---- Constructor (low level) ----*/
	
	/**
	 * Constructs a QR Code symbol with the specified version number, error correction level, binary data array, and mask number.
	 * <p>This is a cumbersome low-level constructor that should not be invoked directly by the user.
	 * To go one level up, see the {@link #encodeSegments(List,Ecc,int,int,int,boolean)} function.</p>
	 * @param ver the version number to use, which must be in the range 1 to 40 (inclusive)
	 * @param ecl the error correction level to use
	 * @param dataCodewords the bytes representing segments to encode (without ECC)
	 * @param mask the mask pattern to use, which is either &minus;1 for automatic choice or from 0 to 7 for fixed choice
	 * @throws NullPointerException if the byte array or error correction level is {@code null}
	 * @throws IllegalArgumentException if the version or mask value is out of range,
	 * or if the data is the wrong length for the specified version and error correction level
	 */
	public QrCode(int ver, Ecc ecl, byte[] dataCodewords, int mask) {
		// Check arguments
		errorCorrectionLevel = Objects.requireNonNull(ecl);
		Objects.requireNonNull(dataCodewords);
		if (ver < MIN_VERSION || ver > MAX_VERSION || mask < -1 || mask > 7)
			throw new IllegalArgumentException("Value out of range");
		
		// Initialize fields
		version = ver;
		size = ver * 4 + 17;
		modules = new boolean[size][size];  // Entirely white grid
		isFunction = new boolean[size][size];
		
		// Draw function patterns, draw all codewords, do masking
		drawFunctionPatterns();
		byte[] allCodewords = addEccAndInterleave(dataCodewords);
		drawCodewords(allCodewords);
		this.mask = handleConstructorMasking(mask);
		isFunction = null;
	}
	
	
	
	/*---- Public instance methods ----*/
	
	/**
	 * Returns the color of the module (pixel) at the specified coordinates, which is either
	 * false for white or true for black. The top left corner has the coordinates (x=0, y=0).
	 * If the specified coordinates are out of bounds, then false (white) is returned.
	 * @param x the x coordinate, where 0 is the left edge and size&minus;1 is the right edge
	 * @param y the y coordinate, where 0 is the top edge and size&minus;1 is the bottom edge
	 * @return the module's color, which is either false (white) or true (black)
	 */
	public boolean getModule(int x, int y) {
		return 0 <= x && x < size && 0 <= y && y < size && modules[y][x];
	}
	
	
	/**
	 * Returns a new image object representing this QR Code, with the specified module scale and number
	 * of border modules. For example, the arguments scale=10, border=4 means to pad the QR Code symbol
	 * with 4 white border modules on all four edges, then use 10*10 pixels to represent each module.
	 * The resulting image only contains the hex colors 000000 and FFFFFF.
	 * @param scale the module scale factor, which must be positive
	 * @param border the number of border modules to add, which must be non-negative
	 * @return an image representing this QR Code, with padding and scaling
	 * @throws IllegalArgumentException if the scale or border is out of range, or if
	 * {scale, border, size} cause the image dimensions to exceed Integer.MAX_VALUE
	 */
	public BufferedImage toImage(int scale, int border) {
		if (scale <= 0 || border < 0)
			throw new IllegalArgumentException("Value out of range");
		if (border > Integer.MAX_VALUE / 2 || size + border * 2L > Integer.MAX_VALUE / scale)
			throw new IllegalArgumentException("Scale or border too large");
		
		BufferedImage result = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				boolean color = getModule(x / scale - border, y / scale - border);
				result.setRGB(x, y, color ? 0x000000 : 0xFFFFFF);
			}
		}
		return result;
	}
	
	
	/**
	 * Returns a string of SVG XML code representing an image of this QR Code symbol with the specified
	 * number of border modules. Note that Unix newlines (\n) are always used, regardless of the platform.
	 * @param border the number of border modules to add, which must be non-negative
	 * @return a string representing this QR Code as an SVG document
	 * @throws IllegalArgumentException if the border is negative
	 */
	public String toSvgString(int border) {
		if (border < 0)
			throw new IllegalArgumentException("Border must be non-negative");
		long brd = border;
		StringBuilder sb = new StringBuilder()
			.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
			.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
				size + brd * 2))
			.append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
			.append("\t<path d=\"");
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (getModule(x, y)) {
					if (x != 0 || y != 0)
						sb.append(" ");
					sb.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd));
				}
			}
		}
		return sb
			.append("\" fill=\"#000000\"/>\n")
			.append("</svg>\n")
			.toString();
	}
	
	
	
	/*---- Private helper methods for constructor: Drawing function modules ----*/
	
	// Reads this object's version field, and draws and marks all function modules.
	private void drawFunctionPatterns() {
		// Draw horizontal and vertical timing patterns
		for (int i = 0; i < size; i++) {
			setFunctionModule(6, i, i % 2 == 0);
			setFunctionModule(i, 6, i % 2 == 0);
		}
		
		// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
		drawFinderPattern(3, 3);
		drawFinderPattern(size - 4, 3);
		drawFinderPattern(3, size - 4);
		
		// Draw numerous alignment patterns
		int[] alignPatPos = getAlignmentPatternPositions();
		int numAlign = alignPatPos.length;
		for (int i = 0; i < numAlign; i++) {
			for (int j = 0; j < numAlign; j++) {
				// Don't draw on the three finder corners
				if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
					drawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
			}
		}
		
		// Draw configuration data
		drawFormatBits(0);  // Dummy mask value; overwritten later in the constructor
		drawVersion();
	}
	
	
	// Draws two copies of the format bits (with its own error correction code)
	// based on the given mask and this object's error correction level field.
	private void drawFormatBits(int mask) {
		// Calculate error correction code and pack bits
		int data = errorCorrectionLevel.formatBits << 3 | mask;  // errCorrLvl is uint2, mask is uint3
		int rem = data;
		for (int i = 0; i < 10; i++)
			rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
		int bits = (data << 10 | rem) ^ 0x5412;  // uint15
		assert bits >>> 15 == 0;
		
		// Draw first copy
		for (int i = 0; i <= 5; i++)
			setFunctionModule(8, i, getBit(bits, i));
		setFunctionModule(8, 7, getBit(bits, 6));
		setFunctionModule(8, 8, getBit(bits, 7));
		setFunctionModule(7, 8, getBit(bits, 8));
		for (int i = 9; i < 15; i++)
			setFunctionModule(14 - i, 8, getBit(bits, i));
		
		// Draw second copy
		for (int i = 0; i <= 7; i++)
			setFunctionModule(size - 1 - i, 8, getBit(bits, i));
		for (int i = 8; i < 15; i++)
			setFunctionModule(8, size - 15 + i, getBit(bits, i));
		setFunctionModule(8, size - 8, true);  // Always black
	}
	
	
	// Draws two copies of the version bits (with its own error correction code),
	// based on this object's version field, iff 7 <= version <= 40.
	private void drawVersion() {
		if (version < 7)
			return;
		
		// Calculate error correction code and pack bits
		int rem = version;  // version is uint6, in the range [7, 40]
		for (int i = 0; i < 12; i++)
			rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
		int bits = version << 12 | rem;  // uint18
		assert bits >>> 18 == 0;
		
		// Draw two copies
		for (int i = 0; i < 18; i++) {
			boolean bit = getBit(bits, i);
			int a = size - 11 + i % 3;
			int b = i / 3;
			setFunctionModule(a, b, bit);
			setFunctionModule(b, a, bit);
		}
	}
	
	
	// Draws a 9*9 finder pattern including the border separator,
	// with the center module at (x, y). Modules can be out of bounds.
	private void drawFinderPattern(int x, int y) {
		for (int dy = -4; dy <= 4; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				int dist = Math.max(Math.abs(dx), Math.abs(dy));  // Chebyshev/infinity norm
				int xx = x + dx, yy = y + dy;
				if (0 <= xx && xx < size && 0 <= yy && yy < size)
					setFunctionModule(xx, yy, dist != 2 && dist != 4);
			}
		}
	}
	
	
	// Draws a 5*5 alignment pattern, with the center module
	// at (x, y). All modules must be in bounds.
	private void drawAlignmentPattern(int x, int y) {
		for (int dy = -2; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++)
				setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
		}
	}
	
	
	// Sets the color of a module and marks it as a function module.
	// Only used by the constructor. Coordinates must be in bounds.
	private void setFunctionModule(int x, int y, boolean isBlack) {
		modules[y][x] = isBlack;
		isFunction[y][x] = true;
	}
	
	
	/*---- Private helper methods for constructor: Codewords and masking ----*/
	
	// Returns a new byte string representing the given data with the appropriate error correction
	// codewords appended to it, based on this object's version and error correction level.
	private byte[] addEccAndInterleave(byte[] data) {
		Objects.requireNonNull(data);
		if (data.length != getNumDataCodewords(version, errorCorrectionLevel))
			throw new IllegalArgumentException();
		
		// Calculate parameter numbers
		int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
		int blockEccLen = ECC_CODEWORDS_PER_BLOCK  [errorCorrectionLevel.ordinal()][version];
		int rawCodewords = getNumRawDataModules(version) / 8;
		int numShortBlocks = numBlocks - rawCodewords % numBlocks;
		int shortBlockLen = rawCodewords / numBlocks;
		
		// Split data into blocks and append ECC to each block
		byte[][] blocks = new byte[numBlocks][];
		ReedSolomonGenerator rs = new ReedSolomonGenerator(blockEccLen);
		for (int i = 0, k = 0; i < numBlocks; i++) {
			byte[] dat = Arrays.copyOfRange(data, k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
			k += dat.length;
			byte[] block = Arrays.copyOf(dat, shortBlockLen + 1);
			byte[] ecc = rs.getRemainder(dat);
			System.arraycopy(ecc, 0, block, block.length - blockEccLen, ecc.length);
			blocks[i] = block;
		}
		
		// Interleave (not concatenate) the bytes from every block into a single sequence
		byte[] result = new byte[rawCodewords];
		for (int i = 0, k = 0; i < blocks[0].length; i++) {
			for (int j = 0; j < blocks.length; j++) {
				// Skip the padding byte in short blocks
				if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
					result[k] = blocks[j][i];
					k++;
				}
			}
		}
		return result;
	}
	
	
	// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
	// data area of this QR Code symbol. Function modules need to be marked off before this is called.
	private void drawCodewords(byte[] data) {
		Objects.requireNonNull(data);
		if (data.length != getNumRawDataModules(version) / 8)
			throw new IllegalArgumentException();
		
		int i = 0;  // Bit index into the data
		// Do the funny zigzag scan
		for (int right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
			if (right == 6)
				right = 5;
			for (int vert = 0; vert < size; vert++) {  // Vertical counter
				for (int j = 0; j < 2; j++) {
					int x = right - j;  // Actual x coordinate
					boolean upward = ((right + 1) & 2) == 0;
					int y = upward ? size - 1 - vert : vert;  // Actual y coordinate
					if (!isFunction[y][x] && i < data.length * 8) {
						modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
						i++;
					}
					// If this QR Code has any remainder bits (0 to 7), they were assigned as
					// 0/false/white by the constructor and are left unchanged by this method
				}
			}
		}
		assert i == data.length * 8;
	}
	
	
	// XORs the codeword modules in this QR Code with the given mask pattern.
	// The function modules must be marked and the codeword bits must be drawn
	// before masking. Due to the arithmetic of XOR, calling applyMask() with
	// the same mask value a second time will undo the mask. A final well-formed
	// QR Code symbol needs exactly one (not zero, two, etc.) mask applied.
	private void applyMask(int mask) {
		if (mask < 0 || mask > 7)
			throw new IllegalArgumentException("Mask value out of range");
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean invert;
				switch (mask) {
					case 0:  invert = (x + y) % 2 == 0;                    break;
					case 1:  invert = y % 2 == 0;                          break;
					case 2:  invert = x % 3 == 0;                          break;
					case 3:  invert = (x + y) % 3 == 0;                    break;
					case 4:  invert = (x / 3 + y / 2) % 2 == 0;            break;
					case 5:  invert = x * y % 2 + x * y % 3 == 0;          break;
					case 6:  invert = (x * y % 2 + x * y % 3) % 2 == 0;    break;
					case 7:  invert = ((x + y) % 2 + x * y % 3) % 2 == 0;  break;
					default:  throw new AssertionError();
				}
				modules[y][x] ^= invert & !isFunction[y][x];
			}
		}
	}
	
	
	// A messy helper function for the constructor. This QR Code must be in an unmasked state when this
	// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
	// This method applies and returns the actual mask chosen, from 0 to 7.
	private int handleConstructorMasking(int mask) {
		if (mask == -1) {  // Automatically choose best mask
			int minPenalty = Integer.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				drawFormatBits(i);
				applyMask(i);
				int penalty = getPenaltyScore();
				if (penalty < minPenalty) {
					mask = i;
					minPenalty = penalty;
				}
				applyMask(i);  // Undoes the mask due to XOR
			}
		}
		assert 0 <= mask && mask <= 7;
		drawFormatBits(mask);  // Overwrite old format bits
		applyMask(mask);  // Apply the final choice of mask
		return mask;  // The caller shall assign this value to the final-declared field
	}
	
	
	// Calculates and returns the penalty score based on state of this QR Code's current modules.
	// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	private int getPenaltyScore() {
		int result = 0;
		
		// Adjacent modules in row having same color
		for (int y = 0; y < size; y++) {
			boolean colorX = false;
			for (int x = 0, runX = 0; x < size; x++) {
				if (x == 0 || modules[y][x] != colorX) {
					colorX = modules[y][x];
					runX = 1;
				} else {
					runX++;
					if (runX == 5)
						result += PENALTY_N1;
					else if (runX > 5)
						result++;
				}
			}
		}
		// Adjacent modules in column having same color
		for (int x = 0; x < size; x++) {
			boolean colorY = false;
			for (int y = 0, runY = 0; y < size; y++) {
				if (y == 0 || modules[y][x] != colorY) {
					colorY = modules[y][x];
					runY = 1;
				} else {
					runY++;
					if (runY == 5)
						result += PENALTY_N1;
					else if (runY > 5)
						result++;
				}
			}
		}
		
		// 2*2 blocks of modules having same color
		for (int y = 0; y < size - 1; y++) {
			for (int x = 0; x < size - 1; x++) {
				boolean color = modules[y][x];
				if (  color == modules[y][x + 1] &&
				      color == modules[y + 1][x] &&
				      color == modules[y + 1][x + 1])
					result += PENALTY_N2;
			}
		}
		
		// Finder-like pattern in rows
		for (int y = 0; y < size; y++) {
			for (int x = 0, bits = 0; x < size; x++) {
				bits = ((bits << 1) & 0b11111111111) | (modules[y][x] ? 1 : 0);
				if (x >= 10 && (bits == 0b00001011101 || bits == 0b10111010000))  // Needs 11 bits accumulated
					result += PENALTY_N3;
			}
		}
		// Finder-like pattern in columns
		for (int x = 0; x < size; x++) {
			for (int y = 0, bits = 0; y < size; y++) {
				bits = ((bits << 1) & 0b11111111111) | (modules[y][x] ? 1 : 0);
				if (y >= 10 && (bits == 0b00001011101 || bits == 0b10111010000))  // Needs 11 bits accumulated
					result += PENALTY_N3;
			}
		}
		
		// Balance of black and white modules
		int black = 0;
		for (boolean[] row : modules) {
			for (boolean color : row) {
				if (color)
					black++;
			}
		}
		int total = size * size;  // Note that size is odd, so black/total != 1/2
		// Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
		int k = (Math.abs(black * 20 - total * 10) + total - 1) / total - 1;
		result += k * PENALTY_N4;
		return result;
	}
	
	
	
	/*---- Private static helper functions ----*/
	
	// Returns an ascending list of positions of alignment patterns for this version number.
	// Each position is in the range [0,177), and are used on both the x and y axes.
	// This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
	private int[] getAlignmentPatternPositions() {
		if (version == 1)
			return new int[]{};
		else {
			int numAlign = version / 7 + 2;
			int step;
			if (version == 32)  // Special snowflake
				step = 26;
			else  // step = ceil[(size - 13) / (numAlign*2 - 2)] * 2
				step = (version*4 + numAlign*2 + 1) / (numAlign*2 - 2) * 2;
			int[] result = new int[numAlign];
			result[0] = 6;
			for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step)
				result[i] = pos;
			return result;
		}
	}
	
	
	// Returns the number of data bits that can be stored in a QR Code of the given version number, after
	// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
	// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
	private static int getNumRawDataModules(int ver) {
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		
		int size = ver * 4 + 17;
		int result = size * size;   // Number of modules in the whole QR symbol square
		result -= 8 * 8 * 3;        // Subtract the three finders with separators
		result -= 15 * 2 + 1;       // Subtract the format information and black module
		result -= (size - 16) * 2;  // Subtract the timing patterns (excluding finders)
		// The five lines above are equivalent to: int result = (16 * ver + 128) * ver + 64;
		if (ver >= 2) {
			int numAlign = ver / 7 + 2;
			result -= (numAlign - 1) * (numAlign - 1) * 25;  // Subtract alignment patterns not overlapping with timing patterns
			result -= (numAlign - 2) * 2 * 20;  // Subtract alignment patterns that overlap with timing patterns
			// The two lines above are equivalent to: result -= (25 * numAlign - 10) * numAlign - 55;
			if (ver >= 7)
				result -= 6 * 3 * 2;  // Subtract version information
		}
		return result;
	}
	
	
	// Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	// QR Code of the given version number and error correction level, with remainder bits discarded.
	// This stateless pure function could be implemented as a (40*4)-cell lookup table.
	static int getNumDataCodewords(int ver, Ecc ecl) {
		return getNumRawDataModules(ver) / 8
			- ECC_CODEWORDS_PER_BLOCK    [ecl.ordinal()][ver]
			* NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal()][ver];
	}
	
	
	// Returns true iff the i'th bit of x is set to 1.
	static boolean getBit(int x, int i) {
		return ((x >>> i) & 1) != 0;
	}
	
	
	/*---- Constants and tables ----*/
	
	public static final int MIN_VERSION =  1;
	public static final int MAX_VERSION = 40;
	
	
	// For use in getPenaltyScore(), when evaluating which mask is best.
	private static final int PENALTY_N1 =  3;
	private static final int PENALTY_N2 =  3;
	private static final int PENALTY_N3 = 40;
	private static final int PENALTY_N4 = 10;
	
	
	private static final byte[][] ECC_CODEWORDS_PER_BLOCK = {
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Low
		{-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},  // Medium
		{-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Quartile
		{-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // High
	};
	
	private static final byte[][] NUM_ERROR_CORRECTION_BLOCKS = {
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},  // Low
		{-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},  // Medium
		{-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},  // Quartile
		{-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},  // High
	};
	
	
	
	/*---- Public helper enumeration ----*/
	
	/**
	 * Represents the error correction level used in a QR Code symbol.
	 */
	public enum Ecc {
		// These enum constants must be declared in ascending order of error protection,
		// for the sake of the implicit ordinal() method and values() function.
		LOW(1), MEDIUM(0), QUARTILE(3), HIGH(2);
		
		// In the range 0 to 3 (unsigned 2-bit integer).
		final int formatBits;
		
		// Constructor.
		private Ecc(int fb) {
			formatBits = fb;
		}
	}
	
	
	
	/*---- Private helper class ----*/
	
	/**
	 * Computes the Reed-Solomon error correction codewords for a sequence of data codewords
	 * at a given degree. Objects are immutable, and the state only depends on the degree.
	 * This class exists because each data block in a QR Code shares the same the divisor polynomial.
	 */
	private static final class ReedSolomonGenerator {
		
		/*-- Field --*/
		
		// Coefficients of the divisor polynomial, stored from highest to lowest power, excluding the leading term which
		// is always 1. For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
		private final byte[] coefficients;
		
		
		/*-- Constructor --*/
		
		/**
		 * Constructs a Reed-Solomon ECC generator for the specified degree. This could be implemented
		 * as a lookup table over all possible parameter values, instead of as an algorithm.
		 * @param degree the divisor polynomial degree, which must be between 1 and 255 (inclusive)
		 * @throws IllegalArgumentException if degree &lt; 1 or degree > 255
		 */
		public ReedSolomonGenerator(int degree) {
			if (degree < 1 || degree > 255)
				throw new IllegalArgumentException("Degree out of range");
			
			// Start with the monomial x^0
			coefficients = new byte[degree];
			coefficients[degree - 1] = 1;
			
			// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
			// drop the highest term, and store the rest of the coefficients in order of descending powers.
			// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
			int root = 1;
			for (int i = 0; i < degree; i++) {
				// Multiply the current product by (x - r^i)
				for (int j = 0; j < coefficients.length; j++) {
					coefficients[j] = (byte)multiply(coefficients[j] & 0xFF, root);
					if (j + 1 < coefficients.length)
						coefficients[j] ^= coefficients[j + 1];
				}
				root = multiply(root, 0x02);
			}
		}
		
		
		/*-- Method --*/
		
		/**
		 * Computes and returns the Reed-Solomon error correction codewords for the specified
		 * sequence of data codewords. The returned object is always a new byte array.
		 * This method does not alter this object's state (because it is immutable).
		 * @param data the sequence of data codewords
		 * @return the Reed-Solomon error correction codewords
		 * @throws NullPointerException if the data is {@code null}
		 */
		public byte[] getRemainder(byte[] data) {
			Objects.requireNonNull(data);
			
			// Compute the remainder by performing polynomial division
			byte[] result = new byte[coefficients.length];
			for (byte b : data) {
				int factor = (b ^ result[0]) & 0xFF;
				System.arraycopy(result, 1, result, 0, result.length - 1);
				result[result.length - 1] = 0;
				for (int i = 0; i < result.length; i++)
					result[i] ^= multiply(coefficients[i] & 0xFF, factor);
			}
			return result;
		}
		
		
		/*-- Static function --*/
		
		// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
		// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
		private static int multiply(int x, int y) {
			assert x >>> 8 == 0 && y >>> 8 == 0;
			// Russian peasant multiplication
			int z = 0;
			for (int i = 7; i >= 0; i--) {
				z = (z << 1) ^ ((z >>> 7) * 0x11D);
				z ^= ((y >>> i) & 1) * x;
			}
			assert z >>> 8 == 0;
			return z;
		}
		
	}
	
}
