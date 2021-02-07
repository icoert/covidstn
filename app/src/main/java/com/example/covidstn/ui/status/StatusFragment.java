package com.example.covidstn.ui.status;

import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.example.covidstn.CovidData;
import com.example.covidstn.CurrentDayStats;
import com.example.covidstn.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.utils.Utils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;


public class StatusFragment extends Fragment {

    private static final String SET_LABEL = "Evolutie cazuri in ultima saptamana";

    private View view;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Utils.init(getContext());
        this.view = inflater.inflate(R.layout.fragment_status, container,false);

        addDatabaseListener();

        //doRefreshData();

        //initializeChart();

        return this.view;
    }

    private void addDatabaseListener() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference covidDataReference = database.getReference("covidData");

        ValueEventListener postListener = new ValueEventListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get CovidData object and use the values to update the UI
                String value = dataSnapshot.getValue(String.class);
                CovidData data = new Gson().fromJson(value, CovidData.class);

                populateForm(data);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w("", "loadPost:onCancelled", databaseError.toException());
            }
        };

        covidDataReference.addValueEventListener(postListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void populateForm(CovidData data) {
        // Total numbers
        TextView totalCasesNo = (TextView) view.findViewById(R.id.totalCasesNo);
        totalCasesNo.setText(String.valueOf(data.currentDayStats.numberInfected));

        TextView totalCureCasesNo = (TextView) view.findViewById(R.id.totalCureCasesNo);
        totalCureCasesNo.setText(String.valueOf(data.currentDayStats.numberCured));

        TextView totalDeathsNo = (TextView) view.findViewById(R.id.totalDeathsNo);
        totalDeathsNo.setText(String.valueOf(data.currentDayStats.numberDeceased));

        // Daily Cases
        TextView dailyCasesTitle = (TextView) view.findViewById(R.id.dailyCasesTitle);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate date = LocalDate.parse(data.currentDayStats.parsedOnString, formatter);
        dailyCasesTitle.setText(date.format(newFormatter));

        CurrentDayStats yesterdayStats = data.historicalData.get(date.plusDays(-1).format(formatter));

        TextView dailyCasesNo = (TextView) view.findViewById(R.id.dailyCasesNo);
        dailyCasesNo.setText(String.valueOf(data.currentDayStats.numberInfected - yesterdayStats.numberInfected));

        TextView dailyDeathsNo = (TextView) view.findViewById(R.id.dailyDeathsNo);
        dailyDeathsNo.setText(String.valueOf(data.currentDayStats.numberDeceased - yesterdayStats.numberDeceased));

        initializeChart(date, data.historicalData);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private int getOnlyNewCases(CurrentDayStats stats, Map<String, CurrentDayStats> historicalData) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate localDate = LocalDate.parse(stats.parsedOnString, formatter);

        CurrentDayStats yesterdayStats = historicalData.get(localDate.plusDays(-1).format(formatter));

        return stats.numberInfected - yesterdayStats.numberInfected;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initializeChart(LocalDate date, Map<String, CurrentDayStats> historicalData) {
        BarChart chart = (BarChart) this.view.findViewById(R.id.statusChart);
        BarData data = createChartData(date, historicalData);
        configureChartAppearance(chart);
        prepareChartData(chart, data);
    }

    private void configureChartAppearance(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setDrawValueAboveBar(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public String getFormattedValue(float value) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
                LocalDateTime currentDate = LocalDateTime.now();
                LocalDateTime dayToShow = currentDate.plusDays((int) value - 8);
                return dayToShow.format(formatter);
            }
        });

        YAxis axisLeft = chart.getAxisLeft();
        axisLeft.setGranularity(1f);
        axisLeft.setAxisMinimum(0);

        YAxis axisRight = chart.getAxisRight();
        axisRight.setGranularity(1f);
        axisRight.setAxisMinimum(0);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private BarData createChartData(LocalDate todayDate, Map<String, CurrentDayStats> historicalData) {
        ArrayList<BarEntry> values = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            LocalDate dayToShow = todayDate.plusDays(i - 8);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            CurrentDayStats statsToShow = historicalData.get(dayToShow.format(formatter));
            int y = getOnlyNewCases(statsToShow, historicalData);
            values.add(new BarEntry(i, y));
        }

        BarDataSet set1 = new BarDataSet(values, SET_LABEL);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        dataSets.add(set1);

        BarData data = new BarData(dataSets);

        return data;
    }

    private void prepareChartData(BarChart chart, BarData data) {
        data.setValueTextSize(12f);
        chart.setData(data);
        chart.invalidate();
    }

    private void doRefreshData() {
        try {
            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8)
            {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                        .permitAll().build();
                StrictMode.setThreadPolicy(policy);
            }
            refreshData();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void refreshData() throws IOException {

        URL url = new URL("https://d35p9e4fm9h3wo.cloudfront.net/latestData.json");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        con.disconnect();

        String result = content.toString();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("covidData");

        myRef.setValue(result);
    }
}
