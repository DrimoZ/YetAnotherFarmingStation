import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public final class GenerateTestStructure {

    private static final String OUT = "src/main/resources/data/yafs/structure";

    private static final int SIZE_X = 11;
    private static final int SIZE_Y = 8;
    private static final int SIZE_Z = 11;

    private static final int DATA_VERSION = 3955;

    private static final byte TAG_END = 0;
    private static final byte TAG_INT = 3;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;

    private static final int STONE = 0;
    private static final int AIR = 1;

    public static void main(String[] args) throws IOException {
        new File(OUT).mkdirs();
        File file = new File(OUT + "/platform.nbt");

        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(new FileOutputStream(file)))) {
            out.writeByte(TAG_COMPOUND);
            out.writeUTF("");

            intTag(out, "DataVersion", DATA_VERSION);
            size(out);
            emptyList(out, "entities");
            palette(out);
            blocks(out);

            out.writeByte(TAG_END);
        }
        System.out.println("Wrote " + file + " (" + file.length() + " bytes)");
    }

    private static void size(DataOutputStream out) throws IOException {
        out.writeByte(TAG_LIST);
        out.writeUTF("size");
        out.writeByte(TAG_INT);
        out.writeInt(3);
        out.writeInt(SIZE_X);
        out.writeInt(SIZE_Y);
        out.writeInt(SIZE_Z);
    }

    private static void palette(DataOutputStream out) throws IOException {
        out.writeByte(TAG_LIST);
        out.writeUTF("palette");
        out.writeByte(TAG_COMPOUND);
        out.writeInt(2);
        for (String name : new String[]{"minecraft:stone", "minecraft:air"}) {
            out.writeByte(TAG_STRING);
            out.writeUTF("Name");
            out.writeUTF(name);
            out.writeByte(TAG_END);
        }
    }

    private static void blocks(DataOutputStream out) throws IOException {
        out.writeByte(TAG_LIST);
        out.writeUTF("blocks");
        out.writeByte(TAG_COMPOUND);
        out.writeInt(SIZE_X * SIZE_Y * SIZE_Z);

        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    out.writeByte(TAG_LIST);
                    out.writeUTF("pos");
                    out.writeByte(TAG_INT);
                    out.writeInt(3);
                    out.writeInt(x);
                    out.writeInt(y);
                    out.writeInt(z);

                    intTag(out, "state", y == 0 ? STONE : AIR);
                    out.writeByte(TAG_END);
                }
            }
        }
    }

    private static void emptyList(DataOutputStream out, String name) throws IOException {
        out.writeByte(TAG_LIST);
        out.writeUTF(name);
        out.writeByte(TAG_END);
        out.writeInt(0);
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(TAG_INT);
        out.writeUTF(name);
        out.writeInt(value);
    }

    private GenerateTestStructure() {}
}
