package main;

import libs.F5Random;
import libs.Permutation;
import libs.HuffmanDecode;

import java.io.*;
import java.util.ArrayList;


public class DecodeFromJPG {
    private static File f; // carrier file
    private static String value = "";
    private static ArrayList<Byte> list = new ArrayList<>();
    private static byte[] carrier; // carrier data

    private static int[] coeff; // dct values

    private static String password;

    private static byte[] deZigZag = {
            0, 1, 5, 6, 14, 15, 27, 28, 2, 4, 7, 13, 16, 26, 29, 42, 3, 8, 12, 17, 25, 30, 41, 43, 9, 11, 18, 24, 31,
            40, 44, 53, 10, 19, 23, 32, 39, 45, 52, 54, 20, 22, 33, 38, 46, 51, 55, 60, 21, 34, 37, 47, 50, 56, 59, 61,
            35, 36, 48, 49, 57, 58, 62, 63};

    public static void resetTemps() {
        DecodeFromJPG.list = new ArrayList<>();
        DecodeFromJPG.value = "";
    }

    public static void extract(final InputStream fis, final int flength, final String password)
            throws IOException {
        carrier = new byte[flength];
        fis.read(carrier);
        final HuffmanDecode hd = new HuffmanDecode(carrier);
        System.out.println("Huffman decoding starts");
        coeff = hd.decode();
        System.out.println("Permutation starts");
        final F5Random random = new F5Random(password.getBytes());
        final Permutation permutation = new Permutation(coeff.length, random);
        System.out.println(coeff.length + " indices shuffled");
        int extractedByte = 0;
        int availableExtractedBits = 0;
        int extractedFileLength = 0;
        int nBytesExtracted = 0;
        int shuffledIndex = 0;
        int extractedBit;
        int i;
        System.out.println("Extraction starts");
        // extract length information
        for (i = 0; availableExtractedBits < 32; i++) {
            shuffledIndex = permutation.getShuffled(i);
            if (shuffledIndex % 64 == 0) {
                continue; // skip DC coefficients
            }
            shuffledIndex = shuffledIndex - shuffledIndex % 64 + deZigZag[shuffledIndex % 64];
            if (coeff[shuffledIndex] == 0) {
                continue; // skip zeroes
            }
            if (coeff[shuffledIndex] > 0) {
                extractedBit = coeff[shuffledIndex] & 1;
            } else {
                extractedBit = 1 - (coeff[shuffledIndex] & 1);
            }
            extractedFileLength |= extractedBit << availableExtractedBits++;
        }
        // remove pseudo random pad
        extractedFileLength ^= random.getNextByte();
        extractedFileLength ^= random.getNextByte() << 8;
        extractedFileLength ^= random.getNextByte() << 16;
        extractedFileLength ^= random.getNextByte() << 24;
        int k = extractedFileLength >> 24;
        k %= 32;
        final int n = (1 << k) - 1;
        extractedFileLength &= 0x007fffff;
        System.out.println("Length of embedded file: " + extractedFileLength + " bytes");
        availableExtractedBits = 0;
        if (n > 0) {
            int startOfN = i;
            int hash;
            System.out.println("(1, " + n + ", " + k + ") code used");
            extractingLoop:
            do {
                // 1. read n places, and calculate k bits
                hash = 0;
                int code = 1;
                for (i = 0; code <= n; i++) {
                    // check for pending end of coeff
                    if (startOfN + i >= coeff.length) {
                        break extractingLoop;
                    }
                    shuffledIndex = permutation.getShuffled(startOfN + i);
                    if (shuffledIndex % 64 == 0) {
                        continue; // skip DC coefficients
                    }
                    shuffledIndex = shuffledIndex - shuffledIndex % 64 + deZigZag[shuffledIndex % 64];
                    if (coeff[shuffledIndex] == 0) {
                        continue; // skip zeroes
                    }
                    if (coeff[shuffledIndex] > 0) {
                        extractedBit = coeff[shuffledIndex] & 1;
                    } else {
                        extractedBit = 1 - (coeff[shuffledIndex] & 1);
                    }
                    if (extractedBit == 1) {
                        hash ^= code;
                    }
                    code++;
                }
                startOfN += i;
                // 2. write k bits bytewise
                for (i = 0; i < k; i++) {
                    extractedByte |= (hash >> i & 1) << availableExtractedBits++;
                    if (availableExtractedBits == 8) {
                        // remove pseudo random pad
                        extractedByte ^= random.getNextByte();
                        //fos.write((byte) extractedByte);
                        list.add((byte) extractedByte);
                        //value += (char) extractedByte;
                        extractedByte = 0;
                        availableExtractedBits = 0;
                        nBytesExtracted++;
                        // check for pending end of embedded data
                        if (nBytesExtracted == extractedFileLength) {
                            break extractingLoop;
                        }
                    }
                }
            } while (true);
        } else {
            System.out.println("Default code used");
            for (; i < coeff.length; i++) {
                shuffledIndex = permutation.getShuffled(i);
                if (shuffledIndex % 64 == 0) {
                    continue; // skip DC coefficients
                }
                shuffledIndex = shuffledIndex - shuffledIndex % 64 + deZigZag[shuffledIndex % 64];
                if (coeff[shuffledIndex] == 0) {
                    continue; // skip zeroes
                }
                if (coeff[shuffledIndex] > 0) {
                    extractedBit = coeff[shuffledIndex] & 1;
                } else {
                    extractedBit = 1 - (coeff[shuffledIndex] & 1);
                }
                extractedByte |= extractedBit << availableExtractedBits++;
                if (availableExtractedBits == 8) {
                    // remove pseudo random pad
                    extractedByte ^= random.getNextByte();
                    //fos.write((byte) extractedByte);
                    list.add((byte) extractedByte);
                    //value += (char) extractedByte;
                    extractedByte = 0;
                    availableExtractedBits = 0;
                    nBytesExtracted++;
                    if (nBytesExtracted == extractedFileLength) {
                        break;
                    }
                }
            }
        }
        if (nBytesExtracted < extractedFileLength) {
            if(nBytesExtracted == 0) {
                Utils.errorDialog("No encoded data!");
            } else {
                Utils.errorDialog("Incomplete file: only " + nBytesExtracted + " of " + extractedFileLength
                        + " bytes extracted");
            }
        }

        if (list == null) {
            return;
        }
        byte[] array = new byte[list.size()];
        int ji = 0;
        for (Byte current : list) {
            array[ji] = current;
            ji++;
        }

        value = new String(array);
    }

    public static String decodeFile(String path) {
        password = "abc123";
        try {
            f = new File(path);
            final FileInputStream fis = new FileInputStream(f);
            extract(fis, (int) f.length(), password);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return value;
    }
}