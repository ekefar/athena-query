package com.athena;

import com.athena.tools.ConvertUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Random;

/**
 * @author : Alexander Serebriyan
 */
public class Application {

    public static void main(String[] args) {
        testCSV(10000);
    }

    private static void testCSV(long rowsNumber) {

        final long nameWithoutExtension = System.currentTimeMillis();
        File csv = new File(nameWithoutExtension + ".csv");
        BufferedWriter writer = null;
        try {

            writer = new BufferedWriter(new FileWriter(csv));

            long i = 0;

            while (i < rowsNumber) {
                i++;
                final double mz = BigDecimal.valueOf(new Random().nextDouble()).setScale(5, BigDecimal.ROUND_DOWN).doubleValue();
                final double rt = BigDecimal.valueOf(new Random().nextDouble()).setScale(5,BigDecimal.ROUND_DOWN).doubleValue();
                final double x = BigDecimal.valueOf(new Random().nextDouble()).setScale(5,BigDecimal.ROUND_DOWN).doubleValue();
                final double y = BigDecimal.valueOf(new Random().nextDouble()).setScale(5,BigDecimal.ROUND_DOWN).doubleValue();
                final double charge = BigDecimal.valueOf(new Random().nextDouble()).setScale(5,BigDecimal.ROUND_DOWN).doubleValue();
                final String[] row = {
                        String.valueOf(i),
                        String.valueOf(mz),
                        String.valueOf(rt),
                        String.valueOf(charge),
                        String.valueOf(x),
                        String.valueOf(y)
                };

                writer.write(String.join(",", row));
                writer.newLine();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // Close the writer regardless of what happens...
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        try {
            ConvertUtils.convertCsvToParquet(csv, new File(nameWithoutExtension + ".par"));
            csv.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
