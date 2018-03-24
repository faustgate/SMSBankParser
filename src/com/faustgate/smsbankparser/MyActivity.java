package com.faustgate.smsbankparser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MyActivity extends Activity {
    /**
     * Called when the activity is first created.
     */

    Map<String, ArrayList<Map<String, String>>> report = new HashMap<>();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Uri uriSms = Uri.parse("content://sms/inbox");
        Cursor cursor = getApplicationContext().getContentResolver().query(uriSms, null, null, null, "date");
        if (cursor.moveToFirst()) { // must check the result to prevent exception
            do {
                String message = cursor.getString(13);
                String sender  = cursor.getString(2);
                switch (sender) {
                    case "BANKVOSTOK": ParseVostok(message); break;
                    case "Oschadbank": ParseOschad(message); break;
                    case "10901":      ParseAval(message);   break;
                }
            } while (cursor.moveToNext());
        }
        ArrayList<String> foundItems = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Map<String, String>>> entry : report.entrySet()) {
            String key = entry.getKey();
            ArrayList<Map<String, String>> value = entry.getValue();
            WriteCVS(key.trim().replace(")", "").replaceAll("[^A-Za-z0-9]", "-"), value);
            foundItems.add(key);
        }
        Collections.sort(foundItems);
        showReport(foundItems);

    }

    private void ParseAval(String message) {
        if ((!message.contains("vidkhyl") && !message.contains("nemozhlyva")) && (message.contains("zarakhovano") || message.contains("suma") || message.contains("zalyshok"))) {
            String[] msg;
            String currentCardNum = "";

            Map<String, String> data = new HashMap<>();
            String[] elements = message.split(" ");

            if (message.contains("zarakhovano")) {
                currentCardNum = message.split(" ")[5];
                data.put("date", elements[0]);
                data.put("payee", "Incoming transfer");
                data.put("sum", elements[elements.length - 2]);
            } else {
                if (message.contains("dostupna")) {
                    msg = message.replaceFirst("^.*: \\d{2}.\\d{2}.\\d{4} \\d{2}:\\d{2}:\\d{2} ", "").split(" ");
                    currentCardNum = MessageFormat.format("{0}_{1}", msg[0], msg[1]).replace("/", "_");
                    data.put("date", elements[2]);

                    data.put("sum", "-" + elements[6]);

                    String payeeName = "";
                    for (int i = 8; i < elements.length - 4; i++) {
                        payeeName += elements[i] + " ";
                    }
                    data.put("payee", payeeName.trim());
                    data.put("left", elements[elements.length - 2]);
                } else {
                    currentCardNum = elements[elements.length - 9];
                    data.put("date", elements[0]);
                    data.put("sum", "-" + elements[elements.length - 6]);

                    String payeeName = "";
                    for (int i = 2; i < elements.length - 9; i++) {
                        payeeName += elements[i] + " ";
                    }
                    data.put("payee", payeeName.trim());
                    data.put("left", elements[elements.length - 2]);
                }
            }
            String key = MessageFormat.format("{0}_{1}", "Aval", currentCardNum);
            if (!report.containsKey(key)) {
                report.put(key, new ArrayList<>());
            }
            report.get(key).add(data);
        }
    }

    private void ParseVostok(String message) {

        String[] reportLines= message.split("\n");
        String payee;
        String sum = "";
        String currency = "";
        String left_sum = "";
        String left_currency = "";
        String date;
        String paymentDetails;
        String currentCardNum;
        String key;
        Map<String, String> data = new HashMap<>();

        if (message.contains("Card:") && (!message.contains("OTKAZ"))) {
            currentCardNum = reportLines[0].replaceFirst(".*Card:", "").replace(".", "");
            key = MessageFormat.format("{0}_{1}", "BankVostok", currentCardNum.trim());
            if (!report.containsKey(key)) {
                report.put(key, new ArrayList<>());
            }
            try {
                date            = reportLines[0].substring(5, 10).replace("/", ".") + ".2017";
                paymentDetails  = reportLines[1].replace("Sum=", "");
                if (paymentDetails.contains("Popolnenie")) {
                    payee    = "Incoming transfer";
                    sum      = reportLines[1].split(" ")[1].trim();
                    currency = reportLines[1].split(" ")[2].trim();
                } else {
                    int sumDetailsDelimiterPosition = paymentDetails.indexOf(" ", paymentDetails.indexOf(" ") + 1);

                    sum = paymentDetails.substring(0, paymentDetails.indexOf(" ")).trim();
                    if (sum.startsWith("-")) {
                        sum = sum.substring(1);
                    } else {
                        sum = "-" + sum;
                    }
                    currency = paymentDetails.substring(paymentDetails.indexOf(" "), sumDetailsDelimiterPosition).trim();
                    String data1 = paymentDetails.substring(sumDetailsDelimiterPosition).replace("(", "").replace(")", "").trim();
                    payee = data1;
                }
                data.put("date", date);
                data.put("sum", sum);
                data.put("comment", currency);
                data.put("payee", payee);
                data.put("left", reportLines[2].substring(8, reportLines[2].length() - 4));
            } catch (Exception e) {
                e.printStackTrace();
            }
            report.get(key).add(data);
        }
        if (message.contains("Popovnennia") || message.contains("Oplata") || message.contains("Zniattia") ){
            date            = reportLines[3].split(" ")[0].replace("/", ".") + ".2017";
            currentCardNum  = reportLines[0].split(" - ")[0];
            key = MessageFormat.format("{0}_{1}", "BankVostok", currentCardNum.trim());
            if (!report.containsKey(key)) {
                report.put(key, new ArrayList<>());
            }

            if (message.contains("Popovnennia")){
                payee    = "Incoming transfer";
                sum=reportLines[1].split(" ")[0];
                left_sum      = reportLines[2].replace("Dostupno ", "").split(" ")[0];
                left_currency = reportLines[2].replace("Dostupno ", "").split(" ")[1];
            } else {

                paymentDetails = reportLines[1];
                int payeeStart = paymentDetails.indexOf('(');
                int payeeStop  = paymentDetails.lastIndexOf(')');

                String payment = paymentDetails.substring(0, payeeStart);
                payee          = paymentDetails.substring(payeeStart + 1, payeeStop);

                sum = "-" + payment.split(" ")[0];
                currency  = payment.split(" ")[1];

                left_sum      = reportLines[2].replace("Dostupno ", "").split(" ")[0];
                left_currency = reportLines[2].replace("Dostupno ", "").split(" ")[1];
            }
            data.put("date", date);
            data.put("sum", sum);
            data.put("comment", currency);
            data.put("payee", payee);
            data.put("left", left_sum);

            report.get(key).add(data);

        }

    }

    private void ParseOschad(String message) {
        if (message.contains("Balance=") && (!message.contains("VIDHYLENO"))) {
            Map<String, String> data = new HashMap<>();

            String[] reportLines = message.split("\n");
            String payee;
            String sum;
            String currency;

            String date = reportLines[0];
            String[] paymentDetails = reportLines[2].replace("Sum: ", "").split(" ");

            sum = paymentDetails[0];
            currency = paymentDetails[1].trim();

            if (paymentDetails[0].contains("-")) {
                payee = reportLines[3].replace("M=", "");
            } else {
                payee = "Incoming transfer";
            }

            data.put("date", date);
            data.put("sum", sum);
            data.put("comment", currency);
            data.put("payee", payee);
            data.put("left", reportLines[4].substring(8, reportLines[4].length() - 4));

            String currentCardNum = reportLines[1].replaceFirst(".*Card:", "").replace(".", "");
            String key = MessageFormat.format("{0}_{1}", "Oschadbank", currentCardNum);
            if (!report.containsKey(key)) {
                report.put(key, new ArrayList<>());
            }
            report.get(key).add(data);
        }
    }

    private void WriteCVS(String file_name, ArrayList<Map<String, String>> data) {

        String[] headers = new String[]{"date", "payee", "sum", "left", "comment"};


        File folder = new File(Environment.getExternalStorageDirectory() + "/Folder");

        boolean var = false;
        if (!folder.exists()) {
            var = folder.mkdir();
        }

        System.out.println("" + var);


        final String filename = folder.toString() + "/" + file_name + ".csv";

        try {

            FileWriter fw = new FileWriter(filename);

            for (String header : headers) {
                fw.append(header);
                fw.append(',');
            }
            fw.append('\n');

            for (Map<String, String> val : data) {
                StringBuilder string = new StringBuilder();
                for (String header : headers) {
                    if (val.containsKey(header)) {
                        String el = val.get(header);
                        if (el.contains(",")) {
                            string.append("\"").append(el).append("\"");
                        } else {
                            string.append(el);
                        }
                    }
                    string.append(",");
                }
                fw.append(string.toString().replaceAll("^,+", "").replaceAll(",+$", ""));
                fw.append('\n');
            }
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void showReport(ArrayList<String> report) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(MyActivity.this);
        builderSingle.setIcon(R.drawable.ic_launcher);
        builderSingle.setTitle("Processed items");

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(MyActivity.this, android.R.layout.preference_category);
        for (String line : report) {
            arrayAdapter.add(line);
        }

        builderSingle.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builderSingle.show();
    }


}
