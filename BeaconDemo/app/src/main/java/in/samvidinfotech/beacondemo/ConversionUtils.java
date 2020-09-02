package in.samvidinfotech.beacondemo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import java.util.UUID;

/**
 * Utils class to help with conversion of Bytes to Hex.
 */

public final class ConversionUtils {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    @Nullable
    private static SparseArray<Character> alphabet = null;
    private static int bitsAvailable = 160;

    private ConversionUtils() {
    }

    /**
     * Converts byte[] to an iBeacon {@link UUID}.
     * From http://stackoverflow.com/a/9855338.
     *
     * @param bytes Byte[] to convert
     * @return UUID
     */
    public static UUID bytesToUuid(@NonNull final byte[] bytes) {
        final char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            final int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        final String hex = new String(hexChars);

        return UUID.fromString(hex.substring(0, 8) + "-" +
                hex.substring(8, 12) + "-" +
                hex.substring(12, 16) + "-" +
                hex.substring(16, 20) + "-" +
                hex.substring(20, 32));
    }

    /**
     * Converts a {@link UUID} to a byte[]. This is used to create a {@link android.bluetooth.le.ScanFilter}.
     * From http://stackoverflow.com/questions/29664316/bluetooth-le-scan-filter-not-working.
     *
     * @param uuid UUID to convert to a byte[]
     * @return byte[]
     */
    @NonNull
    public static byte[] UuidToByteArray(@NonNull final UUID uuid) {
        final String hex = uuid.toString().replace("-","");
        final int length = hex.length();
        final byte[] result = new byte[length / 2];

        for (int i = 0; i < length; i += 2)
        {
            result[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }

        return result;
    }

    /**
     * Convert major or minor to hex byte[]. This is used to create a {@link android.bluetooth.le.ScanFilter}.
     *
     * @param value major or minor to convert to byte[]
     * @return byte[]
     */
    @NonNull
    public static byte[] integerToByteArray(final int value) {
        final byte[] result = new byte[2];
        result[0] = (byte) (value / 256);
        result[1] = (byte) (value % 256);

        return result;
    }

    /**
     * Convert major and minor byte array to integer.
     *
     * @param byteArray that contains major and minor byte
     * @return integer value for major and minor
     */
    public static int byteArrayToInteger(final byte[] byteArray) {
        return (byteArray[0] & 0xff) * 0x100 + (byteArray[1] & 0xff);
    }

    public static @NonNull String bitStringFromBeaconData(long msb, long lsb, int major, int minor) {
        //Java does not put the leading 0s in the toBinaryString method, so we have to format the string to manually
        //put 0s.
        String mostSignificantBits = String.format("%64s", Long.toBinaryString(msb)).replace(' ', '0');
        String leastSignificantBits = String.format("%64s", Long.toBinaryString(lsb)).replace(' ', '0');
        String majorBits = String.format("%16s", Integer.toBinaryString(major)).replace(' ', '0');
        String minorBits = String.format("%16s", Integer.toBinaryString(minor)).replace(' ', '0');
        return (mostSignificantBits + leastSignificantBits + majorBits + minorBits);
    }

    public static int numberOfCharactersFromBeaconData(String bitString) {
        return Integer.parseInt(bitString.substring(5, 10), 2);
    }

    /**
     * Converts the bits from concatenation of UUID, major, minor to String of characters. String must be of length
     * 150 bits, received after truncating size and feature bits.
     * @param bitString 150 bits with encoded content
     * @param numberOfCharacters number of characters present in the bit string
     * @return String of characters
     */
    public static @Nullable String bitsToString(@NonNull String bitString, int numberOfCharacters) {
        if (alphabet == null) {
            return null;
        }

        final int bitsPerCharacter = (int) Math.ceil(Math.log10(alphabet.size()) / Math.log10(2));
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < numberOfCharacters; ++i) {
            int characterStartPosition = i * bitsPerCharacter;
            builder.append(alphabet.get(Integer.parseInt(bitString.substring(characterStartPosition,
                    characterStartPosition + bitsPerCharacter), 2), ' '));
        }

        return builder.toString();
    }

    /**
     * Creates a String similar to the form of the one retrieved from BLE device. But it has only 155 bits.
     * 5 bits for feature processing must be generated separately and must be prepended to the string before
     * passing it to the service.
     * @param message content string with size <= 30 characters
     * @return bit string of 155 bits with the content encoded using the alphabet present in file "assets/alphabet"
     */
    public static @Nullable String messageToOfflineBits(@NonNull String message) {
        if (alphabet == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < message.length(); i++) {
            int charIndex = alphabet.indexOfValue(message.charAt(i));
            builder.append(String.format("%5s", Integer.toBinaryString(charIndex))
                    .replace(' ', '0'));
        }

        for (int i = 0; i < (30 - message.length()); i++) {
            builder.append(String.format("%5s", Integer.toBinaryString(0))
                    .replace(' ', '0'));
        }

        String size = String.format("%5s", Integer.toBinaryString(message.length())).replace(' ', '0');

        return size + builder.toString();
    }

    /**
     * Converts the fake BLE ID to form which can be recognized by method {@link BeaconHandlerService} method
     * fetchDataAndNotify().
     * @param bleId ID of the BLE, as per the string written in the EditText.
     * @return 155 characters(bits) formatted BLE ID which can be used to search the database on server
     * @throws NumberFormatException if integer value of BLE ID is >= (2^155)
     */
    public static @NonNull String bleIdToBitString(@NonNull String bleId) {
        int integerBleId = Integer.parseInt(bleId);
        if (integerBleId >= Math.pow(2, 155)) {
            throw new NumberFormatException("BLE ID cannot be >= (2^155)");
        }
        String bleIdInBinary = Integer.toBinaryString(integerBleId);

        return String.format("%155s", bleIdInBinary).replace(' ', '0');
    }

    /**
     * Creates first 5 bits for the bit string to provide the service.
     * @param offline true if we want the bit string to be processed as offline content, false otherwise
     * @param silent true if we want to turn the silent mode on, false otherwise
     * @return byte with specific services enabled(bits turned to 1)
     */
    public static @NonNull String createFeatureListBits(boolean offline, boolean silent) {
        byte services = 0;
        if (!offline) {
            services |= 0x01; //0x01 = 00000001, turning the last bit 1
        }

        if (silent) {
            services |= 0x02; //0x02 = 00000010, turning the second last bit 1
        }

        return new StringBuilder(
                String.format("%5s", Integer.toBinaryString(services)).replace(' ', '0')
        ).reverse().toString();
    }

    public static void setAlphabet(@NonNull SparseArray<Character> a) {
        alphabet = a;
    }
}