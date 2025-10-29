import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import db.BinaryField;
import db.BooleanField;
import db.ByteField;
import db.DBBuffer;
import db.DBHandle;
import db.DBRecord;
import db.Field;
import db.IntField;
import db.LongField;
import db.RecordIterator;
import db.Schema;
import db.ShortField;
import db.StringField;
import db.Table;
import db.buffers.LocalBufferFile;

/*
TODO:
pass through log methods to be callable from java
*/

// 0 column in schema is the key

@CContext(Crimes.CrimesDirectives.class)
public class Crimes {

    static class CrimesDirectives implements CContext.Directives {
        @Override
        public List<String> getHeaderFiles() {
            File headerFile = new File("../../../../../java-bindings.h");
            return Collections.singletonList("\"" + headerFile.getAbsolutePath() + "\"");
        }
    }

    @CEnum("ghidra_field_type_t")
    enum GhidraFieldType {
        BYTE_TYPE,
        SHORT_TYPE,
        INT_TYPE,
        LONG_TYPE,
        STRING_TYPE,
        BINARY_OBJ_TYPE,
        BOOLEAN_TYPE;

        @CEnumValue
        public native int getCValue();

        @CEnumLookup
        public static native GhidraFieldType fromCValue(int value);
    }

    @CStruct("ghidra_field_t")
    interface CGhidraFieldPointer extends PointerBase {
        CGhidraFieldPointer addressOf(int index);

        @CField("type")
        int fieldType();
        @CField("type")
        void fieldType(int fieldType);

        @CField("boolean_value")
        boolean booleanValue();
        @CField("boolean_value")
        void booleanValue(boolean booleanVal);

        @CField("byte_value")
        byte byteValue();
        @CField("byte_value")
        void byteValue(byte byteVal);

        @CField("short_value")
        short shortValue();
        @CField("short_value")
        void shortValue(short shortVal);

        @CField("int_value")
        int intValue();
        @CField("int_value")
        void intValue(int intVal);

        @CField("long_value")
        long longValue();
        @CField("long_value")
        void longValue(long longVal);

        @CField("string_value")
        CCharPointer stringValue();
        @CField("string_value")
        void stringValue(CCharPointer stringVal);

        @CField("binary_value")
        CCharPointer binaryValue();
        @CField("binary_value")
        void binaryValue(CCharPointer binaryVal);

        // Used for string and binary types
        @CField("value_length")
        long valueLength();
        @CField("value_length")
        void valueLength(long valueLength);
    }

    @CPointerTo(CGhidraFieldPointer.class)
    interface CGhidraFieldPointerPointer extends PointerBase {
        CGhidraFieldPointer read();
        CGhidraFieldPointer read(int index);
        void write(CGhidraFieldPointer value);
        void write(int index, CGhidraFieldPointer value);
    }

    @CStruct("ghidra_record_t")
    interface CGhidraRecordPointer extends PointerBase {
        CGhidraRecordPointer addressOf(int index);

        @CField("fields")
        CGhidraFieldPointer fields();
        @CField("fields")
        void fields(CGhidraFieldPointer fields);

        @CField("field_count")
        int fieldCount();
        @CField("field_count")
        void fieldCount(int fieldCount);
    }

    @CPointerTo(CGhidraRecordPointer.class)
    interface CGhidraRecordPointerPointer extends PointerBase {
        CGhidraRecordPointer read();
        CGhidraRecordPointer read(int index);
        void write(CGhidraRecordPointer value);
        void write(int index, CGhidraRecordPointer value);
    }

    static final byte BYTE_TYPE = 0;
    static final byte SHORT_TYPE = 1;
    static final byte INT_TYPE = 2;
    static final byte LONG_TYPE = 3;
    static final byte STRING_TYPE = 4;
    static final byte BINARY_OBJ_TYPE = 5;
    static final byte BOOLEAN_TYPE = 6;

    private static HashMap<Integer, DBHandle> g_dbInstances = new HashMap<>();
    private static int g_nextId = 0;

    private static DBHandle getDbHandleFromId(int dbId) throws Exception {
        return getDbHandleFromId(dbId, true);
    }

    private static DBHandle getDbHandleFromId(int dbId, boolean checkOpen) throws Exception {
        DBHandle handle = g_dbInstances.get(dbId);

        if (handle == null) {
            throw new Exception("Failed to get db handle with id " + dbId);
        }

        if (checkOpen && handle.isClosed()) {
            throw new Exception("DB handle with id " + dbId + " is closed");
        }

        return handle;
    }

    private static int getFieldTypeAsInt(Field field) throws Exception {
        if (field instanceof ByteField) {
            return BYTE_TYPE;
        }
        else if (field instanceof BooleanField) {
            return BOOLEAN_TYPE;
        }
        else if (field instanceof ShortField) {
            return SHORT_TYPE;
        }
        else if (field instanceof IntField) {
            return INT_TYPE;
        }
        else if (field instanceof LongField) {
            return LONG_TYPE;
        }
        else if (field instanceof StringField) {
            return STRING_TYPE;
        }
        else if (field instanceof BinaryField) {
            return BINARY_OBJ_TYPE;
        }
        throw new Exception("Unexpected DB column type: " + field.getClass().getSimpleName());
    }

    @CEntryPoint(name="FFI_open_db_handle")
    public static boolean openDbHandle(@CEntryPoint.IsolateThreadContext long isolateId, CCharPointer cFileName, CIntPointer dbId) {
        final String fileName = CTypeConversion.toJavaString(cFileName);
        File selectedFile = new File(fileName);
        DBHandle handle;
        try {
            LocalBufferFile bf = new LocalBufferFile(selectedFile, true);
            handle = new DBHandle(bf);
        }
        catch (Exception e) {
            // Trying to open a PackedDatabase throws an IOException but we don't support those (yet?)
            e.printStackTrace();
            return false;
        }
        dbId.write(g_nextId);
        g_dbInstances.put(g_nextId++, handle);
        return true;
    }

    @CEntryPoint(name="FFI_db_handle_is_open")
    public static boolean isDbHandleOpen(@CEntryPoint.IsolateThreadContext long isolateId, int dbId) {
        try {
            DBHandle handle = getDbHandleFromId(dbId, false);
            return !handle.isClosed();
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_close_db_handle")
    public static boolean closeDbHandle(@CEntryPoint.IsolateThreadContext long isolateId, int dbId) {
        try {
            DBHandle handle = getDbHandleFromId(dbId);
            handle.close();
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_table_names")
    public static CCharPointerPointer getTableNames(@CEntryPoint.IsolateThreadContext long isolateId, int dbId, CIntPointer count) {
        try {
            DBHandle handle = getDbHandleFromId(dbId);
            Table[] tables = handle.getTables();

            CCharPointerPointer result = UnmanagedMemory.calloc(SizeOf.get(CCharPointerPointer.class)*tables.length);
            for (int i = 0; i < tables.length; i++) {
                result.write(i, Util.allocString(tables[i].getName()).buf);
            }

            count.write(tables.length);

            return result;
        }
        catch (Exception e) {
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name="FFI_get_table_schema_version")
    public static boolean get_table_schema_version(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, CCharPointer cTableName, CIntPointer version) {
        try {
            final String tableName = CTypeConversion.toJavaString(cTableName);

            DBHandle handle = getDbHandleFromId(db_id);
            Table table = handle.getTable(tableName);
            if (table == null) {
                throw new Exception("Failed to find table \"" + tableName + "\"");
            }

            Schema schema = table.getSchema();
            if (schema == null) {
                throw new Exception("Schema for table \"" + tableName + "\" is null");
            }

            version.write(schema.getVersion());
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_table_col_count")
    public static boolean get_table_col_count(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, CCharPointer cTableName, CIntPointer count) {
        try {
            final String tableName = CTypeConversion.toJavaString(cTableName);

            DBHandle handle = getDbHandleFromId(db_id);
            Table table = handle.getTable(tableName);
            if (table == null) {
                throw new Exception("Failed to find table \"" + tableName + "\"");
            }

            Schema schema = table.getSchema();
            if (schema == null) {
                throw new Exception("Schema for table \"" + tableName + "\" is null");
            }

            // Add one for key column
            count.write(schema.getFieldCount() + 1);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_table_col_type")
    public static boolean get_table_col_type(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, CCharPointer cTableName, int col, CIntPointer type) {
        try {
            final String tableName = CTypeConversion.toJavaString(cTableName);

            DBHandle handle = getDbHandleFromId(db_id);
            Table table = handle.getTable(tableName);
            if (table == null) {
                throw new Exception("Failed to find table \"" + tableName + "\"");
            }

            Schema schema = table.getSchema();
            if (schema == null) {
                throw new Exception("Schema for table \"" + tableName + "\" is null");
            }

            Field field = (col == 0) ? schema.getKeyFieldType() : schema.getFields()[col-1];

            type.write(getFieldTypeAsInt(field));

            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_table_col_name")
    public static boolean get_table_col_name(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, CCharPointer cTableName, int col, CCharPointerPointer name) {
        try {
            final String tableName = CTypeConversion.toJavaString(cTableName);

            DBHandle handle = getDbHandleFromId(db_id);
            Table table = handle.getTable(tableName);
            if (table == null) {
                throw new Exception("Failed to find table \"" + tableName + "\"");
            }

            Schema schema = table.getSchema();
            if (schema == null) {
                throw new Exception("Schema for table \"" + tableName + "\" is null");
            }

            String fieldName = (col == 0) ? schema.getKeyName() : schema.getFieldNames()[col-1];
            name.write(Util.allocString(fieldName).buf);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_table_row_count")
    public static boolean get_table_row_count(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, CCharPointer cTableName, CIntPointer count) {
        try {
            final String tableName = CTypeConversion.toJavaString(cTableName);

            DBHandle handle = getDbHandleFromId(db_id);
            Table table = handle.getTable(tableName);
            if (table == null) {
                throw new Exception("Failed to find table \"" + tableName + "\"");
            }

            Schema schema = table.getSchema();
            if (schema == null) {
                throw new Exception("Schema for table \"" + tableName + "\" is null");
            }

            count.write(table.getRecordCount());
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_table_records")
    public static boolean get_table_records(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, CCharPointer cTableName, CGhidraRecordPointerPointer records, CIntPointer count) {
        records.write(WordFactory.nullPointer());
        count.write(0);

        try {
            final String tableName = CTypeConversion.toJavaString(cTableName);

            DBHandle handle = getDbHandleFromId(db_id);
            Table table = handle.getTable(tableName);
            if (table == null) {
                throw new Exception("Failed to find table \"" + tableName + "\"");
            }

            Schema schema = table.getSchema();
            if (schema == null) {
                throw new Exception("Schema for table \"" + tableName + "\" is null");
            }

            RecordIterator iter = table.iterator();
            int recordCount = 0;
            List<DBRecord> dbRecords = new ArrayList<>();

            while (iter.hasNext()) {
                dbRecords.add(iter.next());
                recordCount++;
            }

            count.write(recordCount);
            CGhidraRecordPointer recordArray = UnmanagedMemory.calloc(SizeOf.get(CGhidraRecordPointer.class) * recordCount);
            records.write(recordArray);

            for (int recordNum = 0; recordNum < recordCount; recordNum++) {
                DBRecord currRecord = dbRecords.get(recordNum);
                CGhidraRecordPointer cRecord = recordArray.addressOf(recordNum);

                int fieldCount = currRecord.getColumnCount()+1;
                CGhidraFieldPointer fieldArray = UnmanagedMemory.calloc(SizeOf.get(CGhidraFieldPointer.class) * fieldCount);
                cRecord.fields(fieldArray);
                cRecord.fieldCount(fieldCount);

                for (int fieldNum = 0; fieldNum < fieldCount; fieldNum++) {
                    CGhidraFieldPointer cField = fieldArray.addressOf(fieldNum);
                    Field field = (fieldNum == 0) ? currRecord.getKeyField() : currRecord.getFieldValue(fieldNum-1);

                    if (field instanceof ByteField) {
                        cField.fieldType(GhidraFieldType.BYTE_TYPE.getCValue());
                        cField.byteValue(field.getByteValue());
                    }
                    else if (field instanceof BooleanField) {
                        cField.fieldType(GhidraFieldType.BOOLEAN_TYPE.getCValue());
                        cField.booleanValue(field.getBooleanValue());
                    }
                    else if (field instanceof ShortField) {
                        cField.fieldType(GhidraFieldType.SHORT_TYPE.getCValue());
                        cField.shortValue(field.getShortValue());
                    }
                    else if (field instanceof IntField) {
                        cField.fieldType(GhidraFieldType.INT_TYPE.getCValue());
                        cField.intValue(field.getIntValue());
                    }
                    else if (field instanceof LongField) {
                        cField.fieldType(GhidraFieldType.LONG_TYPE.getCValue());
                        cField.longValue(field.getLongValue());
                    }
                    else if (field instanceof StringField) {
                        cField.fieldType(GhidraFieldType.STRING_TYPE.getCValue());
                        String stringVal = field.getString();
                        if (stringVal != null) {
                            Util.AllocatedString s = Util.allocString(stringVal);
                            cField.stringValue(s.buf);
                            cField.valueLength(s.bufLen);
                        }
                    }
                    else if (field instanceof BinaryField) {
                        cField.fieldType(GhidraFieldType.BINARY_OBJ_TYPE.getCValue());
                        byte[] binaryVal = field.getBinaryData();
                        if (binaryVal != null) {
                            CCharPointer buf = UnmanagedMemory.calloc(binaryVal.length);
                            for (int byteNum = 0; byteNum < binaryVal.length; byteNum++) {
                                buf.write(byteNum, binaryVal[byteNum]);
                            }
                            cField.binaryValue(buf);
                            cField.valueLength(binaryVal.length);
                        }
                    }
                    else {
                        throw new Exception("Unknown field type " + field.toString());
                    }
                }
            }

            return true;
        }
        catch (Exception e) {
            free_records(isolateId, records.read(), count.read());
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_buffer")
    public static boolean get_buffer(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, int bufferId, CCharPointerPointer bufferData, CIntPointer bufferSize) {
        try {
            DBHandle handle = getDbHandleFromId(db_id);
            DBBuffer buffer = handle.getBuffer(bufferId);
            if (buffer == null) {
                throw new Exception("Could not find buffer " + bufferId);
            }
            bufferSize.write(buffer.length());
            bufferData.write(Util.allocBuffer(buffer).buf);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_get_buffer_shadowed")
    public static boolean get_buffer(@CEntryPoint.IsolateThreadContext long isolateId, int db_id, int bufferId, int shadowBufferId, CCharPointerPointer bufferData, CIntPointer bufferSize) {
        try {
            DBHandle handle = getDbHandleFromId(db_id);
            DBBuffer shadowBuffer = handle.getBuffer(shadowBufferId);
            if (shadowBuffer == null) {
                throw new Exception("Could not find buffer " + bufferId);
            }

            DBBuffer buffer = handle.getBuffer(bufferId, shadowBuffer);
            if (buffer == null) {
                throw new Exception("Could not find buffer " + bufferId);
            }
            bufferSize.write(buffer.length());
            bufferData.write(Util.allocBuffer(buffer).buf);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_free_records")
    public static boolean free_records(@CEntryPoint.IsolateThreadContext long isolateId, CGhidraRecordPointer recordArray, int count) {
        try {
            for (int i = 0; i < count; i++)
            {
                CGhidraRecordPointer curr = recordArray.addressOf(i);
                if (!free_fields(isolateId, curr.fields(), curr.fieldCount()))
                {
                    return false;
                }
            }
            UnmanagedMemory.free(recordArray);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_free_fields")
    public static boolean free_fields(@CEntryPoint.IsolateThreadContext long isolateId, CGhidraFieldPointer fieldArray, int count) {
        try {
            for (int i = 0; i < count; i++)
            {
                CGhidraFieldPointer curr = fieldArray.addressOf(i);
                UnmanagedMemory.free(curr.stringValue());
                UnmanagedMemory.free(curr.binaryValue());
            }
            UnmanagedMemory.free(fieldArray);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CEntryPoint(name="FFI_free_string")
    public static void free_string(@CEntryPoint.IsolateThreadContext long isolateId, CCharPointer p) {
        UnmanagedMemory.free(p);
    }

    @CEntryPoint(name="FFI_free_string_list")
    public static void free_string_list(@CEntryPoint.IsolateThreadContext long isolateId, CCharPointerPointer p, int count) {
        for (int i = 0; i < count; i++)
        {
            UnmanagedMemory.free(p.read(i));
        }
        UnmanagedMemory.free(p);
    }

}
