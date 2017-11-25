package com.athena.tools;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.Log;
import org.apache.parquet.Preconditions;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.ParquetEncodingException;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.RecordConsumer;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class ConvertUtils {

    private static final Log LOG = Log.getLog(ConvertUtils.class);

    public static final String CSV_DELIMITER= ",";

    private static String readFile(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        StringBuilder stringBuilder = new StringBuilder();

        try {
            String line = null;
            String ls = System.getProperty("line.separator");

            while ((line = reader.readLine()) != null ) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }
        } finally {
            reader.close();
        }

        return stringBuilder.toString();
    }

    public static String getSchema(File csvFile) throws IOException {
//        String fileName = csvFile.getName().substring(0, csvFile.getName().length() - ".csv".length()) + ".schema";
        String fileName = "parquet.schema";
        File schemaFile = new File(csvFile.getParentFile(), fileName);
        return readFile(schemaFile.getAbsolutePath());
    }

    public static void convertCsvToParquet(File csvFile, File outputParquetFile) throws IOException {
        convertCsvToParquet(csvFile, outputParquetFile, false);
    }

    public static void convertCsvToParquet(File csvFile, File outputParquetFile, boolean enableDictionary) throws IOException {
        LOG.info("Converting " + csvFile.getName() + " to " + outputParquetFile.getName());
        String rawSchema = getSchema(csvFile);
        if(outputParquetFile.exists()) {
            throw new IOException("Output file " + outputParquetFile.getAbsolutePath() +
                    " already exists");
        }

        Path path = new Path(outputParquetFile.toURI());

        MessageType schema = MessageTypeParser.parseMessageType(rawSchema);
        CsvParquetWriter writer = new CsvParquetWriter(path, schema, enableDictionary);

        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        String line;
        int lineNumber = 0;
        try {
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(Pattern.quote(CSV_DELIMITER));
                writer.write(Arrays.asList(fields));
                ++lineNumber;
            }

            writer.close();
        } finally {
            LOG.info("Number of lines: " + lineNumber);
            br.close();
        }
    }

    public static void convertParquetToCSV(File parquetFile, File csvOutputFile) throws IOException {
        Preconditions.checkArgument(parquetFile.getName().endsWith(".parquet"),
                "parquet file should have .parquet extension");
        Preconditions.checkArgument(csvOutputFile.getName().endsWith(".csv"),
                "csv file should have .csv extension");
        Preconditions.checkArgument(!csvOutputFile.exists(),
                "Output file " + csvOutputFile.getAbsolutePath() + " already exists");

        LOG.info("Converting " + parquetFile.getName() + " to " + csvOutputFile.getName());


        Path parquetFilePath = new Path(parquetFile.toURI());

        Configuration configuration = new Configuration(true);

        GroupReadSupport readSupport = new GroupReadSupport();
        ParquetMetadata readFooter = ParquetFileReader.readFooter(configuration, parquetFilePath);
        MessageType schema = readFooter.getFileMetaData().getSchema();

        readSupport.init(configuration, null, schema);
        ParquetReader<Group> reader = new ParquetReader<Group>(parquetFilePath, readSupport);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(csvOutputFile))) {
            Group g = null;
            while ((g = reader.read()) != null) {
                writeGroup(w, g, schema);
            }
            reader.close();
        }
    }

    private static void writeGroup(BufferedWriter w, Group g, MessageType schema)
            throws IOException{
        for (int j = 0; j < schema.getFieldCount(); j++) {
            if (j > 0) {
                w.write(CSV_DELIMITER);
            }
            String valueToString = g.getValueToString(j, 0);
            w.write(valueToString);
        }
        w.write('\n');
    }

    @Deprecated
    public static void convertParquetToCSVEx(File parquetFile, File csvOutputFile) throws IOException {
        Preconditions.checkArgument(parquetFile.getName().endsWith(".parquet"),
                "parquet file should have .parquet extension");
        Preconditions.checkArgument(csvOutputFile.getName().endsWith(".csv"),
                "csv file should have .csv extension");
        Preconditions.checkArgument(!csvOutputFile.exists(),
                "Output file " + csvOutputFile.getAbsolutePath() + " already exists");

        LOG.info("Converting " + parquetFile.getName() + " to " + csvOutputFile.getName());

        Path parquetFilePath = new Path(parquetFile.toURI());

        Configuration configuration = new Configuration(true);

        // TODO Following can be changed by using ParquetReader instead of ParquetFileReader
        ParquetMetadata readFooter = ParquetFileReader.readFooter(configuration, parquetFilePath);
        MessageType schema = readFooter.getFileMetaData().getSchema();
        PageReadStore pages = null;
        try (ParquetFileReader parquetFileReader = new ParquetFileReader(
                configuration, parquetFilePath, readFooter.getBlocks(), schema.getColumns()); BufferedWriter w = new BufferedWriter(new FileWriter(csvOutputFile))) {
            while (null != (pages = parquetFileReader.readNextRowGroup())) {
                final long rows = pages.getRowCount();
                LOG.info("Number of rows: " + rows);

                final MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                final RecordReader<Group> recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema));
                for (int i = 0; i < rows; i++) {
                    final Group g = recordReader.read();
                    writeGroup(w, g, schema);
                }
            }
        }
    }



    public static class CsvParquetWriter extends ParquetWriter<List<String>> {

        public CsvParquetWriter(Path file, MessageType schema) throws IOException {
            this(file, schema, false);
        }

        public CsvParquetWriter(Path file, MessageType schema, boolean enableDictionary) throws IOException {
            this(file, schema, CompressionCodecName.UNCOMPRESSED, enableDictionary);
        }

        public CsvParquetWriter(Path file, MessageType schema, CompressionCodecName codecName, boolean enableDictionary) throws IOException {
            super(file, (WriteSupport<List<String>>) new CsvWriteSupport(schema), codecName, DEFAULT_BLOCK_SIZE, DEFAULT_PAGE_SIZE, enableDictionary, false);
        }
    }

    public static class CsvWriteSupport extends WriteSupport<List<String>> {
        MessageType schema;
        RecordConsumer recordConsumer;
        List<ColumnDescriptor> cols;

        // TODO: support specifying encodings and compression
        public CsvWriteSupport(MessageType schema) {
            this.schema = schema;
            this.cols = schema.getColumns();
        }

        @Override
        public WriteContext init(Configuration config) {
            return new WriteContext(schema, new HashMap<String, String>());
        }

        @Override
        public void prepareForWrite(RecordConsumer r) {
            recordConsumer = r;
        }

        @Override
        public void write(List<String> values) {
            if (values.size() != cols.size()) {
                throw new ParquetEncodingException("Invalid input data. Expecting " +
                        cols.size() + " columns. Input had " + values.size() + " columns (" + cols + ") : " + values);
            }

            recordConsumer.startMessage();
            for (int i = 0; i < cols.size(); ++i) {
                String val = values.get(i);
                // val.length() == 0 indicates a NULL value.
                if (val.length() > 0) {
                    recordConsumer.startField(cols.get(i).getPath()[0], i);
                    switch (cols.get(i).getType()) {
                        case BOOLEAN:
                            recordConsumer.addBoolean(Boolean.parseBoolean(val));
                            break;
                        case FLOAT:
                            recordConsumer.addFloat(Float.parseFloat(val));
                            break;
                        case DOUBLE:
                            recordConsumer.addDouble(Double.parseDouble(val));
                            break;
                        case INT32:
                            recordConsumer.addInteger(Integer.parseInt(val));
                            break;
                        case INT64:
                            recordConsumer.addLong(Long.parseLong(val));
                            break;
                        case BINARY:
                            recordConsumer.addBinary(stringToBinary(val));
                            break;
                        default:
                            throw new ParquetEncodingException(
                                    "Unsupported column type: " + cols.get(i).getType());
                    }
                    recordConsumer.endField(cols.get(i).getPath()[0], i);
                }
            }
            recordConsumer.endMessage();
        }

        private Binary stringToBinary(Object value) {
            return Binary.fromString(value.toString());
        }
    }

}