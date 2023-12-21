package com.example.write_file_java_3;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Context;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainActivity extends AppCompatActivity {
    private EditText editText;
    private Button writeButton;
    private Button readButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editText = findViewById(R.id.editText1);
        writeButton = findViewById(R.id.button1);
        readButton = findViewById(R.id.button2);

        writeButton.setOnClickListener(new View.OnClickListener() {
            String readMsg = "A/1/2/1x2x3x4/50/11/A";
            String readMsgB = "B/130/40/B";
            int values_count = 0;
            @Override
            public void onClick(View v) {
                if (readMsg.trim().startsWith("A") && readMsg.trim().endsWith("A")) {
                    try{
                        String[] values = readMsg.trim().split("/", 0);
                        values_count = values.length;
                        Write_file(values, "data_log.csv", values_count);
                    } catch(Exception e){
                        e.printStackTrace();
                        System.out.println("An error occurred.");
                    }
                }
                if (readMsgB.trim().startsWith("B") && readMsgB.trim().endsWith("B")){
                    try{
                        String[] values = readMsgB.trim().split("/", 0);
                        values_count = values.length;
                        Write_file(values, "data_log.csv", values_count);
                    } catch(Exception e){
                        e.printStackTrace();
                        System.out.println("An error occurred.");
                    }
                }
                //String values = editText.getText().toString();
                //Write_file(values, "data_log.csv", values_count);
            }
        });

        readButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String dataRead = readFromFile("data_log.csv");
                Toast.makeText(MainActivity.this, "Data read: " + dataRead, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void Write_file(String[] arr, String filename, int n) {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
        try {
            // Check if the parent directory exists; if not, create it
            File parentDirectory = file.getParentFile();
            if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
                // Handle the case where directory creation fails
                throw new IOException("Failed to create parent directory: " + parentDirectory);
            }
            // Create or overwrite the file
            FileWriter writer = new FileWriter(file,true);
            
            //for adding time stamp to each data input
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestampedData = LocalDateTime.now().format(formatter) + ":";
            writer.write(timestampedData);
            
            // Write data to the file
            for (int i = 0; i < n-1; i++){
                writer.write(arr[i] + "/");
            }
            writer.write(arr[0] + "\n");

            // Close the FileWriter
            writer.close();
            Toast.makeText(this, "Data created or updated successfully!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to update data! >:(", Toast.LENGTH_SHORT).show();
            // Handle the exception
        }
    }
    private String readFromFile(String filename) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
            //FileInputStream fileInputStream = openFileInput("my_data.txt");
            FileInputStream fileInputStream = new FileInputStream(file);
            StringBuilder stringBuilder = new StringBuilder();
            int c;
            while ((c = fileInputStream.read()) != -1) {
                stringBuilder.append((char) c);
            }
            fileInputStream.close();
            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
}
